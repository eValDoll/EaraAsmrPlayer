package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo

@UnstableApi
internal class PlaybackLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy(6) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        val error = loadErrorInfo.exception as? HttpDataSource.HttpDataSourceException
        if (error != null && error.type == HttpDataSource.HttpDataSourceException.TYPE_OPEN &&
            loadErrorInfo.loadEventInfo.bytesLoaded == 0L &&
            loadErrorInfo.mediaLoadData.dataType == C.DATA_TYPE_MEDIA &&
            loadErrorInfo.mediaLoadData.trackType == C.TRACK_TYPE_UNKNOWN &&
            isRecoverableRemotePlaybackFailure(
                error.dataSpec.uri.toString(),
                error.reason,
                (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode,
            )
        ) {
            // Media3 1.8 的 progressive 加载在 open 失败时可能复用旧读取位置（androidx/media#3440）。
            // 未读到数据的打开失败交给已有的有限 prepare 重试，重建读取上下文并保留播放进度。
            // 已开始读取、缓存分段重开和 HLS/DASH 的错误仍使用加载器续传，保留可播缓冲。
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }
}
