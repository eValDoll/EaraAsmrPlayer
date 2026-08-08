package com.asmr.player.subtitle

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

internal data class SpeechRegion(
    val startSample: Int,
    val endSample: Int
)

internal class SherpaOnnxTranscriptionEngine(
    context: Context,
    modelDirectory: File,
    override val model: SubtitleTranscriptionModel
) : SubtitleTranscriptionEngine {
    init {
        SherpaOnnxNativeLoader.load(context)
    }

    private var recognizer: OfflineRecognizer? = createRecognizer(modelDirectory)
    private var vad: Vad? = Vad(
        assetManager = context.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = VAD_ASSET_PATH,
                threshold = VAD_THRESHOLD,
                minSilenceDuration = VAD_MIN_SILENCE_SECONDS,
                minSpeechDuration = VAD_MIN_SPEECH_SECONDS,
                windowSize = VAD_WINDOW_SAMPLES,
                maxSpeechDuration = VAD_MAX_SPEECH_SECONDS
            ),
            sampleRate = model.inputSampleRateHz,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
    )

    override fun transcribe(
        channelSamples: List<FloatArray>,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): List<SubtitleTranscriptionSegment> {
        val activeRecognizer = checkNotNull(recognizer) { "日语字幕模型已释放" }
        val activeVad = checkNotNull(vad) { "语音切分模型已释放" }
        val validChannels = channelSamples.filter { it.isNotEmpty() }
        if (isCancelled() || validChannels.isEmpty()) return emptyList()

        onProgress(0)
        val filteredChannels = validChannels.map { samples ->
            SpeechAudioPreprocessor.filterForVad(samples, model.inputSampleRateHz)
        }
        val totalSamples = filteredChannels.minOf(FloatArray::size)
        val detected = filteredChannels.flatMap { samples ->
            detectSpeech(activeVad, samples.copyOf(totalSamples), isCancelled)
        }
        if (isCancelled()) return emptyList()
        val regions = splitSpeechRegionsAtQuietPoints(
            regions = mergeSpeechRegions(
                regions = detected,
                totalSamples = totalSamples,
                sampleRateHz = model.inputSampleRateHz
            ),
            channels = filteredChannels,
            sampleRateHz = model.inputSampleRateHz,
            minimumRegionMs = MINIMUM_REGION_MS,
            maximumRegionMs = MAXIMUM_REGION_MS
        )
        onProgress(VAD_PROGRESS_PERCENT)
        if (regions.isEmpty()) return emptyList()

        val segments = mutableListOf<SubtitleTranscriptionSegment>()
        var completedRegionCount = 0
        regions.chunked(model.inferenceBatchSize).forEach { batch ->
            if (isCancelled()) return emptyList()
            val pending = mutableListOf<Pair<SpeechRegion, OfflineStream>>()
            try {
                batch.forEach { region ->
                    val speech = selectSpeechChannel(filteredChannels, region)
                    val prepared = SpeechAudioPreprocessor.normalizeSpeechSegment(
                        speech,
                        model.inputSampleRateHz
                    )
                    val stream = activeRecognizer.createStream()
                    pending += region to stream
                    stream.acceptWaveform(prepared, model.inputSampleRateHz)
                }
                activeRecognizer.decode(pending.map { it.second })
                if (isCancelled()) return emptyList()
                pending.forEach { (region, stream) ->
                    val result = activeRecognizer.getResult(stream)
                    val text = cleanRecognizerText(result.text)
                    if (text.isNotEmpty()) {
                        val regionDurationMs =
                            (region.endSample - region.startSample) * 1_000L /
                                model.inputSampleRateHz
                        segments += SubtitleTranscriptionSegment(
                            startMs = region.startSample * 1_000L / model.inputSampleRateHz,
                            endMs = region.endSample * 1_000L / model.inputSampleRateHz,
                            text = text,
                            tokens = buildRecognizerTokenTimeline(
                                tokens = result.tokens.toList(),
                                timestampsSeconds = result.timestamps.toList(),
                                durationsSeconds = result.durations.toList(),
                                segmentDurationMs = regionDurationMs
                            )
                        )
                    }
                    completedRegionCount += 1
                    onProgress(
                        VAD_PROGRESS_PERCENT +
                            (completedRegionCount * (100 - VAD_PROGRESS_PERCENT) / regions.size)
                    )
                }
            } finally {
                pending.forEach { (_, stream) -> stream.release() }
            }
        }
        return segments
    }

    override fun close() {
        recognizer?.release()
        recognizer = null
        vad?.release()
        vad = null
    }

    private fun createRecognizer(modelDirectory: File): OfflineRecognizer {
        val artifacts = model.artifacts.associate { artifact ->
            artifact.fileName to File(modelDirectory, artifact.fileName).also { file ->
                check(isInstalledSubtitleModelArtifact(file, artifact.bytes)) {
                    "日语字幕模型文件缺失：${artifact.fileName}"
                }
            }
        }
        return OfflineRecognizer(
            config = buildOfflineRecognizerConfig(
                model = model,
                artifactPaths = artifacts.mapValues { it.value.absolutePath },
                numThreads = preferredThreadCount()
            )
        )
    }

    private fun detectSpeech(
        activeVad: Vad,
        samples: FloatArray,
        isCancelled: () -> Boolean
    ): List<SpeechRegion> {
        activeVad.reset()
        var offset = 0
        while (offset < samples.size) {
            if (isCancelled()) return emptyList()
            val end = minOf(offset + VAD_FEED_SAMPLES, samples.size)
            activeVad.acceptWaveform(samples.copyOfRange(offset, end))
            offset = end
        }
        activeVad.flush()
        return buildList {
            while (!activeVad.empty()) {
                val segment = activeVad.front()
                val start = segment.start.coerceIn(0, samples.size)
                val end = (start + segment.samples.size).coerceIn(start, samples.size)
                if (end > start) add(SpeechRegion(start, end))
                activeVad.pop()
            }
        }.also { activeVad.reset() }
    }

    private fun preferredThreadCount(): Int {
        val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val highPerformanceCount = runCatching {
            (0 until processorCount)
                .mapNotNull { index ->
                    File("/sys/devices/system/cpu/cpu$index/cpufreq/cpuinfo_max_freq")
                        .takeIf(File::isFile)
                        ?.readText()
                        ?.trim()
                        ?.toLongOrNull()
                }
                .let { frequencies ->
                    val minimum = frequencies.minOrNull() ?: return@runCatching 0
                    frequencies.count { it > minimum }
                }
        }.getOrDefault(0)
        return max(2, highPerformanceCount)
            .coerceAtMost(processorCount)
            .coerceAtMost(MAX_THREAD_COUNT)
    }

    private companion object {
        const val MODEL_FILE_NAME = "model.int8.onnx"
        const val TOKENS_FILE_NAME = "tokens.txt"
        const val VAD_ASSET_PATH = "subtitle/silero_vad.onnx"
        const val VAD_THRESHOLD = 0.15f
        const val VAD_MIN_SILENCE_SECONDS = 0.2f
        const val VAD_MIN_SPEECH_SECONDS = 0.15f
        const val VAD_MAX_SPEECH_SECONDS = 15f
        const val VAD_WINDOW_SAMPLES = 512
        const val VAD_FEED_SAMPLES = 16_000
        const val VAD_PROGRESS_PERCENT = 20
        const val MAX_THREAD_COUNT = 4
        const val MINIMUM_REGION_MS = 8_000
        const val MAXIMUM_REGION_MS = 15_000
    }
}

