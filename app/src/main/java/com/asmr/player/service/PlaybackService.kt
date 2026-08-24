package com.asmr.player.service

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import androidx.media3.datasource.cache.CacheDataSource
import com.asmr.player.MainActivity
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.TrackPlaybackProgressEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.data.settings.AudioEffectController
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.data.settings.PlaybackRuntimeSettings
import com.asmr.player.data.repository.StatisticsRepository
import com.asmr.player.data.repository.ListeningRecordRepository
import com.asmr.player.data.repository.ListeningTrackContext
import com.asmr.player.playback.AsmrRenderersFactory
import com.asmr.player.playback.BalanceAudioProcessor
import com.asmr.player.playback.ChannelModeAudioProcessor
import com.asmr.player.playback.DefaultSpectrumAudioTrackBufferDurationMillis
import com.asmr.player.playback.FadingPlayer
import com.asmr.player.playback.AppVolume
import com.asmr.player.playback.AppVolumeBoostController
import com.asmr.player.playback.GraphicEqualizerAudioProcessor
import com.asmr.player.playback.PlaybackMediaCache
import com.asmr.player.playback.PlaybackConnectionLifecycle
import com.asmr.player.playback.PlaybackRecoveryPolicy
import com.asmr.player.playback.PlaybackStateStore
import com.asmr.player.playback.RoutingPlaybackDataSource
import com.asmr.player.playback.SceneEffectAudioProcessor
import com.asmr.player.playback.StereoFftAnalyzer
import com.asmr.player.playback.StereoOrbitAudioProcessor
import com.asmr.player.playback.StereoPcmRingBuffer
import com.asmr.player.playback.StereoSpectrumBus
import com.asmr.player.playback.StereoSpectrumTapAudioProcessor
import com.asmr.player.playback.SpectrumOutputBufferSizeProvider
import com.asmr.player.playback.SpectrumPcmRingSlotCount
import com.asmr.player.playback.VolumeThresholdAudioProcessor
import com.asmr.player.playback.VolumeFader
import com.asmr.player.playback.isRecoverableRemotePlaybackFailure
import com.asmr.player.playback.capturePersistedPlaybackState
import com.asmr.player.playback.spectrumVisualDelayMillis
import com.asmr.player.util.EmbeddedMediaExtractor
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.OkHttpClient

