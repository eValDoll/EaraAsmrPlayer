package com.asmr.player.cache

object AppCacheLimits {
    const val MinSizeMb = 50
    const val MaxSizeMb = 1000
    const val DefaultSizeMb = 150
    const val SizeStepMb = 10

    private const val BytesPerMb = 1024L * 1024L
    private const val ImageBudgetPercent = 60L
    private const val PlaybackBudgetPercent = 35L

    fun clampSizeMb(sizeMb: Int): Int = sizeMb.coerceIn(MinSizeMb, MaxSizeMb)

    fun totalSizeBytes(sizeMb: Int): Long = clampSizeMb(sizeMb) * BytesPerMb

    fun imageMaxSizeBytes(sizeMb: Int): Long = totalSizeBytes(sizeMb) * ImageBudgetPercent / 100L

    fun playbackMaxSizeBytes(sizeMb: Int): Long = totalSizeBytes(sizeMb) * PlaybackBudgetPercent / 100L

    fun previewMaxSizeBytes(sizeMb: Int): Long {
        val total = totalSizeBytes(sizeMb)
        return total - imageMaxSizeBytes(sizeMb) - playbackMaxSizeBytes(sizeMb)
    }
}
