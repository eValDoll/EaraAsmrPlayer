package com.asmr.player.ui.playlists

import com.asmr.player.ui.common.formatStoredCv
import com.asmr.player.util.Formatting
import com.asmr.player.ui.common.LocalMainHeaderPadding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.data.local.db.entities.PlaylistPlaybackEntity
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.playback.sourcePlaylistId
import androidx.media3.common.Player
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AudioItemMenuAction
import com.asmr.player.ui.common.AudioItemRow
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.smoothScrollToTop
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.common.reorderable.ItemPosition
import com.asmr.player.ui.common.reorderable.ReorderableItem
import com.asmr.player.ui.common.reorderable.detectReorderAfterLongPress
import com.asmr.player.ui.common.reorderable.rememberReorderableLazyListState
import com.asmr.player.ui.common.reorderable.reorderable
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import kotlinx.coroutines.flow.StateFlow

private val PlaylistDetailHorizontalPadding = 8.dp

internal const val PLAYLIST_DETAIL_ITEM_TAG_PREFIX = "playlistDetailItem"
internal const val PLAYLIST_DETAIL_ITEM_MENU_BUTTON_TAG_PREFIX = "playlistDetailItemMenu"
internal const val PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG = "playlistDetailMoveTopMenuItem"
internal const val PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG = "playlistDetailMoveBottomMenuItem"
internal const val PLAYLIST_DETAIL_REORDER_DIALOG_TAG = "playlistDetailReorderDialog"

private const val PLAYLIST_DETAIL_HERO_KEY = "__playlist_detail_hero__"

@Composable
fun PlaylistDetailScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    playlistId: Long,
    title: String,
    onPlayAll: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    onResumeAll: (List<PlaylistItemEntity>, PlaylistItemEntity, Long) -> Unit,
    playback: StateFlow<PlaybackSnapshot>,
    scrollToTopSignal: Long = 0L,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(playlistId) {
        viewModel.setPlaylistId(playlistId)
    }
    val items by viewModel.items.collectAsStateWhileActive(isDataActive)
    val matchingItems = remember(items, playlistId) { items.filter { it.playlistId == playlistId } }
    key(playlistId) {
        PlaylistDetailContent(
            windowSizeClass = windowSizeClass,
            isActive = isActive,
            isDataActive = isDataActive,
            title = title,
            items = matchingItems,
            onPlayAll = onPlayAll,
            onResumeAll = onResumeAll,
            playback = playback,
            playbackHistory = viewModel.recentPlayback,
            onRemoveItem = viewModel::removeItem,
            onMoveItemToTop = viewModel::moveItemToTop,
            onMoveItemToBottom = viewModel::moveItemToBottom,
            onSaveManualOrder = viewModel::saveManualOrder,
            scrollToTopSignal = scrollToTopSignal,
        )
    }
}