internal class SherpaOnnxTranscriptionEngineFactory(
    private val context: Context,
    private val modelDirectory: File,
    override val model: SubtitleTranscriptionModel
) : SubtitleTranscriptionEngineFactory {
    override fun create(): SubtitleTranscriptionEngine = SherpaOnnxTranscriptionEngine(
        context = context,
        modelDirectory = modelDirectory,
        model = model
    )
}

internal fun mergeSpeechRegions(
    regions: List<SpeechRegion>,
    totalSamples: Int,
    sampleRateHz: Int,
    preRollMs: Int = 250,
    postRollMs: Int = 350,
    mergeGapMs: Int = 250
): List<SpeechRegion> {
    if (regions.isEmpty() || totalSamples <= 0) return emptyList()
    require(sampleRateHz > 0)
    val preRoll = preRollMs * sampleRateHz / 1_000
    val postRoll = postRollMs * sampleRateHz / 1_000
    val mergeGap = mergeGapMs * sampleRateHz / 1_000
    val padded = regions.asSequence()
        .mapNotNull { region ->
            val start = (region.startSample - preRoll).coerceIn(0, totalSamples)
            val end = (region.endSample + postRoll).coerceIn(start, totalSamples)
            SpeechRegion(start, end).takeIf { it.endSample > it.startSample }
        }
        .sortedBy(SpeechRegion::startSample)
        .toList()
    if (padded.isEmpty()) return emptyList()

    val merged = mutableListOf<SpeechRegion>()
    var current = padded.first()
    padded.drop(1).forEach { next ->
        if (next.startSample - current.endSample <= mergeGap) {
            current = SpeechRegion(current.startSample, max(current.endSample, next.endSample))
        } else {
            merged += current
            current = next
        }
    }
    merged += current

    return merged
}