@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var sessionPlayer: FadingPlayer
    private lateinit var appVolumeBoostController: AppVolumeBoostController
    private var startupAppVolumePercent: Int? = null
    private val graphicEqualizerAudioProcessor = GraphicEqualizerAudioProcessor()
    private val balanceAudioProcessor = BalanceAudioProcessor()
    private val stereoOrbitAudioProcessor = StereoOrbitAudioProcessor()
    private val sceneEffectAudioProcessor = SceneEffectAudioProcessor()
    private val channelModeAudioProcessor = ChannelModeAudioProcessor()
    private val volumeThresholdAudioProcessor = VolumeThresholdAudioProcessor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val volumeFader = VolumeFader(serviceScope)
    @Volatile private var spectrumAudioTrackBufferDurationMillis =
        DefaultSpectrumAudioTrackBufferDurationMillis
    @Volatile private var spectrumOutputSampleRate: Int? = null
    @Volatile private var spectrumOutputFramesPerBuffer: Int? = null
    private val spectrumPcmBuffer = StereoPcmRingBuffer(
        frameSize = 1024,
        slotCount = SpectrumPcmRingSlotCount
    )
    private val spectrumAnalyzer = StereoFftAnalyzer(
        pcmBuffer = spectrumPcmBuffer,
        spectrumStore = StereoSpectrumBus.store,
        scope = serviceScope,
        fftSize = 1024,
        binCount = StereoSpectrumBus.DefaultBinCount
    )
    private val spectrumTapAudioProcessor = StereoSpectrumTapAudioProcessor(spectrumPcmBuffer) { sr ->
        spectrumAnalyzer.setSampleRate(sr)
    }
    private val spectrumOutputBufferSizeProvider = SpectrumOutputBufferSizeProvider { durationMillis ->
        spectrumAudioTrackBufferDurationMillis = durationMillis
        updateSpectrumVisualDelay()
    }
    
    // Temporary settings for current session
    private val sessionSettings = MutableStateFlow<EqualizerSettings?>(null)
    private var effectApplyJob: Job? = null
    private var sleepTimerJob: Job? = null
    @Volatile private var lastEffectiveSettings: EqualizerSettings = EqualizerSettings()
    
    private var currentLyrics: List<SubtitleEntry> = emptyList()
    private var lyricsIndexFinder: SubtitleIndexFinder? = null
    private var lastLyricIndex: Int = Int.MIN_VALUE
    private var floatingLyricsEnabled: Boolean = false
    private var overlay: FloatingLyricsOverlay? = null
    private var pauseOnOutputDisconnectEnabled: Boolean = true
    private var resumeOnOutputConnectEnabled: Boolean = false
    private var pauseOnOtherAudioEnabled: Boolean = true
    private var autoPausedByDisconnect: Boolean = false
    private var autoPausedByAudioFocusLoss: Boolean = false
    private var lastOutputEventAtMs: Long = 0L
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var notificationProvider: LyricMediaNotificationProvider? = null
    private var sfwHideSystemControlsEnabled: Boolean = false
    private var videoOutputEnabled: Boolean = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> handleAudioFocusGain()
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                handleAudioFocusLoss(resumeWhenFocusReturns = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                handleAudioFocusLoss(resumeWhenFocusReturns = true)
            }
        }
    }

    private val outputBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> handlePotentialOutputDisconnect("becoming_noisy")
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 0) {
                        handlePotentialOutputDisconnect("headset_unplugged")
                    } else if (state == 1) {
                        handlePotentialOutputConnect("headset_plugged")
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        handlePotentialOutputDisconnect("bluetooth_off")
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isResumeEligibleOutputDevice() }) {
                handlePotentialOutputConnect("device_added")
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isDisconnectSensitiveOutputDevice() }) {
                handlePotentialOutputDisconnect("device_removed")
            }
        }
    }

    @Inject
    lateinit var audioEffectController: AudioEffectController

    @Inject
    lateinit var lyricsLoader: LyricsLoader

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var statisticsRepository: StatisticsRepository

    @Inject
    lateinit var listeningRecordRepository: ListeningRecordRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var playbackStateStore: PlaybackStateStore

    private var lastMarkedMediaId: String? = null
    private var lastMarkedElapsedMs: Long = 0L

    private var statsJob: Job? = null
    private var playbackRecoveryJob: Job? = null
    private var appExitJob: Job? = null
    private val playbackRecoveryPolicy = PlaybackRecoveryPolicy()
    private var currentTrackListenedMs: Long = 0L
    private var isCurrentTrackCounted: Boolean = false
    private var currentMediaId: String? = null
    private var lastProgressPersistElapsedMs: Long = 0L
    private val pendingNetworkTrafficBytes = AtomicLong(0L)

    private fun updateSpectrumVisualDelay() {
        spectrumAnalyzer.setVisualDelayMs(
            spectrumVisualDelayMillis(
                audioTrackBufferDurationMillis = spectrumAudioTrackBufferDurationMillis,
                outputSampleRate = spectrumOutputSampleRate,
                outputFramesPerBuffer = spectrumOutputFramesPerBuffer
            )
        )
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        appVolumeBoostController =
            AppVolumeBoostController(getSystemService(AUDIO_SERVICE) as AudioManager)
        val runtimeSettings = runBlocking {
            settingsRepository.loadPlaybackRuntimeSettings()
        }
        applyPlaybackRuntimeSettings(runtimeSettings)
        val currentAppVolumePercent = appVolumeBoostController.currentVolumePercent()
        startupAppVolumePercent = currentAppVolumePercent
        val startupAppVolumeSyncJob = serviceScope.launch(Dispatchers.IO) {
            settingsRepository.syncAppVolumePercentFromSystem(currentAppVolumePercent)
        }
        runCatching {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            spectrumOutputSampleRate = audioManager
                .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
            spectrumOutputFramesPerBuffer = audioManager
                .getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toIntOrNull()
        }
        updateSpectrumVisualDelay()
        val authStore = DlsiteAuthStore(applicationContext)
        val playbackHttpClient = okHttpClient.newBuilder()
            .connectTimeout(NETWORK_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(NETWORK_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val httpFactory = OkHttpDataSource.Factory(playbackHttpClient)
            .setUserAgent(DLSITE_UA)
        
        val transferListener = object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork && bytesTransferred > 0) {
                    // 该回调位于加载线程且调用非常频繁。只做无锁累加，由后台统计心跳
                    // 批量入库，避免每个网络缓冲都唤醒应用主线程并启动两次数据库切换。
                    pendingNetworkTrafficBytes.addAndGet(bytesTransferred.toLong())
                }
            }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        }

        val upstreamDataSourceFactory = DefaultDataSource.Factory(
            this,
            ResolvingDataSource.Factory(httpFactory) { dataSpec ->
                val uri = dataSpec.uri
                val host = uri.host.orEmpty().lowercase()
                if (
                    host.endsWith("dlsite.com") ||
                    host.endsWith("chobit.cc") ||
                    host.endsWith("dlsite.com")
                ) {
                    val headers = LinkedHashMap(dataSpec.httpRequestHeaders)
                    headers["User-Agent"] = DLSITE_UA
                    headers["Accept-Language"] = NetworkHeaders.ACCEPT_LANGUAGE
                    headers["Referer"] = "https://www.dlsite.com/"
                    val cookie = buildDlsiteCookieHeader(authStore.getDlsiteCookie())
                    if (cookie.isNotBlank() && !headers.containsKey("Cookie")) {
                        headers["Cookie"] = cookie
                    }
                    dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
                } else {
                    dataSpec
                }
            }
        ).apply {
            setTransferListener(transferListener)
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(PlaybackMediaCache.getInstance(applicationContext))
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = RoutingPlaybackDataSource.Factory(
            upstreamFactory = upstreamDataSourceFactory,
            cachedFactory = cacheDataSourceFactory
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(
                DefaultLoadErrorHandlingPolicy(NETWORK_MINIMUM_LOADABLE_RETRY_COUNT)
            )

        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(
                AsmrRenderersFactory(
                    this,
                    graphicEqualizerAudioProcessor,
                    balanceAudioProcessor,
                    stereoOrbitAudioProcessor,
                    sceneEffectAudioProcessor,
                    channelModeAudioProcessor,
                    volumeThresholdAudioProcessor,
                    spectrumTapAudioProcessor,
                    spectrumOutputBufferSizeProvider
                )
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        applyVideoOutputEnabled()

        spectrumAnalyzer.start()

        sessionPlayer = FadingPlayer(
            delegate = exoPlayer,
            volumeFader = volumeFader,
            playFadeMs = runtimeSettings.playFadeInMs.toLong(),
            pauseFadeMs = runtimeSettings.pauseFadeOutMs.toLong(),
            switchFadeOutMs = 250L,
            switchFadeInMs = 250L,
            onPlayRequested = { requestPlaybackAudioFocus() }
        )
        registerPlaybackRouteListeners()
        startEffectLoops(startupAppVolumeSyncJob)
        mediaSession = buildMediaSession()

        notificationProvider = LyricMediaNotificationProvider(
            context = this,
            initialHideSystemControls = sfwHideSystemControlsEnabled
        )
        setMediaNotificationProvider(notificationProvider!!)
        overlay = FloatingLyricsOverlay(this) { settings ->
            serviceScope.launch {
                settingsRepository.updateFloatingLyricsSettings(settings)
            }
        }
        
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                StereoSpectrumBus.playbackActive = isPlaying
                if (isPlaying) {
                    markCurrentAlbumPlayed()
                } else {
                    if (
                        !autoPausedByAudioFocusLoss &&
                        (!exoPlayer.playWhenReady || exoPlayer.playbackState == Player.STATE_ENDED)
                    ) {
                        abandonPlaybackAudioFocus()
                    }
                    serviceScope.launch { persistCurrentTrackProgressIfNeeded(force = true) }
                    serviceScope.launch { listeningRecordRepository.flush() }
                }
                refreshMediaNotification()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                cancelPlaybackRecovery(resetPolicy = true)
                serviceScope.launch { updateArtworkForCurrentMedia() }
                serviceScope.launch { loadLyricsForCurrentMedia() }
                applyVideoOutputEnabled()
                if (exoPlayer.isPlaying) {
                    markCurrentAlbumPlayed()
                }
                
                // Reset track stats for new item
                volumeThresholdAudioProcessor.resetForNewItem()
                currentMediaId = mediaItem?.mediaId
                currentTrackListenedMs = 0L
                isCurrentTrackCounted = false
                lastProgressPersistElapsedMs = 0L
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> cancelPlaybackRecovery(resetPolicy = true)
                    Player.STATE_ENDED -> abandonPlaybackAudioFocus()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    cancelPlaybackRecovery(resetPolicy = true)
                    if (!autoPausedByAudioFocusLoss) {
                        abandonPlaybackAudioFocus()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                schedulePlaybackRecovery(error)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (
                    events.contains(Player.EVENT_AVAILABLE_COMMANDS_CHANGED) ||
                    events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                ) {
                    syncMediaNotificationControllerState()
                    refreshMediaNotification()
                }
            }
        })
        StereoSpectrumBus.playbackActive = exoPlayer.isPlaying

        statsJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val tick = withContext(Dispatchers.Main.immediate) {
                    if (!exoPlayer.isPlaying) {
                        null
                    } else {
                        currentTrackListenedMs += 1000L
                        val totalDuration = exoPlayer.duration
                        val shouldIncrementTrackCount =
                            !isCurrentTrackCounted &&
                                totalDuration > 0L &&
                                currentTrackListenedMs > totalDuration * 0.25
                        if (shouldIncrementTrackCount) {
                            isCurrentTrackCounted = true
                        }
                        PlaybackStatsTick(
                            trackContext = currentListeningTrackContext(),
                            incrementTrackCount = shouldIncrementTrackCount,
                        )
                    }
                }

                if (tick != null) {
                    statisticsRepository.addListeningDuration(1000L)

                    // 会话级记录：把这一秒计入当前作品的收听会话。
                    if (tick.trackContext != null) {
                        listeningRecordRepository.recordTick(tick.trackContext, 1000L)
                    }

                    if (tick.incrementTrackCount) {
                        statisticsRepository.incrementTrackCount()
                        listeningRecordRepository.incrementTrackCount()
                    }
                    persistCurrentTrackProgressIfNeeded(force = false)
                }
                flushPendingNetworkTraffic()
                delay(1000L)
            }
        }

        serviceScope.launch {
            settingsRepository.floatingLyricsEnabled.collect { enabled ->
                floatingLyricsEnabled = enabled
                if (!enabled) {
                    overlay?.hide()
                } else {
                    lastLyricIndex = Int.MIN_VALUE
                }
            }
        }
        serviceScope.launch {
            settingsRepository.floatingLyricsSettings.collect { settings ->
                overlay?.applySettings(settings)
            }
        }
        serviceScope.launch {
            settingsRepository.pauseOnOutputDisconnect.collectLatest { enabled ->
                pauseOnOutputDisconnectEnabled = enabled
                if (!enabled) autoPausedByDisconnect = false
            }
        }
        serviceScope.launch {
            settingsRepository.resumeOnOutputConnect.collectLatest { enabled ->
                resumeOnOutputConnectEnabled = enabled
            }
        }
        serviceScope.launch {
            settingsRepository.pauseOnOtherAudio.collectLatest { enabled ->
                pauseOnOtherAudioEnabled = enabled
                if (!enabled) {
                    val shouldResume = autoPausedByAudioFocusLoss
                    autoPausedByAudioFocusLoss = false
                    abandonPlaybackAudioFocus()
                    if (shouldResume) {
                        runCatching { sessionPlayer.play() }
                    }
                } else if (exoPlayer.isPlaying && !hasAudioFocus) {
                    requestPlaybackAudioFocus()
                }
            }
        }
        serviceScope.launch {
            combine(
                settingsRepository.playFadeInMs,
                settingsRepository.pauseFadeOutMs
            ) { fadeInMs, fadeOutMs ->
                fadeInMs to fadeOutMs
            }.collectLatest { (fadeInMs, fadeOutMs) ->
                sessionPlayer.setFadeDurations(
                    playFadeMs = fadeInMs.toLong(),
                    pauseFadeMs = fadeOutMs.toLong()
                )
            }
        }
        serviceScope.launch {
            settingsRepository.sfwHideSystemControls.collectLatest { hide ->
                val stateChanged = sfwHideSystemControlsEnabled != hide
                sfwHideSystemControlsEnabled = hide
                notificationProvider?.setHideSystemControls(hide)
                if (stateChanged) {
                    refreshMediaNotificationControllerRegistration()
                }
                syncMediaNotificationControllerState()
                refreshMediaNotification()
            }
        }
        serviceScope.launch {
            settingsRepository.sleepTimerEndAtMs.collect { endAtMs ->
                sleepTimerJob?.cancel()
                sleepTimerJob = null

                if (endAtMs <= 0L) return@collect
                val delayMs = endAtMs - System.currentTimeMillis()
                if (delayMs <= 0L) {
                    settingsRepository.clearSleepTimer()
                    return@collect
                }

                sleepTimerJob = serviceScope.launch {
                    delay(delayMs)
                    withContext(Dispatchers.Main.immediate) {
                        exoPlayer.pause()
                    }
                    settingsRepository.clearSleepTimer()
                }
            }
        }
        serviceScope.launch(Dispatchers.Default) {
            loadLyricsForCurrentMedia()
            updateArtworkForCurrentMedia()
            while (isActive) {
                val nextDelayMs = updateLyricsTick()
                delay(nextDelayMs)
            }
        }
    }

    private suspend fun updateArtworkForCurrentMedia() {
        val (index, item) = withContext(Dispatchers.Main.immediate) {
            exoPlayer.currentMediaItemIndex to exoPlayer.currentMediaItem
        }
        if (index < 0) return
        if (item == null) return

        val uriString = item.localConfiguration?.uri?.toString().orEmpty().trim()
        if (uriString.isBlank()) return
        if (uriString.startsWith("http", ignoreCase = true)) return

        val mime = item.localConfiguration?.mimeType.orEmpty()
        val ext = uriString.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
        val isVideo = item.mediaMetadata.extras?.getBoolean("is_video") == true ||
            mime.startsWith("video/") ||
            ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
        if (isVideo) return

        val cacheKey = "track:" + (item.mediaId.ifBlank { uriString })
        val file = EmbeddedMediaExtractor.getArtworkCacheFile(applicationContext, cacheKey)

        if (!file.exists() || file.length() <= 0L) {
            val bmp = EmbeddedMediaExtractor.extractArtwork(applicationContext, uriString) ?: return
            val saved = EmbeddedMediaExtractor.saveArtworkToCache(applicationContext, cacheKey, bmp) ?: return
            if (saved.isBlank()) return
        }
        if (!file.exists() || file.length() <= 0L) return

        val newUri = Uri.fromFile(file)
        val oldUri = item.mediaMetadata.artworkUri
        if (oldUri != null && oldUri.toString() == newUri.toString()) return

        val updatedMeta = item.mediaMetadata.buildUpon().setArtworkUri(newUri).build()
        val updatedItem = item.buildUpon().setMediaMetadata(updatedMeta).build()
        withContext(Dispatchers.Main.immediate) {
            if (exoPlayer.currentMediaItemIndex == index) {
                exoPlayer.replaceMediaItem(index, updatedItem)
            }
        }
    }

    private fun markCurrentAlbumPlayed() {
        val item = exoPlayer.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val albumId = extras.getLong("album_id", -1L)
        if (albumId <= 0L) return

        val mediaId = item.mediaId
        val nowElapsed = SystemClock.elapsedRealtime()
        if (mediaId == lastMarkedMediaId && nowElapsed - lastMarkedElapsedMs < 5_000L) return
        lastMarkedMediaId = mediaId
        lastMarkedElapsedMs = nowElapsed

        val playedAt = System.currentTimeMillis()
        serviceScope.launch(Dispatchers.IO) {
            runCatching { database.playStatDao().markAlbumPlayed(albumId, playedAt) }
        }
    }

    /**
     * 采集当前播放项的作品上下文快照（供会话级收听记录使用）。
     * 必须在主线程调用（[serviceScope] 使用 Main.immediate）。
     * 无有效作品标识（albumId / rjCode 均为空）时返回 null。
     */
    private fun currentListeningTrackContext(): ListeningTrackContext? {
        val item = exoPlayer.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val albumId = extras?.getLong("album_id", -1L) ?: -1L
        val rjCode = extras?.getString("rj_code").orEmpty()
        if (albumId <= 0L && rjCode.isBlank()) return null
        return ListeningTrackContext(
            albumId = albumId,
            rjCode = rjCode,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            albumTitle = metadata.albumTitle?.toString().orEmpty(),
            artworkUri = metadata.artworkUri?.toString()
        )
    }

    private suspend fun flushPendingNetworkTraffic() {
        val bytes = pendingNetworkTrafficBytes.getAndSet(0L)
        if (bytes <= 0L) return
        statisticsRepository.addNetworkTraffic(bytes)
        // 音频流量归入当前收听会话（若存在）。
        listeningRecordRepository.addTraffic(bytes)
    }

    private suspend fun persistCurrentTrackProgressIfNeeded(force: Boolean) {
        data class Snapshot(
            val mediaId: String,
            val albumId: Long,
            val trackId: Long,
            val positionMs: Long,
            val durationMs: Long
        )

        val snapshot = withContext(Dispatchers.Main.immediate) {
            val item = exoPlayer.currentMediaItem
            val extras = item?.mediaMetadata?.extras
            Snapshot(
                mediaId = item?.mediaId.orEmpty(),
                albumId = extras?.getLong("album_id", -1L) ?: -1L,
                trackId = extras?.getLong("track_id", -1L) ?: -1L,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            )
        }

        if (snapshot.mediaId.isBlank()) return
        if (snapshot.albumId <= 0L) return

        val durationMs = snapshot.durationMs
        val isCompleted = if (durationMs > 0L) {
            val remaining = (durationMs - snapshot.positionMs).coerceAtLeast(0L)
            remaining <= 10_000L || snapshot.positionMs.toDouble() / durationMs.toDouble() >= 0.95
        } else {
            false
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val shouldPersist = force || isCompleted || nowElapsed - lastProgressPersistElapsedMs >= 10_000L
        if (!shouldPersist) return
        lastProgressPersistElapsedMs = nowElapsed

        withContext(Dispatchers.IO) {
            val dao = database.trackPlaybackProgressDao()
            val now = System.currentTimeMillis()
            val existing = dao.getByMediaId(snapshot.mediaId)
            val mergedDurationMs = when {
                snapshot.durationMs > 0L -> snapshot.durationMs
                existing != null && existing.durationMs > 0L -> existing.durationMs
                else -> 0L
            }
            val mergedCompleted = existing?.completed == true || isCompleted
            val mergedPositionMs = when {
                mergedDurationMs > 0L -> snapshot.positionMs.coerceIn(0L, mergedDurationMs)
                else -> snapshot.positionMs
            }

            dao.upsert(
                TrackPlaybackProgressEntity(
                    mediaId = snapshot.mediaId,
                    albumId = snapshot.albumId,
                    trackId = snapshot.trackId.takeIf { it > 0L },
                    positionMs = mergedPositionMs,
                    durationMs = mergedDurationMs,
                    completed = mergedCompleted,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    private fun requestPlaybackAudioFocus(): Boolean {
        if (!pauseOnOtherAudioEnabled) return true
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = (audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                .also { audioFocusRequest = it })
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun handleAudioFocusGain() {
        hasAudioFocus = true
        if (!autoPausedByAudioFocusLoss) return
        autoPausedByAudioFocusLoss = false
        if (!pauseOnOtherAudioEnabled) return
        if (exoPlayer.mediaItemCount == 0 || exoPlayer.playbackState == Player.STATE_ENDED) return

        Log.d("PlaybackService", "Resume after transient audio focus loss")
        serviceScope.launch(Dispatchers.Main.immediate) {
            runCatching { sessionPlayer.play() }
                .onFailure { Log.w("PlaybackService", "Failed to resume after audio focus gain", it) }
        }
    }

    private fun handleAudioFocusLoss(resumeWhenFocusReturns: Boolean) {
        if (!pauseOnOtherAudioEnabled) return
        if (!exoPlayer.playWhenReady) return
        autoPausedByAudioFocusLoss = resumeWhenFocusReturns
        Log.d("PlaybackService", "Auto pause due to audio focus loss")
        serviceScope.launch(Dispatchers.Main.immediate) {
            sessionPlayer.pause()
        }
    }

    private fun schedulePlaybackRecovery(error: PlaybackException) {
        val item = exoPlayer.currentMediaItem ?: return
        if (!exoPlayer.playWhenReady) return

        val mediaItemIndex = exoPlayer.currentMediaItemIndex
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        if (
            !isRecoverableRemotePlaybackFailure(
                uriText = uri,
                errorCode = error.errorCode,
                httpStatusCode = error.findHttpStatusCode()
            )
        ) {
            return
        }

        val mediaKey = "$mediaItemIndex:${item.mediaId}:$uri"
        val attempt = playbackRecoveryPolicy.nextAttempt(mediaKey)
        if (attempt == null) {
            Log.e(
                "PlaybackService",
                "Playback recovery exhausted for mediaId=${item.mediaId} error=${error.errorCodeName}"
            )
            return
        }

        playbackRecoveryJob?.cancel()
        playbackRecoveryJob = serviceScope.launch(Dispatchers.Main.immediate) {
            Log.w(
                "PlaybackService",
                "Scheduling playback recovery attempt=${attempt.number} delayMs=${attempt.delayMs} " +
                    "mediaId=${item.mediaId} error=${error.errorCodeName}"
            )
            delay(attempt.delayMs)
            if (!exoPlayer.playWhenReady) return@launch
            val currentItem = exoPlayer.currentMediaItem ?: return@launch
            val currentUri = currentItem.localConfiguration?.uri?.toString().orEmpty()
            if (
                exoPlayer.currentMediaItemIndex != mediaItemIndex ||
                currentItem.mediaId != item.mediaId ||
                currentUri != uri
            ) {
                return@launch
            }
            if (exoPlayer.playerError !== error || exoPlayer.playbackState != Player.STATE_IDLE) return@launch

            val resumePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            exoPlayer.seekTo(resumePositionMs)
            exoPlayer.prepare()
        }
    }

    private fun cancelPlaybackRecovery(resetPolicy: Boolean) {
        playbackRecoveryJob?.cancel()
        playbackRecoveryJob = null
        if (resetPolicy) {
            playbackRecoveryPolicy.reset()
        }
    }

    private fun PlaybackException.findHttpStatusCode(): Int? {
        var current: Throwable? = this
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                return current.responseCode
            }
            current = current.cause
        }
        return null
    }

    private fun abandonPlaybackAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private fun registerPlaybackRouteListeners() {
        val intentFilter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(outputBroadcastReceiver, intentFilter)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    private fun unregisterPlaybackRouteListeners() {
        runCatching { unregisterReceiver(outputBroadcastReceiver) }
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
    }

    private fun handlePotentialOutputDisconnect(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOutputEventAtMs < OUTPUT_EVENT_DEBOUNCE_MS) return
        lastOutputEventAtMs = now
        if (!pauseOnOutputDisconnectEnabled) return
        if (!exoPlayer.isPlaying) return
        autoPausedByDisconnect = true
        Log.d("PlaybackService", "Auto pause due to output disconnect: $reason")
        serviceScope.launch(Dispatchers.Main.immediate) {
            sessionPlayer.pause()
        }
    }

    private fun handlePotentialOutputConnect(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOutputEventAtMs < OUTPUT_EVENT_DEBOUNCE_MS) return
        lastOutputEventAtMs = now
        if (!resumeOnOutputConnectEnabled) return
        if (!hasResumeEligibleOutputDevice()) return
        if (exoPlayer.isPlaying) return
        Log.d("PlaybackService", "Auto resume due to output connect: $reason")
        serviceScope.launch(Dispatchers.Main.immediate) {
            runCatching { sessionPlayer.play() }
            autoPausedByDisconnect = false
            autoPausedByAudioFocusLoss = false
        }
    }

    private fun hasResumeEligibleOutputDevice(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.isResumeEligibleOutputDevice() }
    }

    private fun applyPlaybackRuntimeSettings(settings: PlaybackRuntimeSettings) {
        floatingLyricsEnabled = settings.floatingLyricsEnabled
        pauseOnOutputDisconnectEnabled = settings.pauseOnOutputDisconnect
        resumeOnOutputConnectEnabled = settings.resumeOnOutputConnect
        pauseOnOtherAudioEnabled = settings.pauseOnOtherAudio
        sfwHideSystemControlsEnabled = settings.sfwHideSystemControls
    }

    private fun refreshMediaNotification() {
        serviceScope.launch(Dispatchers.Main.immediate) {
            runCatching {
                syncMediaNotificationControllerState()
                mediaSession?.let { session ->
                    notificationProvider?.refreshNotification()
                    onUpdateNotification(session, false)
                }
            }.onFailure {
                Log.w("PlaybackService", "Failed to refresh media notification", it)
            }
        }
    }

    private fun buildMediaSession(): MediaSession {
        return MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(createContentIntent())
            // Media3 默认每 3 秒把仅位置变化的 PLAYING 状态重新广播给所有系统控制器。
            // 系统本就能根据 position/speed/eventTime 外推位置；关闭这类周期广播可避免
            // MIUI 同期唤醒蓝牙、妙播、灵动岛和媒体面板，真实播放状态变化仍会立即通知。
            .setPeriodicPositionUpdateEnabled(false)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val isNotificationController =
                        isMediaNotificationController(session, controller)
                    if (sfwHideSystemControlsEnabled && isNotificationController) {
                        return MediaSession.ConnectionResult.reject()
                    }
                    val base = super.onConnect(session, controller)
                    val commands = if (isNotificationController) {
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    } else {
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .add(androidx.media3.session.SessionCommand("GET_AUDIO_SESSION_ID", android.os.Bundle.EMPTY))
                            .add(androidx.media3.session.SessionCommand("UPDATE_SESSION_EQ", android.os.Bundle.EMPTY))
                            .add(androidx.media3.session.SessionCommand("RELOAD_LYRICS", android.os.Bundle.EMPTY))
                            .add(androidx.media3.session.SessionCommand("SET_VIDEO_OUTPUT_ENABLED", android.os.Bundle.EMPTY))
                            .build()
                    }
                    val playerCommands = if (
                        isNotificationController &&
                        sfwHideSystemControlsEnabled
                    ) {
                        Player.Commands.EMPTY
                    } else {
                        base.availablePlayerCommands
                    }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(playerCommands)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onPostConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ) {
                    if (isMediaNotificationController(session, controller)) {
                        syncMediaNotificationControllerState(session, controller)
                    }
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: androidx.media3.session.SessionCommand,
                    args: android.os.Bundle
                ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                    when (customCommand.customAction) {
                        "GET_AUDIO_SESSION_ID" -> {
                            val resultBundle = android.os.Bundle()
                            resultBundle.putInt("AUDIO_SESSION_ID", exoPlayer.audioSessionId)
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, resultBundle)
                            )
                        }

                        "UPDATE_SESSION_EQ" -> {
                            val prev = sessionSettings.value ?: EqualizerSettings()
                            val enabled = if (args.containsKey("enabled")) args.getBoolean("enabled") else prev.enabled
                            val levels = args.getIntArray("levels")?.toList() ?: prev.bandLevels
                            val virt = if (args.containsKey("virtualizer")) args.getInt("virtualizer") else prev.virtualizerStrength
                            val bal = if (args.containsKey("balance")) args.getFloat("balance") else prev.balance
                            val preset = args.getString("preset") ?: prev.presetName
                            val gain = if (args.containsKey("gain")) args.getFloat("gain") else prev.originalGain
                            val reverbEnabled = if (args.containsKey("reverbEnabled")) args.getBoolean("reverbEnabled") else prev.reverbEnabled
                            val reverbPreset = args.getString("reverbPreset") ?: prev.reverbPreset
                            val reverbWet = if (args.containsKey("reverbWet")) args.getInt("reverbWet") else prev.reverbWet
                            val stereoEnabled = if (args.containsKey("stereoEnabled")) args.getBoolean("stereoEnabled") else prev.stereoEnabled
                            val orbitEnabled = if (args.containsKey("orbitEnabled")) args.getBoolean("orbitEnabled") else prev.orbitEnabled
                            val orbitSpeed = if (args.containsKey("orbitSpeed")) args.getFloat("orbitSpeed") else prev.orbitSpeed
                            val orbitDistance = if (args.containsKey("orbitDistance")) args.getFloat("orbitDistance") else prev.orbitDistance
                            val channelMode = if (args.containsKey("channelMode")) args.getInt("channelMode") else prev.channelMode
                            val orbitAzimuthDeg = if (args.containsKey("orbitAzimuthDeg")) args.getFloat("orbitAzimuthDeg") else prev.orbitAzimuthDeg
                            val channelEnabled = if (args.containsKey("channelEnabled")) args.getBoolean("channelEnabled") else prev.channelEnabled
                            val vtEnabled = if (args.containsKey("volumeThresholdEnabled")) args.getBoolean("volumeThresholdEnabled") else prev.volumeThresholdEnabled
                            val vtMode = if (args.containsKey("volumeThresholdMode")) args.getInt("volumeThresholdMode") else prev.volumeThresholdMode
                            val vtMinDb = if (args.containsKey("volumeThresholdMinDb")) args.getFloat("volumeThresholdMinDb") else prev.volumeThresholdMinDb
                            val vtMaxDb = if (args.containsKey("volumeThresholdMaxDb")) args.getFloat("volumeThresholdMaxDb") else prev.volumeThresholdMaxDb
                            val loudnessTargetDb = if (args.containsKey("volumeLoudnessTargetDb")) args.getFloat("volumeLoudnessTargetDb") else prev.volumeLoudnessTargetDb
                            val sceneEffectEnabled = if (args.containsKey("sceneEffectEnabled")) args.getBoolean("sceneEffectEnabled") else prev.sceneEffectEnabled
                            val sceneEffectPresetId = args.getString("sceneEffectPresetId") ?: prev.sceneEffectPresetId
                            val sceneEffectAmount = if (args.containsKey("sceneEffectAmount")) args.getInt("sceneEffectAmount") else prev.sceneEffectAmount
                            sessionSettings.value = prev.copy(
                                enabled = enabled,
                                bandLevels = levels,
                                virtualizerStrength = virt,
                                balance = bal,
                                presetName = preset,
                                originalGain = gain,
                                reverbEnabled = reverbEnabled,
                                reverbPreset = reverbPreset,
                                reverbWet = reverbWet,
                                stereoEnabled = stereoEnabled,
                                orbitEnabled = orbitEnabled,
                                orbitSpeed = orbitSpeed,
                                orbitDistance = orbitDistance,
                                orbitAzimuthDeg = orbitAzimuthDeg,
                                channelEnabled = channelEnabled,
                                channelMode = channelMode,
                                volumeThresholdEnabled = vtEnabled,
                                volumeThresholdMode = vtMode,
                                volumeThresholdMinDb = vtMinDb,
                                volumeThresholdMaxDb = vtMaxDb,
                                volumeLoudnessTargetDb = loudnessTargetDb,
                                sceneEffectEnabled = sceneEffectEnabled,
                                sceneEffectPresetId = sceneEffectPresetId,
                                sceneEffectAmount = sceneEffectAmount
                            )
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, android.os.Bundle.EMPTY)
                            )
                        }

                        "RELOAD_LYRICS" -> {
                            serviceScope.launch { loadLyricsForCurrentMedia() }
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, android.os.Bundle.EMPTY)
                            )
                        }

                        "SET_VIDEO_OUTPUT_ENABLED" -> {
                            setVideoOutputEnabled(args.getBoolean("enabled", false))
                            return com.google.common.util.concurrent.Futures.immediateFuture(
                                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, android.os.Bundle.EMPTY)
                            )
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
    }

    private fun setVideoOutputEnabled(enabled: Boolean) {
        if (videoOutputEnabled == enabled) return
        videoOutputEnabled = enabled
        applyVideoOutputEnabled()
    }

    private fun applyVideoOutputEnabled() {
        val item = exoPlayer.currentMediaItem
        val videoActive = videoOutputEnabled && item.isVideoMediaItem()
        val params = exoPlayer.trackSelectionParameters
        val updated = params.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoActive)
            .build()
        if (updated != params) {
            exoPlayer.trackSelectionParameters = updated
        }
    }

    private fun MediaItem?.isVideoMediaItem(): Boolean {
        val item = this ?: return false
        val uriString = item.localConfiguration?.uri?.toString().orEmpty()
        val mime = item.localConfiguration?.mimeType.orEmpty()
        val ext = uriString
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
        return item.mediaMetadata.extras?.getBoolean("is_video") == true ||
            mime.startsWith("video/") ||
            ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
    }

    private fun refreshMediaNotificationControllerRegistration() {
        val session = mediaSession ?: return
        runCatching {
            if (isSessionAdded(session)) {
                removeSession(session)
            }
            addSession(session)
        }.onFailure {
            Log.w(
                "PlaybackService",
                "Failed to refresh media notification controller registration",
                it
            )
        }
    }

    private fun syncMediaNotificationControllerState() {
        val session = mediaSession ?: return
        val controller = session.getMediaNotificationControllerInfo() ?: return
        syncMediaNotificationControllerState(session, controller)
    }

    private fun isMediaNotificationController(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): Boolean {
        return session.isMediaNotificationController(controller) ||
            controller.connectionHints.getBoolean(MEDIA_NOTIFICATION_CONTROLLER_HINT, false)
    }

    private fun syncMediaNotificationControllerState(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ) {
        val sessionCommands = if (sfwHideSystemControlsEnabled) {
            SessionCommands.EMPTY
        } else {
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
        }
        val playerCommands = if (sfwHideSystemControlsEnabled) {
            Player.Commands.EMPTY
        } else {
            session.player.availableCommands
        }
        session.setAvailableCommands(
            controller,
            sessionCommands,
            playerCommands
        )
        session.setCustomLayout(controller, emptyList())
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(LYRICS_CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            LYRICS_CHANNEL_ID,
            "播放控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            description = "用于后台播放控制与锁屏媒体面板"
        }
        manager.createNotificationChannel(channel)
    }

    private suspend fun loadLyricsForCurrentMedia() {
        val item = withContext(Dispatchers.Main.immediate) { exoPlayer.currentMediaItem }
        val result = lyricsLoader.load(item)
        currentLyrics = result.lyrics
        lyricsIndexFinder = if (result.lyrics.isNotEmpty()) SubtitleIndexFinder(result.lyrics) else null
        lastLyricIndex = Int.MIN_VALUE
        refreshMediaNotification()
    }

    private suspend fun updateLyricsTick(): Long {
        data class TickState(
            val overlayNeeded: Boolean,
            val positionMs: Long,
            val isPlaying: Boolean
        )

        val state = withContext(Dispatchers.Main.immediate) {
            val overlayReady = overlay?.canDraw() == true
            val need = floatingLyricsEnabled && overlayReady
            if (need && overlay?.isShown() != true) overlay?.show()
            if (!need && overlay?.isShown() == true) overlay?.hide()
            TickState(
                overlayNeeded = need,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                isPlaying = exoPlayer.isPlaying
            )
        }
        val overlayNeeded = state.overlayNeeded
        val positionMs = state.positionMs
        val playing = state.isPlaying

        if (!overlayNeeded) return 1_000L
        val lyrics = currentLyrics
        if (lyrics.isEmpty()) {
            withContext(Dispatchers.Main.immediate) {
                if (overlayNeeded) overlay?.updateLine("暂无歌词")
            }
            return 2_000L
        }

        val idx = lyricsIndexFinder?.findActiveIndex(positionMs) ?: -1
        if (idx != lastLyricIndex) {
            lastLyricIndex = idx

            val current = lyrics.getOrNull(idx)?.text.orEmpty().ifBlank { " " }
            withContext(Dispatchers.Main.immediate) {
                if (overlayNeeded) overlay?.updateLine(current)
            }
        }

        val nextStartMs = when {
            idx + 1 in lyrics.indices -> lyrics[idx + 1].startMs
            idx < 0 && lyrics.isNotEmpty() -> lyrics.first().startMs
            else -> null
        }
        val rawDelay = nextStartMs?.let { it - positionMs } ?: 2_000L
        val maxDelay = if (playing) 2_000L else 1_500L
        return rawDelay.coerceIn(200L, maxDelay)
    }

    private fun startEffectLoops(startupAppVolumeSyncJob: Job) {
        effectApplyJob?.cancel()
        effectApplyJob = serviceScope.launch {
            combine(
                audioEffectController.equalizerSettings,
                sessionSettings
            ) { global, session ->
                session ?: global
            }
                .distinctUntilChanged()
                .collect { settings ->
                lastEffectiveSettings = settings
                graphicEqualizerAudioProcessor.setEnabled(settings.enabled)
                graphicEqualizerAudioProcessor.setBandLevels(settings.bandLevels)
                val stereoEnabled = settings.stereoEnabled
                val panActive = stereoEnabled && (settings.orbitEnabled || settings.orbitAzimuthDeg != 0f)
                balanceAudioProcessor.setBalance(if (stereoEnabled && !panActive) settings.balance else 0f)
                channelModeAudioProcessor.setMode(if (stereoEnabled) settings.channelMode else 0)
                volumeThresholdAudioProcessor.setEnabled(settings.volumeThresholdEnabled)
                volumeThresholdAudioProcessor.setMode(settings.volumeThresholdMode)
                volumeThresholdAudioProcessor.setThresholds(settings.volumeThresholdMinDb, settings.volumeThresholdMaxDb)
                volumeThresholdAudioProcessor.setLoudnessTargetDb(settings.volumeLoudnessTargetDb)
                sceneEffectAudioProcessor.setEnabled(settings.sceneEffectEnabled)
                sceneEffectAudioProcessor.setPreset(settings.sceneEffectPresetId)
                sceneEffectAudioProcessor.setAmount(settings.sceneEffectAmount)
                stereoOrbitAudioProcessor.setEnabled(settings.stereoEnabled)
                stereoOrbitAudioProcessor.setAutoOrbitEnabled(settings.orbitEnabled)
                stereoOrbitAudioProcessor.setOrbitSpeedDegPerSec(settings.orbitSpeed)
                stereoOrbitAudioProcessor.setDistance(settings.orbitDistance)
                stereoOrbitAudioProcessor.setAzimuthDeg(settings.orbitAzimuthDeg)
            }
        }
        serviceScope.launch {
            startupAppVolumeSyncJob.join()
            var skipStartupApplyPercent = startupAppVolumePercent
            settingsRepository.appVolumePercent
                .distinctUntilChanged()
                .collect { appVolumePercent ->
                    if (
                        skipStartupApplyPercent == appVolumePercent ||
                        settingsRepository.consumePendingSystemVolumeSync(appVolumePercent)
                    ) {
                        skipStartupApplyPercent = null
                        sessionPlayer.setBaseVolume(1f)
                        return@collect
                    }
                    skipStartupApplyPercent = null
                    sessionPlayer.setBaseVolume(
                        appVolumeBoostController.applyVolumePercent(appVolumePercent)
                    )
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_FOR_APP_EXIT) {
            shutdownForExplicitAppExit()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ?: PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    override fun onDestroy() {
        overlay?.hide()
        statsJob?.cancel()
        runBlocking(Dispatchers.IO) {
            flushPendingNetworkTraffic()
        }
        cancelPlaybackRecovery(resetPolicy = true)
        effectApplyJob?.cancel()
        sleepTimerJob?.cancel()
        unregisterPlaybackRouteListeners()
        abandonPlaybackAudioFocus()
        appVolumeBoostController.release()
        spectrumAnalyzer.stop()
        releaseMediaSession()
        notificationProvider = null
        runCatching { PlaybackMediaCache.release() }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownForExplicitAppExit()
    }

    private fun shutdownForExplicitAppExit() {
        if (appExitJob?.isActive == true) return
        // 先阻止控制器重连，再保存暂停后的状态；释放会话只移除系统媒体组件，不清空队列。
        PlaybackConnectionLifecycle.markAppExit()
        val state = mediaSession?.player?.let { player ->
            player.playWhenReady = false
            capturePersistedPlaybackState(player)
        }
        appExitJob = serviceScope.launch {
            if (state != null) {
                runCatching { playbackStateStore.save(state) }
                    .onFailure { error ->
                        Log.e("PlaybackService", "保存退出时播放状态失败", error)
                    }
            }
            releaseMediaSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun releaseMediaSession() {
        val session = mediaSession ?: return
        mediaSession = null
        session.player.release()
        session.release()
    }

    companion object {
        private const val ACTION_STOP_FOR_APP_EXIT =
            "com.asmr.player.action.STOP_PLAYBACK_FOR_APP_EXIT"

        internal fun requestShutdownForAppExit(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java).setAction(ACTION_STOP_FOR_APP_EXIT)
            )
        }

        private const val DLSITE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val LYRICS_CHANNEL_ID = "playback"
        private const val MEDIA_NOTIFICATION_CONTROLLER_HINT =
            "androidx.media3.session.MediaNotificationManager"
        private const val OUTPUT_EVENT_DEBOUNCE_MS = 1200L
        private const val NETWORK_CONNECT_TIMEOUT_MS = 15_000
        private const val NETWORK_READ_TIMEOUT_MS = 30_000
        private const val NETWORK_MINIMUM_LOADABLE_RETRY_COUNT = 6
    }
}

private data class PlaybackStatsTick(
    val trackContext: ListeningTrackContext?,
    val incrementTrackCount: Boolean,
)
