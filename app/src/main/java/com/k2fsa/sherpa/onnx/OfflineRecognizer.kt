package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

data class OfflineRecognizerResult(
    val text: String,
    val tokens: Array<String>,
    val timestamps: FloatArray,
    val lang: String,
    val emotion: String,
    val event: String,
    val durations: FloatArray
)

data class OfflineTransducerModelConfig(
    var encoder: String = "",
    var decoder: String = "",
    var joiner: String = ""
)

data class OfflineParaformerModelConfig(
    var model: String = "",
    var qnnConfig: QnnConfig = QnnConfig()
)

data class OfflineNemoEncDecCtcModelConfig(
    var model: String = ""
)

data class OfflineDolphinModelConfig(
    var model: String = ""
)

data class OfflineZipformerCtcModelConfig(
    var model: String = "",
    var qnnConfig: QnnConfig = QnnConfig()
)

data class OfflineWenetCtcModelConfig(
    var model: String = ""
)

data class OfflineOmnilingualAsrCtcModelConfig(
    var model: String = ""
)

data class OfflineMedAsrCtcModelConfig(
    var model: String = ""
)

data class OfflineFireRedAsrCtcModelConfig(
    var model: String = ""
)

data class OfflineFunAsrNanoModelConfig(
    var encoderAdaptor: String = "",
    var llm: String = "",
    var embedding: String = "",
    var tokenizer: String = "",
    var systemPrompt: String = "You are a helpful assistant.",
    var userPrompt: String = "语音转写：",
    var maxNewTokens: Int = 512,
    var temperature: Float = 1e-6f,
    var topP: Float = 0.8f,
    var seed: Int = 42,
    var language: String = "",
    var itn: Boolean = true,
    var hotwords: String = ""
)

data class OfflineQwen3AsrModelConfig(
    var convFrontend: String = "",
    var encoder: String = "",
    var decoder: String = "",
    var tokenizer: String = "",
    var maxTotalLen: Int = 512,
    var maxNewTokens: Int = 128,
    var temperature: Float = 1e-6f,
    var topP: Float = 0.8f,
    var seed: Int = 42,
    var hotwords: String = ""
)

data class OfflineWhisperModelConfig(
    var encoder: String = "",
    var decoder: String = "",
    var language: String = "en",
    var task: String = "transcribe",
    var tailPaddings: Int = 1000,
    var enableTokenTimestamps: Boolean = false,
    var enableSegmentTimestamps: Boolean = false
)

data class OfflineCanaryModelConfig(
    var encoder: String = "",
    var decoder: String = "",
    var srcLang: String = "en",
    var tgtLang: String = "en",
    var usePnc: Boolean = true
)

data class OfflineCohereTranscribeModelConfig(
    var encoder: String = "",
    var decoder: String = "",
    var language: String = "",
    var usePunct: Boolean = true,
    var useItn: Boolean = true
)

data class OfflineFireRedAsrModelConfig(
    var encoder: String = "",
    var decoder: String = ""
)

data class OfflineMoonshineModelConfig(
    var preprocessor: String = "",
    var encoder: String = "",
    var uncachedDecoder: String = "",
    var cachedDecoder: String = "",
    var mergedDecoder: String = ""
)

data class OfflineSenseVoiceModelConfig(
    var model: String = "",
    var language: String = "",
    var useInverseTextNormalization: Boolean = true,
    var qnnConfig: QnnConfig = QnnConfig()
)

data class OfflineModelConfig(
    var transducer: OfflineTransducerModelConfig = OfflineTransducerModelConfig(),
    var paraformer: OfflineParaformerModelConfig = OfflineParaformerModelConfig(),
    var whisper: OfflineWhisperModelConfig = OfflineWhisperModelConfig(),
    var fireRedAsr: OfflineFireRedAsrModelConfig = OfflineFireRedAsrModelConfig(),
    var moonshine: OfflineMoonshineModelConfig = OfflineMoonshineModelConfig(),
    var nemo: OfflineNemoEncDecCtcModelConfig = OfflineNemoEncDecCtcModelConfig(),
    var senseVoice: OfflineSenseVoiceModelConfig = OfflineSenseVoiceModelConfig(),
    var dolphin: OfflineDolphinModelConfig = OfflineDolphinModelConfig(),
    var zipformerCtc: OfflineZipformerCtcModelConfig = OfflineZipformerCtcModelConfig(),
    var wenetCtc: OfflineWenetCtcModelConfig = OfflineWenetCtcModelConfig(),
    var omnilingual: OfflineOmnilingualAsrCtcModelConfig = OfflineOmnilingualAsrCtcModelConfig(),
    var medasr: OfflineMedAsrCtcModelConfig = OfflineMedAsrCtcModelConfig(),
    var funasrNano: OfflineFunAsrNanoModelConfig = OfflineFunAsrNanoModelConfig(),
    var qwen3Asr: OfflineQwen3AsrModelConfig = OfflineQwen3AsrModelConfig(),
    var fireRedAsrCtc: OfflineFireRedAsrCtcModelConfig = OfflineFireRedAsrCtcModelConfig(),
    var canary: OfflineCanaryModelConfig = OfflineCanaryModelConfig(),
    var cohereTranscribe: OfflineCohereTranscribeModelConfig = OfflineCohereTranscribeModelConfig(),
    var teleSpeech: String = "",
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
    var modelType: String = "",
    var tokens: String = "",
    var modelingUnit: String = "",
    var bpeVocab: String = ""
)

data class OfflineRecognizerConfig(
    var featConfig: FeatureConfig = FeatureConfig(),
    var modelConfig: OfflineModelConfig = OfflineModelConfig(),
    var hr: HomophoneReplacerConfig = HomophoneReplacerConfig(),
    var decodingMethod: String = "greedy_search",
    var maxActivePaths: Int = 4,
    var hotwordsFile: String = "",
    var hotwordsScore: Float = 1.5f,
    var ruleFsts: String = "",
    var ruleFars: String = "",
    var blankPenalty: Float = 0.0f
)

class OfflineRecognizer(
    assetManager: AssetManager? = null,
    val config: OfflineRecognizerConfig
) {
    private var ptr: Long = if (assetManager != null) {
        newFromAsset(assetManager, config)
    } else {
        newFromFile(config)
    }

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0L
        }
    }

    fun release() = finalize()

    fun createStream(): OfflineStream = OfflineStream(createStream(ptr))

    fun createStream(hotwords: String): OfflineStream =
        OfflineStream(createStreamWithHotwords(ptr, hotwords))

    fun getResult(stream: OfflineStream): OfflineRecognizerResult = getResult(stream.ptr)

    fun decode(stream: OfflineStream) = decode(ptr, stream.ptr)

    fun setConfig(config: OfflineRecognizerConfig) = setConfig(ptr, config)

    private external fun delete(ptr: Long)
    private external fun createStream(ptr: Long): Long
    private external fun createStreamWithHotwords(ptr: Long, hotwords: String): Long
    private external fun setConfig(ptr: Long, config: OfflineRecognizerConfig)
    private external fun newFromAsset(
        assetManager: AssetManager,
        config: OfflineRecognizerConfig
    ): Long
    private external fun newFromFile(config: OfflineRecognizerConfig): Long
    private external fun decode(ptr: Long, streamPtr: Long)
    private external fun getResult(streamPtr: Long): OfflineRecognizerResult
}
