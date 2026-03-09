package com.asmr.player.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import androidx.core.graphics.drawable.IconCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.R
import com.google.common.collect.ImmutableList
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class LyricMediaNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {
    private val appContext = context.applicationContext
    private val CHANNEL_ID = "playback"
    private val artworkCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val cacheManager = EntryPointAccessors.fromApplication(appContext, ImageCacheEntryPoint::class.java).imageCacheManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastRequest: LastRequest? = null
    @Volatile private var lastArtworkKeyRequested: String? = null
    @Volatile private var artworkJob: Job? = null

    private data class LastRequest(
        val mediaSession: MediaSession,
        val customLayout: ImmutableList<CommandButton>,
        val actionFactory: MediaNotification.ActionFactory,
        val callback: MediaNotification.Provider.Callback
    )

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        lastRequest = LastRequest(mediaSession, customLayout, actionFactory, onNotificationChangedCallback)
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        
        val trackTitle = metadata.title?.toString().orEmpty().ifBlank { "正在播放" }
        val artist = metadata.artist?.toString().orEmpty()
        val contentTitle = trackTitle
        val contentText = artist
        
        val artworkUri = metadata.artworkUri
        val artworkBitmap = getCachedArtworkBitmap(artworkUri).also {
            if (it == null) {
                requestArtworkLoad(artworkUri)
            }
        }

        val prevAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(appContext, R.drawable.ic_notif_prev),
            "上一首",
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
        val playPauseAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(
                appContext,
                if (player.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
            ),
            if (player.isPlaying) "暂停" else "播放",
            Player.COMMAND_PLAY_PAUSE
        )
        val nextAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(appContext, R.drawable.ic_notif_next),
            "下一首",
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )

        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(mediaStyle)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText("")
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(player.isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mediaSession.sessionActivity)
            .setDeleteIntent(actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_STOP.toLong()))
            .apply {
                if (artworkBitmap != null) setLargeIcon(artworkBitmap)
            }

        return MediaNotification(1001, builder.build())
    }

    private fun getCachedArtworkBitmap(uri: Uri?): android.graphics.Bitmap? {
        if (uri == null) return null
        val key = buildArtworkKey(uri)
        val cached = artworkCache.get(key)
        if (cached != null && !cached.isRecycled) return cached
        return null
    }

    private fun requestArtworkLoad(uri: Uri?) {
        if (uri == null) return
        val key = buildArtworkKey(uri)
        if (artworkCache.get(key) != null) return
        if (lastArtworkKeyRequested == key && artworkJob?.isActive == true) return

        lastArtworkKeyRequested = key
        artworkJob?.cancel()
        lastRequest ?: return
        val targetSizePx = targetArtworkSizePx()

        artworkJob = scope.launch {
            val bmp = runCatching {
                cacheManager.loadImageFromCache(
                    model = uri,
                    size = IntSize(targetSizePx, targetSizePx),
                    cachePolicy = CachePolicy(readMemory = true, writeMemory = false, readDisk = true, writeDisk = false)
                )?.asAndroidBitmap()
            }.getOrNull()

            if (bmp == null || bmp.isRecycled) return@launch
            artworkCache.put(key, bmp)

            withContext(Dispatchers.Main) {
                val latest = lastRequest ?: return@withContext
                val currentUri = latest.mediaSession.player.mediaMetadata.artworkUri
                if (currentUri != uri) return@withContext
                latest.callback.onNotificationChanged(
                    createNotification(
                        latest.mediaSession,
                        latest.customLayout,
                        latest.actionFactory,
                        latest.callback
                    )
                )
            }
        }
    }

    private fun targetArtworkSizePx(): Int {
        val density = appContext.resources.displayMetrics.density
        return (48f * density + 0.5f).toInt().coerceAtLeast(1)
    }

    private fun buildArtworkKey(uri: Uri): String {
        if (uri.scheme?.lowercase() == "file") {
            val path = uri.path.orEmpty()
            if (path.isNotBlank()) {
                val lastModified = runCatching { File(path).lastModified() }.getOrDefault(0L)
                return "file:$path:$lastModified"
            }
        }
        return uri.toString()
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
        return false
    }
}
