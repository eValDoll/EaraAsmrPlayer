package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PlaybackLoadErrorHandlingPolicyTest {
    private val policy = PlaybackLoadErrorHandlingPolicy()
    private val spec = DataSpec.Builder().setUri("https://example.com/audio.mp3")
        .setPosition(31_597_200L).build()

    @Test
    fun aFailedOpenBeforeAnyDataRequiresAFreshPrepareAtTheRequestedPosition() {
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(networkError(HttpDataSource.HttpDataSourceException.TYPE_OPEN))))
    }

    @Test
    fun responseBodyFailuresKeepTheLoaderRetryAndBackoff() {
        val error = networkError(HttpDataSource.HttpDataSourceException.TYPE_READ)
        assertEquals(0L, policy.getRetryDelayMsFor(info(error)))
        assertEquals(1_000L, policy.getRetryDelayMsFor(info(error, errorCount = 2)))
        assertEquals(5_000L, policy.getRetryDelayMsFor(info(error, errorCount = 20)))
    }

    @Test
    fun reopeningACacheSegmentAfterReadingDataKeepsTheExistingBuffer() {
        assertEquals(0L, policy.getRetryDelayMsFor(info(networkError(HttpDataSource.HttpDataSourceException.TYPE_OPEN), bytesLoaded = 1_024L)))
    }

    @Test
    fun nonHttpIoErrorsKeepTheirOriginalRetryPolicy() {
        assertEquals(0L, policy.getRetryDelayMsFor(info(IOException("temporary read failure"))))
    }

    @Test
    fun onlyRecoverableHttpStatusesUseTheFreshPreparePath() {
        fun response(status: Int) = HttpDataSource.InvalidResponseCodeException(
            status, "", null, emptyMap(), spec, ByteArray(0),
        )
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(response(503))))
        assertEquals(0L, policy.getRetryDelayMsFor(info(response(403))))
    }

    @Test
    fun nonRecoverableFileErrorsRemainFatal() {
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(FileNotFoundException("missing"))))
    }

    @Test
    fun theExistingSixLoadRetriesRemainAvailable() {
        assertEquals(6, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    @Test
    fun manifestsAndTypedChunksKeepTheirOwnLoaderRetry() {
        val error = networkError(HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        assertEquals(0L, policy.getRetryDelayMsFor(info(error, mediaData = MediaLoadData(C.DATA_TYPE_MANIFEST))))
        assertEquals(0L, policy.getRetryDelayMsFor(info(error, mediaData = MediaLoadData(
            C.DATA_TYPE_MEDIA, C.TRACK_TYPE_AUDIO, null, C.SELECTION_REASON_UNKNOWN,
            null, 0L, 10_000L,
        ))))
    }

    private fun networkError(type: Int) = HttpDataSource.HttpDataSourceException(
        IOException("network unavailable"), spec,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, type,
    )

    private fun info(
        error: IOException,
        bytesLoaded: Long = 0L,
        errorCount: Int = 1,
        mediaData: MediaLoadData = MediaLoadData(C.DATA_TYPE_MEDIA),
    ) = LoadErrorInfo(
        LoadEventInfo(1L, spec, spec.uri, emptyMap(), 0L, 0L, bytesLoaded),
        mediaData, error, errorCount,
    )
}