@Composable
internal fun PlaylistDetailContent(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    title: String,
    items: List<PlaylistItemWithSubtitles>,
    onPlayAll: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    onResumeAll: (List<PlaylistItemEntity>, PlaylistItemEntity, Long) -> Unit,
    onRemoveItem: (String) -> Unit,
    onMoveItemToTop: (String) -> Unit,
    onMoveItemToBottom: (String) -> Unit,
    onSaveManualOrder: (List<String>) -> Unit,
    playbackHistory: StateFlow<PlaylistPlaybackEntity?>? = null,
    playback: StateFlow<PlaybackSnapshot>? = null,
    scrollToTopSignal: Long = 0L,
) {
    val listState = rememberLazyListState()
    val localItems = remember { mutableStateListOf<PlaylistItemWithSubtitles>() }
    var pendingRemoveItem by remember { mutableStateOf<PlaylistItemWithSubtitles?>(null) }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        maxScrollPerFrame = 28.dp,
        scrollTriggerPadding = 112.dp,
        onMove = { from, to ->
            localItems.movePlaylistItems(from, to)
        },
        canDragOver = { draggedOver, _ ->
            localItems.any { item -> item.mediaId == draggedOver.key }
        },
        onDragEnd = { _, _ ->
            onSaveManualOrder(localItems.map { it.mediaId })
        }
    )

    LaunchedEffect(items) {
        if (reorderState.draggingItemIndex == null) {
            localItems.sync(items)
        }
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        listState.smoothScrollToTop()
    }
    LaunchedEffect(isActive) {
        if (isActive) return@LaunchedEffect
        listState.stopScroll(MutatePriority.PreventUserInput)
    }

    val playItems by remember {
        derivedStateOf {
            localItems.map { item -> item.toPlaybackEntity() }
        }
    }
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val colorScheme = AsmrTheme.colorScheme
    val draggedItemShape = RoundedCornerShape(18.dp)
    val draggedItemContainerColor = dynamicPageContainerColor(colorScheme)
    val draggedItemElevation = if (colorScheme.isDark) 10.dp else 14.dp
    val isFavorites = title == PlaylistRepository.PLAYLIST_FAVORITES
    val emptyHeadline = if (isFavorites) {
        "收藏的内容会在这里出现"
    } else {
        "这个列表还没有内容"
    }
    val emptySectionTitle = if (isFavorites) "我的收藏" else title.ifBlank { "我的列表" }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            }
            if (localItems.isEmpty()) {
                EaraBrandedEmptyState(
                    sectionTitle = emptySectionTitle,
                    headline = emptyHeadline,
                    sectionIcon = if (isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                    modifier = contentModifier,
                    contentPadding = PaddingValues(top = LocalMainHeaderPadding.current, bottom = LocalBottomOverlayPadding.current + 88.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = contentModifier
                        .reorderable(reorderState),
                    flingBehavior = rememberCalmScrollableFlingBehavior(),
                    contentPadding = PaddingValues(top = LocalMainHeaderPadding.current, bottom = LocalBottomOverlayPadding.current)
                ) {
                    item(key = PLAYLIST_DETAIL_HERO_KEY, contentType = "playlistHero") {
                        PlaylistDetailHero(
                            items = localItems,
                            playbackHistory = playbackHistory,
                            playback = playback,
                            isActive = isDataActive,
                            onPlay = { item, positionMs ->
                                onResumeAll(playItems, item.toPlaybackEntity(), positionMs)
                            }
                        )
                    }
                    itemsIndexed(
                        items = localItems,
                        key = { _, item -> item.mediaId },
                        contentType = { _, _ -> "playlistDetailItem" }
                    ) { index, item ->
                        ReorderableItem(
                            reorderableState = reorderState,
                            key = item.mediaId,
                            modifier = Modifier.fillMaxWidth(),
                            draggingDecorationModifier = Modifier
                                .shadow(draggedItemElevation, draggedItemShape, clip = false)
                                .clip(draggedItemShape)
                                .background(draggedItemContainerColor)
                        ) { isDragging ->
                            PlaylistItemRow(
                                item = item,
                                showSubtitleStamp = item.hasSubtitles,
                                showTopDivider = index > 0,
                                isDragging = isDragging,
                                loadAudioMetadata = !listState.isScrollInProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("$PLAYLIST_DETAIL_ITEM_TAG_PREFIX:${item.mediaId}")
                                    .detectReorderAfterLongPress(reorderState),
                                onPlay = { onPlayAll(playItems, item.toPlaybackEntity()) },
                                onMoveToTop = { onMoveItemToTop(item.mediaId) },
                                onMoveToBottom = { onMoveItemToBottom(item.mediaId) },
                                onRemove = { pendingRemoveItem = item }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRemoveItem?.let { item ->
        FlatActionDialog(
            onDismissRequest = { pendingRemoveItem = null },
            message = "确定从「$title」移除“${item.title.ifBlank { "未命名" }}”吗？",
            actions = listOf(
                FlatDialogAction("取消", onClick = { pendingRemoveItem = null }),
                FlatDialogAction(
                    text = "移除",
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        pendingRemoveItem = null
                        onRemoveItem(item.mediaId)
                    }
                )
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItemRow(
    item: PlaylistItemWithSubtitles,
    showSubtitleStamp: Boolean,
    showTopDivider: Boolean,
    isDragging: Boolean,
    loadAudioMetadata: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = modifier
    ) {
        if (showTopDivider && !isDragging) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlaylistDetailHorizontalPadding)
                    .align(Alignment.TopCenter),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }
        val cvText = remember(item.artist, item.albumCv) {
            formatStoredCv(item.artist, item.albumCv.takeIf { it.isNotBlank() })
        }
        val durationText = remember(item.duration) { Formatting.formatTrackSeconds(item.duration) }
        val actions = remember(onPlay, onMoveToTop, onMoveToBottom, onRemove) {
            listOf(
                AudioItemMenuAction(
                    label = "播放",
                    onClick = onPlay
                ),
                AudioItemMenuAction(
                    label = "移至顶部",
                    onClick = onMoveToTop,
                    testTag = PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG
                ),
                AudioItemMenuAction(
                    label = "移至末尾",
                    onClick = onMoveToBottom,
                    testTag = PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG
                ),
                AudioItemMenuAction(
                    label = "移除",
                    onClick = onRemove,
                    showDividerBefore = true
                )
            )
        }
        AudioItemRow(
            title = item.title.ifBlank { "未命名" },
            sourcePath = item.uri.ifBlank { item.mediaId },
            loadAudioMetadata = loadAudioMetadata && !item.isVideo,
            subtitle = cvText,
            fixedTrailingSubtitle = durationText,
            showSubtitleStamp = showSubtitleStamp,
            menuButtonTestTag = "$PLAYLIST_DETAIL_ITEM_MENU_BUTTON_TAG_PREFIX:${item.mediaId}",
            onClick = onPlay,
            showClickIndication = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlaylistDetailHorizontalPadding, vertical = 2.dp),
            leadingContent = {
                AsmrAsyncImage(
                    model = item.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholderCornerRadius = 6,
                    peekAnySizeForInitial = true,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            },
            titleTextStyle = MaterialTheme.typography.bodyMedium,
            actions = actions
        )
    }
}

internal fun PlaylistItemWithSubtitles.toPlaybackEntity(): PlaylistItemEntity {
    return PlaylistItemEntity(
        playlistId = playlistId,
        mediaId = mediaId,
        title = title,
        artist = artist,
        albumTitle = albumTitle,
        uri = uri,
        artworkUri = playbackArtworkUri.ifBlank { artworkUri },
        albumId = albumId,
        trackId = trackId,
        rjCode = rjCode,
        albumWorkId = albumWorkId,
        trackGroup = trackGroup,
        lyricsRelativePathNoExt = lyricsRelativePathNoExt,
        remoteSubtitleSources = remoteSubtitleSources,
        mimeType = mimeType,
        isVideo = isVideo,
        itemOrder = itemOrder
    )
}

private fun SnapshotStateList<PlaylistItemWithSubtitles>.sync(
    items: List<PlaylistItemWithSubtitles>
) {
    clear()
    addAll(items)
}

internal fun SnapshotStateList<PlaylistItemWithSubtitles>.movePlaylistItems(
    from: ItemPosition,
    to: ItemPosition
) {
    if (isEmpty()) return
    val fromIndex = indexOfFirst { it.mediaId == from.key }
    val toIndex = indexOfFirst { it.mediaId == to.key }
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return
    add(toIndex, removeAt(fromIndex))
}

internal fun resolvePlaylistHeroItem(
    items: List<PlaylistItemWithSubtitles>,
    playback: PlaylistPlaybackEntity?,
    pendingMediaId: String? = null,
): PlaylistItemWithSubtitles? = items.firstOrNull { it.mediaId == pendingMediaId }
    ?: items.firstOrNull {
        it.playlistId == playback?.playlistId && it.mediaId == playback.mediaId
    }
    ?: items.firstOrNull()

internal fun resolveRequestedPlaylistHeroMediaId(playback: PlaybackSnapshot?, playlistId: Long): String? {
    val item = playback?.currentMediaItem ?: return null
    return item.mediaId.takeIf {
        it.isNotBlank() && item.sourcePlaylistId() == playlistId && playback.playWhenReady &&
            (playback.playbackState == Player.STATE_BUFFERING || playback.playbackState == Player.STATE_READY)
    }
}

internal fun resolvePlaylistHeroLivePosition(playback: PlaybackSnapshot?, playlistId: Long, mediaId: String): Long? {
    val item = playback?.currentMediaItem ?: return null
    if (item.sourcePlaylistId() != playlistId || item.mediaId != mediaId) return null
    return playback.positionMs.coerceAtLeast(0L).takeIf {
        playback.playbackState == Player.STATE_BUFFERING || playback.playbackState == Player.STATE_READY ||
            playback.playbackState == Player.STATE_ENDED
    }
}

internal class PlaylistHeroPlaybackState(
    private val playlistId: Long,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    val snapshot = mutableStateOf<PlaybackSnapshot?>(null)
    var pendingMediaId by mutableStateOf<String?>(null)
        private set
    private var startedMediaId: String? = null
    private var positionMs = 0L
    private var durationMs = 0L
    private var isCompleted = false
    private var startedAt = 0L
    private var observedAt = 0L
    private var playingMediaId: String? = null

    fun activate(history: PlaylistPlaybackEntity?, playback: PlaybackSnapshot) {
        confirm(history, playback)
        update(playback, capturePausedTransition = false, startObservation = true)
    }

    fun update(playback: PlaybackSnapshot) = update(playback, capturePausedTransition = true, startObservation = false)

    private fun update(playback: PlaybackSnapshot, capturePausedTransition: Boolean, startObservation: Boolean) {
        val previous = snapshot.value
        val item = playback.currentMediaItem
        val mediaId = item?.mediaId?.takeIf { it.isNotBlank() }
        val matchesPlaylist = item?.sourcePlaylistId() == playlistId
        playingMediaId = mediaId.takeIf { matchesPlaylist && playback.isPlaying }
        if (playingMediaId != null && (startedMediaId != mediaId || startObservation)) {
            startedMediaId = mediaId
            pendingMediaId = mediaId
            durationMs = 0L
            startedAt = nowEpochMs()
        }
        val hasPreviousTrack = previous?.currentMediaItem?.let {
            it.sourcePlaylistId() == playlistId && it.mediaId == startedMediaId
        } == true
        val pausedAfterPlaying = capturePausedTransition && hasPreviousTrack && previous?.isPlaying == true &&
            !playback.playWhenReady && playback.playbackState == Player.STATE_READY
        val ended = capturePausedTransition && playback.playbackState == Player.STATE_ENDED
        if (matchesPlaylist && mediaId != null && mediaId == startedMediaId &&
            (playback.isPlaying || pausedAfterPlaying || ended)
        ) {
            positionMs = playback.positionMs.coerceAtLeast(0L)
            durationMs = playback.durationMs.takeIf { it > 0L } ?: durationMs
            isCompleted = playback.playbackState == Player.STATE_ENDED ||
                (durationMs > 0L && positionMs >= durationMs)
            observedAt = nowEpochMs()
        }
        snapshot.value = playback
    }

    fun confirm(history: PlaylistPlaybackEntity?, playback: PlaybackSnapshot? = snapshot.value) {
        if (history?.playlistId == playlistId && history.mediaId == pendingMediaId && history.updatedAt >= startedAt) {
            pendingMediaId = null
        } else if (history?.playlistId == playlistId && pendingMediaId != null && history.updatedAt > startedAt &&
            !(playback?.isPlaying == true && playback.currentMediaItem?.sourcePlaylistId() == playlistId &&
                playback.currentMediaItem.mediaId == startedMediaId)
        ) {
            pendingMediaId = null
            startedMediaId = null
        }
    }

    fun observedPlayback(mediaId: String, history: PlaylistPlaybackEntity? = null): PlaylistPlaybackEntity? =
        if (canUseObservedPlayback(mediaId, history)) {
            PlaylistPlaybackEntity(playlistId, mediaId, positionMs, durationMs, isCompleted, updatedAt = observedAt)
        } else null

    fun observedPosition(mediaId: String, history: PlaylistPlaybackEntity?): Long? =
        positionMs.takeIf { canUseObservedPlayback(mediaId, history) }

    private fun canUseObservedPlayback(mediaId: String, history: PlaylistPlaybackEntity?): Boolean {
        if (mediaId != startedMediaId) return false
        if (mediaId == playingMediaId) return true
        val matchingHistory = history?.takeIf { it.playlistId == playlistId && it.mediaId == mediaId }
        return if (matchingHistory != null) matchingHistory.updatedAt <= observedAt else pendingMediaId == mediaId
    }
}

@Composable
private fun PlaylistDetailHero(
    items: List<PlaylistItemWithSubtitles>,
    playbackHistory: StateFlow<PlaylistPlaybackEntity?>?,
    playback: StateFlow<PlaybackSnapshot>?,
    isActive: Boolean,
    onPlay: (PlaylistItemWithSubtitles, Long) -> Unit
) {
    val historyState = playbackHistory?.collectAsStateWhileActive(isActive)
    val history = historyState?.value
    val playlistId = items.firstOrNull()?.playlistId ?: return
    val heroPlayback = remember(playback, playlistId) { PlaylistHeroPlaybackState(playlistId) }
    val liveState = heroPlayback.snapshot
    LaunchedEffect(heroPlayback, playback, isActive) {
        if (!isActive || playback == null) return@LaunchedEffect
        heroPlayback.activate(history, playback.value)
        playback.collect(heroPlayback::update)
    }
    val pendingMediaId = heroPlayback.pendingMediaId
    LaunchedEffect(heroPlayback, history, pendingMediaId) { heroPlayback.confirm(history) }
    val requestedMediaId by remember(liveState, playlistId) {
        derivedStateOf {
            resolveRequestedPlaylistHeroMediaId(liveState.value, playlistId)
        }
    }
    val item = resolvePlaylistHeroItem(items, history, requestedMediaId ?: pendingMediaId) ?: return
    val record = heroPlayback.observedPlayback(item.mediaId, history)
        ?: history?.takeIf { it.playlistId == item.playlistId && it.mediaId == item.mediaId }
    val isPlaying by remember(liveState, item.playlistId, item.mediaId) {
        derivedStateOf {
            val live = liveState.value
            live?.currentMediaItem?.sourcePlaylistId() == item.playlistId &&
                live.currentMediaItem.mediaId == item.mediaId && live.playWhenReady &&
                live.playbackState != Player.STATE_IDLE && live.playbackState != Player.STATE_ENDED
        }
    }
    val liveDuration by remember(liveState, item.playlistId, item.mediaId) {
        derivedStateOf {
            val live = liveState.value
            if (live?.currentMediaItem?.sourcePlaylistId() == item.playlistId &&
                live.currentMediaItem.mediaId == item.mediaId
            ) live.durationMs else 0L
        }
    }
    val hasEnded by remember(liveState, item.playlistId, item.mediaId) {
        derivedStateOf {
            val live = liveState.value
            live?.currentMediaItem?.sourcePlaylistId() == item.playlistId &&
                live.currentMediaItem.mediaId == item.mediaId && live.playbackState == Player.STATE_ENDED
        }
    }
    val heroRecord = if (hasEnded && record != null) record.copy(isCompleted = true) else record
    val displayPosition = remember(liveState, heroPlayback, item.playlistId, item.mediaId, record, history) {
        derivedStateOf {
            resolvePlaylistHeroLivePosition(liveState.value, item.playlistId, item.mediaId)
                ?: heroPlayback.observedPosition(item.mediaId, history) ?: record?.positionMs ?: 0L
        }
    }
    val positionMs = remember(displayPosition) { { displayPosition.value } }
    PlaylistTrackHero(
        item = item,
        playback = heroRecord,
        isPlaying = isPlaying,
        onPlay = { onPlay(item, if (heroRecord?.isCompleted == true) 0L else positionMs()) },
        positionMs = positionMs,
        durationMs = liveDuration.takeIf { it > 0L } ?: record?.durationMs ?: 0L,
        isActive = isActive,
        modifier = Modifier.fillMaxWidth()
    )
}
