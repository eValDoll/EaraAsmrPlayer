package com.asmr.player.playback

import androidx.media3.common.util.UnstableApi

@UnstableApi
class StereoPcmRingBuffer(
    val frameSize: Int,
    val slotCount: Int
) {
    private val leftSlots: Array<FloatArray> = Array(slotCount) { FloatArray(frameSize) }
    private val rightSlots: Array<FloatArray> = Array(slotCount) { FloatArray(frameSize) }

    @Volatile
    private var publishedSeq: Long = 0L

    @Volatile
    private var publishedSlot: Int = 0

    private var seqCounter: Long = 0L
    private var writeSlot: Int = 0
    private var writePos: Int = 0

    fun reset() {
        publishedSeq = 0L
        publishedSlot = 0
        seqCounter = 0L
        writeSlot = 0
        writePos = 0
    }

    fun resetWriteCursor() {
        writeSlot = publishedSlot + 1
        if (writeSlot == leftSlots.size) writeSlot = 0
        writePos = 0
    }

    fun writeNormalized(left: Float, right: Float) {
        leftSlots[writeSlot][writePos] = left
        rightSlots[writeSlot][writePos] = right
        writePos++
        if (writePos == frameSize) {
            publishFrame()
        }
    }

    fun copyLatestTo(outLeft: FloatArray, outRight: FloatArray): Long {
        val seq = publishedSeq
        if (seq == 0L) return 0L
        val slot = publishedSlot
        leftSlots[slot].copyInto(outLeft)
        rightSlots[slot].copyInto(outRight)
        return seq
    }

    fun copyDelayedTo(outLeft: FloatArray, outRight: FloatArray, delaySlots: Int): Long {
        val seq = publishedSeq
        if (seq == 0L) return 0L
        val d = delaySlots.coerceIn(0, leftSlots.size - 1)
        if (seq <= d.toLong()) return 0L
        var slot = publishedSlot - d
        if (slot < 0) slot += leftSlots.size
        leftSlots[slot].copyInto(outLeft)
        rightSlots[slot].copyInto(outRight)
        return seq
    }

    private fun publishFrame() {
        val nextSeq = seqCounter + 1L
        seqCounter = nextSeq
        publishedSlot = writeSlot
        publishedSeq = nextSeq
        writeSlot++
        if (writeSlot == leftSlots.size) writeSlot = 0
        if (writeSlot == publishedSlot) {
            writeSlot++
            if (writeSlot == leftSlots.size) writeSlot = 0
        }
        writePos = 0
    }
}
