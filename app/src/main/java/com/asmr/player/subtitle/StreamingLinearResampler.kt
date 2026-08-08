package com.asmr.player.subtitle

internal class StreamingLinearResampler(
    inputSampleRate: Int,
    private val outputSampleRate: Int = 16_000,
    private val outputChunkSize: Int = outputSampleRate * 30,
    private val minimumOutputChunkSize: Int = outputChunkSize,
    private val silenceWindowSize: Int = (outputSampleRate / 5).coerceAtLeast(1)
) {
    private val sourceSamplesPerOutput = inputSampleRate.toDouble() / outputSampleRate
    private var previousSample = 0f
    private var sourceIndex = -1L
    private var nextOutputPosition = 0.0
    private var chunk = FloatArray(outputChunkSize)
    private var chunkSize = 0

    init {
        require(inputSampleRate > 0)
        require(outputSampleRate > 0)
        require(outputChunkSize > 0)
        require(minimumOutputChunkSize in 1..outputChunkSize)
        require(silenceWindowSize > 0)
    }

    fun consume(monoSamples: FloatArray): List<FloatArray> {
        if (monoSamples.isEmpty()) return emptyList()
        val completed = mutableListOf<FloatArray>()
        monoSamples.forEach { currentSample ->
            sourceIndex += 1L
            if (sourceIndex == 0L) {
                append(currentSample, completed)
                nextOutputPosition += sourceSamplesPerOutput
            } else {
                val previousPosition = sourceIndex - 1.0
                while (nextOutputPosition <= sourceIndex.toDouble()) {
                    val fraction = (nextOutputPosition - previousPosition).toFloat().coerceIn(0f, 1f)
                    append(previousSample + (currentSample - previousSample) * fraction, completed)
                    nextOutputPosition += sourceSamplesPerOutput
                }
            }
            previousSample = currentSample
        }
        return completed
    }

    fun finish(): FloatArray? {
        if (chunkSize == 0) return null
        return chunk.copyOf(chunkSize).also {
            chunk = FloatArray(outputChunkSize)
            chunkSize = 0
        }
    }

    private fun append(sample: Float, completed: MutableList<FloatArray>) {
        chunk[chunkSize] = sample.coerceIn(-1f, 1f)
        chunkSize += 1
        if (chunkSize == outputChunkSize) {
            val splitAt = findQuietSplit()
            completed += chunk.copyOf(splitAt)
            val remaining = chunkSize - splitAt
            chunk.copyInto(chunk, destinationOffset = 0, startIndex = splitAt, endIndex = chunkSize)
            chunk.fill(0f, fromIndex = remaining)
            chunkSize = remaining
        }
    }

    private fun findQuietSplit(): Int {
        if (minimumOutputChunkSize == outputChunkSize) return outputChunkSize
        val halfWindow = silenceWindowSize / 2
        var windowStart = (minimumOutputChunkSize - halfWindow).coerceAtLeast(0)
        val lastWindowStart = (outputChunkSize - silenceWindowSize).coerceAtLeast(windowStart)
        var bestSplit = minimumOutputChunkSize
        var bestEnergy = Double.POSITIVE_INFINITY
        while (windowStart <= lastWindowStart) {
            val windowEnd = (windowStart + silenceWindowSize).coerceAtMost(outputChunkSize)
            var energy = 0.0
            for (index in windowStart until windowEnd) {
                val value = chunk[index].toDouble()
                energy += value * value
            }
            if (energy < bestEnergy) {
                bestEnergy = energy
                bestSplit = (windowStart + (windowEnd - windowStart) / 2)
                    .coerceIn(minimumOutputChunkSize, outputChunkSize)
            }
            windowStart += silenceWindowSize
        }
        return bestSplit
    }
}