internal fun splitSpeechRegionsAtQuietPoints(
    regions: List<SpeechRegion>,
    channels: List<FloatArray>,
    sampleRateHz: Int,
    minimumRegionMs: Int = 4_000,
    maximumRegionMs: Int = 7_000,
    analysisWindowMs: Int = 80,
    analysisStepMs: Int = 40
): List<SpeechRegion> {
    if (regions.isEmpty() || channels.isEmpty()) return emptyList()
    require(sampleRateHz > 0)
    require(minimumRegionMs in 1 until maximumRegionMs)
    val minimumSamples = minimumRegionMs * sampleRateHz / 1_000
    val maximumSamples = maximumRegionMs * sampleRateHz / 1_000
    val windowSamples = (analysisWindowMs * sampleRateHz / 1_000).coerceAtLeast(1)
    val stepSamples = (analysisStepMs * sampleRateHz / 1_000).coerceAtLeast(1)
    val availableSamples = channels.minOf(FloatArray::size)

    return regions.flatMap { source ->
        val region = SpeechRegion(
            startSample = source.startSample.coerceIn(0, availableSamples),
            endSample = source.endSample.coerceIn(0, availableSamples)
        )
        if (region.endSample <= region.startSample) return@flatMap emptyList()

        buildList {
            var start = region.startSample
            while (region.endSample - start > maximumSamples) {
                val searchStart = maxOf(
                    start + minimumSamples,
                    region.endSample - maximumSamples
                )
                val searchEnd = minOf(
                    start + maximumSamples,
                    region.endSample - minimumSamples
                )
                val cut = if (searchEnd >= searchStart) {
                    quietestCut(
                        channels = channels,
                        searchStart = searchStart,
                        searchEnd = searchEnd,
                        windowSamples = windowSamples,
                        stepSamples = stepSamples
                    )
                } else {
                    minOf(start + maximumSamples, region.endSample)
                }
                add(SpeechRegion(start, cut))
                start = cut
            }
            if (region.endSample > start) add(SpeechRegion(start, region.endSample))
        }
    }
}

private fun quietestCut(
    channels: List<FloatArray>,
    searchStart: Int,
    searchEnd: Int,
    windowSamples: Int,
    stepSamples: Int
): Int {
    var bestCut = searchStart
    var bestEnergy = Double.POSITIVE_INFINITY
    var candidate = searchStart
    while (candidate <= searchEnd) {
        val halfWindow = windowSamples / 2
        val frameStart = (candidate - halfWindow).coerceAtLeast(0)
        val frameEnd = (frameStart + windowSamples)
            .coerceAtMost(channels.minOf(FloatArray::size))
        val energy = channels.maxOf { channel ->
            var sum = 0.0
            for (index in frameStart until frameEnd) {
                val sample = channel[index].toDouble()
                sum += sample * sample
            }
            sum / (frameEnd - frameStart).coerceAtLeast(1)
        }
        if (energy < bestEnergy) {
            bestEnergy = energy
            bestCut = candidate
        }
        candidate += stepSamples
    }
    return bestCut
}

