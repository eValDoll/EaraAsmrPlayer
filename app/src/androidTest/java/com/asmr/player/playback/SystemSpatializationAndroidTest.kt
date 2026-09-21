package com.asmr.player.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 32)
class SystemSpatializationAndroidTest {
    @Test
    fun nativeOptOutFlagReachesAudioTrack() {
        val format = AudioFormat.Builder()
            .setSampleRate(48_000)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val bufferSize = AudioTrack.getMinBufferSize(
            48_000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val probeDurationMs = InstrumentationRegistry.getArguments()
            .getString("audioPolicyProbeDurationMs")?.toLong()?.coerceIn(0L, 15_000L) ?: 0L

        for (optOut in listOf(false, true, false)) {
            val behavior = if (optOut) AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER
                else AudioAttributes.SPATIALIZATION_BEHAVIOR_AUTO
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setSpatializationBehavior(behavior)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
                assertEquals(AudioAttributes.USAGE_MEDIA, track.audioAttributes.usage)
                assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, track.audioAttributes.contentType)
                assertEquals(behavior, track.audioAttributes.spatializationBehavior)
                // Acceptance of the flag does not prove vendor effects are bypassed.
                // The optional silent probe allows inspection with dumpsys media.audio_flinger.
                if (probeDurationMs > 0) {
                    Log.i("EaraAudioPolicyTest", "original=$optOut session=${track.audioSessionId}")
                    val silence = ByteArray(bufferSize)
                    track.play()
                    val deadline = SystemClock.elapsedRealtime() + probeDurationMs
                    while (SystemClock.elapsedRealtime() < deadline) {
                        assertEquals(silence.size, track.write(silence, 0, silence.size))
                    }
                    track.stop()
                }
            } finally {
                track.release()
            }
        }
    }
}