private fun selectSpeechChannel(
    channels: List<FloatArray>,
    region: SpeechRegion
): FloatArray {
    val slices = channels.map { channel ->
        channel.copyOfRange(region.startSample, region.endSample.coerceAtMost(channel.size))
    }
    if (slices.size == 1) return slices.single()
    val left = slices[0]
    val right = slices[1]
    val leftEnergy = squareEnergy(left)
    val rightEnergy = squareEnergy(right)
    val denominator = sqrt(leftEnergy * rightEnergy)
    val correlation = if (denominator > 0.0) {
        left.indices.sumOf { index -> left[index].toDouble() * right[index] } / denominator
    } else {
        0.0
    }
    return if (correlation >= 0.65) {
        FloatArray(left.size) { index -> ((left[index] + right[index]) * 0.5f).coerceIn(-1f, 1f) }
    } else {
        if (leftEnergy >= rightEnergy) left else right
    }
}

private fun squareEnergy(samples: FloatArray): Double =
    samples.sumOf { sample -> sample.toDouble() * sample }

internal fun buildRecognizerTokenTimeline(
    tokens: List<String>,
    timestampsSeconds: List<Float>,
    durationsSeconds: List<Float> = emptyList(),
    segmentDurationMs: Long
): List<SubtitleTranscriptionToken> {
    if (tokens.isEmpty() || tokens.size != timestampsSeconds.size || segmentDurationMs <= 0L) {
        return emptyList()
    }
    val starts = timestampsSeconds.map { seconds ->
        seconds.takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?.times(1_000f)
            ?.roundToLong()
            ?.coerceAtMost(segmentDurationMs)
    }
    return tokens.mapIndexedNotNull { index, rawToken ->
        val text = cleanRecognizerToken(rawToken)
        val startMs = starts[index]
        if (text.isEmpty() || startMs == null || startMs >= segmentDurationMs) {
            return@mapIndexedNotNull null
        }
        val nextStartMs = starts.drop(index + 1)
            .firstOrNull { candidate -> candidate != null && candidate > startMs }
        val durationEndMs = durationsSeconds.getOrNull(index)
            ?.takeIf { it.isFinite() && it > 0f }
            ?.times(1_000f)
            ?.roundToLong()
            ?.let { durationMs -> startMs + durationMs }
        val estimatedEndMs = durationEndMs
            ?: minOf(nextStartMs ?: Long.MAX_VALUE, startMs + MAX_TOKEN_DURATION_MS)
        val endMs = estimatedEndMs
            .coerceIn(startMs + 1L, segmentDurationMs)
        SubtitleTranscriptionToken(startMs = startMs, endMs = endMs, text = text)
    }
}

private const val MAX_TOKEN_DURATION_MS = 400L

internal fun buildOfflineRecognizerConfig(
    model: SubtitleTranscriptionModel,
    artifactPaths: Map<String, String>,
    numThreads: Int
): OfflineRecognizerConfig {
    val modelFile = artifactPaths.getValue("model.int8.onnx")
    val modelConfig = OfflineModelConfig(
        numThreads = numThreads,
        debug = false,
        provider = "cpu",
        tokens = artifactPaths.getValue("tokens.txt")
    )
    when (model.type) {
        SubtitleTranscriptionModelType.NEMO_CTC -> {
            modelConfig.nemo = OfflineNemoEncDecCtcModelConfig(model = modelFile)
        }

        SubtitleTranscriptionModelType.SENSE_VOICE -> {
            modelConfig.senseVoice = OfflineSenseVoiceModelConfig(
                model = modelFile,
                language = "ja",
                useInverseTextNormalization = true
            )
        }
    }
    return OfflineRecognizerConfig(
        modelConfig = modelConfig,
        decodingMethod = "greedy_search",
        maxActivePaths = 4
    )
}

private fun cleanRecognizerToken(text: String): String = text
    .replace('▁', ' ')
    .replace(Regex("[\\t\\r\\n ]+"), " ")
    .trim()

private fun cleanRecognizerText(text: String): String = text
    .replace(Regex("[\\t\\r\\n ]+"), " ")
    .trim()
