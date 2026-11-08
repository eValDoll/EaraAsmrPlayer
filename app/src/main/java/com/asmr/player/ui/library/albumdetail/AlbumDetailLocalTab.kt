package com.asmr.player.ui.library

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.subtitle.SubtitleDeviceCapability
import com.asmr.player.subtitle.SubtitleFailureMessages
import com.asmr.player.subtitle.SubtitleGenerationTarget
import com.asmr.player.subtitle.SubtitleTaskRepository
import com.asmr.player.subtitle.SubtitleModelRepository
import com.asmr.player.subtitle.SubtitleModelInstallationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import com.asmr.player.ui.common.ImagePreviewItem
import com.asmr.player.ui.common.ImagePreviewRequest
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.util.isOnlineTrackPath

@Composable
internal fun AlbumLocalBreadcrumbTab(
    stateKey: String,
    initialCurrentPath: String,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    topContentPadding: Dp,
    animateIntro: Boolean,
    album: Album,
    header: @Composable () -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    preferredCurrentPath: String,
    onTogglePreferredCurrentPath: (String, Boolean) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDownloadOnlineTrack: (Track, String) -> Unit,
    onManageTrackTags: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onDeleteTreeEntry: (LocalTreeDeletionTarget, (Boolean) -> Unit) -> Unit,
    onToggleCoverFromImage: (String) -> Unit,
    onPreviewImages: (ImagePreviewRequest) -> Unit,
    onPreviewFile: (DirectoryFileItem) -> Unit,
    onSubtitleGenerationError: (String) -> Unit,
    onSubtitleGenerationUnavailable: (String) -> Unit,
    onSubtitleGenerationQueued: (String) -> Unit,
    onListStateAvailable: (LazyListState?) -> Unit = {},
) {
    var locallyDeletedTrackIds by remember(stateKey) { mutableStateOf(emptySet<Long>()) }
    var treeRevision by rememberSaveable(stateKey) { mutableIntStateOf(0) }
    var pendingDeletion by remember { mutableStateOf<LocalTreeDeletionTarget?>(null) }
    val queueTracks = remember(album.id, album.tracks, locallyDeletedTrackIds) {
        album.tracks
            .filterNot { it.id in locallyDeletedTrackIds }
            .sortedBy { it.path }
    }
    val queueTrackIds = remember(queueTracks) {
        queueTracks.asSequence().map { it.id }.filter { it > 0L }.distinct().toList()
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val subtitleModelRepository = remember(context) { SubtitleModelRepository.get(context) }
    val subtitleModelState by subtitleModelRepository.state.collectAsStateWithLifecycle()
    val subtitleDeviceCapability = remember(context) { SubtitleDeviceCapability.evaluate(context) }
    val subtitleFeatureSupported = subtitleDeviceCapability.supported
    val subtitleModelAvailable = subtitleFeatureSupported &&
        subtitleModelState.installation(subtitleModelState.activeModelId) is
        SubtitleModelInstallationState.Available
    val allPaths = remember(album) { album.getAllLocalPaths() }
    var currentPath by rememberSaveable(stateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }

    LaunchedEffect(album.tracks) {
        val currentTrackIds = album.tracks.asSequence().map { it.id }.toSet()
        locallyDeletedTrackIds = locallyDeletedTrackIds.intersect(currentTrackIds)
    }

    val listState = rememberSaveable("scroll:$stateKey", saver = LazyListState.Saver) {
        LazyListState(initialScroll.first, initialScroll.second)
    }
    DisposableEffect(listState) {
        onListStateAvailable(listState)
        onDispose { onListStateAvailable(null) }
    }
    PersistAlbumDetailListScroll(
        listState = listState,
        stateKey = stateKey,
        onPersistScroll = onPersistScroll
    )
    LaunchedEffect(currentPath, stateKey) {
        onPersistCurrentPath(currentPath)
    }

    val subtitleTrackIds by produceState(initialValue = emptySet<Long>(), key1 = queueTrackIds) {
        if (queueTrackIds.isEmpty()) return@produceState
        AppDatabaseProvider.get(context).trackDao()
            .observeTrackIdsWithSubtitles(queueTrackIds)
            .collect { value = it.toSet() }
    }
    val remoteSubtitleTrackIds by produceState(initialValue = emptySet<Long>(), key1 = queueTrackIds) {
        value = withContext(Dispatchers.IO) {
            if (queueTrackIds.isEmpty()) emptySet()
            else AppDatabaseProvider.get(context).remoteSubtitleSourceDao()
                .getTrackIdsWithRemoteSources(queueTrackIds)
                .toSet()
        }
    }
    val onlineSavedResources by produceState(
        initialValue = emptyList<com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity>(),
        key1 = album.id
    ) {
        if (album.id <= 0L) return@produceState
        AppDatabaseProvider.get(context).onlineSavedResourceDao()
            .observeForAlbum(album.id)
            .collect { value = it }
    }
    val treeIndex by produceState<LocalTreeIndex?>(
        null,
        allPaths,
        queueTracks,
        onlineSavedResources,
        treeRevision,
    ) {
        value = withContext(Dispatchers.IO) {
            loadOrBuildLocalTreeIndex(
                context = context,
                albumId = album.id,
                albumPaths = allPaths,
                tracks = queueTracks,
                onlineSavedResources = onlineSavedResources,
                sources = localTreeSourcesForAlbum(album),
            )
        }
    }
    val browser = remember(treeIndex, currentPath, subtitleTrackIds, remoteSubtitleTrackIds, album) {
        treeIndex?.let { index ->
            buildLocalDirectoryBrowser(
                index = index,
                currentPath = currentPath,
                album = album,
                shouldShowSubtitleStamp = { track ->
                    track?.let { subtitleTrackIds.contains(it.id) || remoteSubtitleTrackIds.contains(it.id) } == true
                }
            )
        }
    }
    val currentDirectorySubtitleGenerationTracks = remember(
        treeIndex,
        currentPath
    ) {
        treeIndex?.let { index ->
            collectSubtitleGenerationTracks(
                index = index,
                currentPath = currentPath,
                unavailableTrackIds = emptySet()
            )
        }.orEmpty()
    }
    val startSubtitleGeneration: (List<Track>) -> Unit = { tracks ->
        val targets = tracks.distinctBy { it.id }.map { track ->
            SubtitleGenerationTarget(
                trackId = track.id,
                title = track.title
            )
        }
        val unavailableMessage = when {
            !subtitleFeatureSupported -> subtitleDeviceCapability.message
            !subtitleModelAvailable -> SubtitleModelRepository.MODEL_REQUIRED_MESSAGE
            else -> null
        }
        if (unavailableMessage != null) {
            onSubtitleGenerationUnavailable(unavailableMessage)
        } else if (targets.isNotEmpty()) {
            scope.launch {
                try {
                    val handle = withContext(Dispatchers.IO) {
                        SubtitleTaskRepository.get(context).enqueueGeneration(targets)
                    }
                    if (tracks.any { it.id in subtitleTrackIds }) {
                        onSubtitleGenerationQueued(appString(R.string.ui_subtitles_already_exist_they_will_be_overwritten_and))
                    }
                    onSubtitleGenerationQueued(
                        if (handle.reusedExisting) appString(R.string.ui_the_selected_audio_already_has_a_subtitle_task) else appString(R.string.ui_already_added_to_subtitle_task_queue)
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    val message = error.message?.takeIf { it.isNotBlank() } ?: appString(R.string.ui_unable_to_start_generating_and_translating_subtitles)
                    if (SubtitleFailureMessages.isUserActionWarning(message)) {
                        onSubtitleGenerationUnavailable(message)
                    } else {
                        onSubtitleGenerationError(message)
                    }
                }
            }
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        state = listState,
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "local-header:$stateKey") { header() }
        val browserValue = browser
        if (browserValue == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_there_are_no_files_in_the_current_directory))
                }
            }
        } else {
            item {
                DirectoryBrowserPanel(
                    panelKey = stateKey,
                    currentPath = currentPath,
                    breadcrumbs = browserValue.breadcrumbs,
                    batchTargets = browserValue.batchTargets,
                    folders = browserValue.folders,
                    files = browserValue.files,
                    onNavigate = { path -> currentPath = path },
                    onDeleteFolder = { folder ->
                        pendingDeletion = LocalTreeDeletionTarget(
                            title = folder.title,
                            relativePath = folder.path,
                            isDirectory = true,
                            trackIds = folder.descendantTrackIds,
                            hasLocalContent = folder.hasLocalContent,
                        )
                    },
                    onAddToFavorites = onAddMediaItemsToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                    onGenerateSubtitlesForCurrentDirectory = {
                        if (currentDirectorySubtitleGenerationTracks.isEmpty()) {
                            scope.launch {
                                val message = withContext(Dispatchers.Default) {
                                    subtitleGenerationDirectoryRequirementMessage(treeIndex, currentPath)
                                }
                                onSubtitleGenerationUnavailable(message)
                            }
                        } else {
                            startSubtitleGeneration(currentDirectorySubtitleGenerationTracks)
                        }
                    },
                    subtitleGenerationForCurrentDirectoryEnabled = subtitleFeatureSupported &&
                        currentDirectorySubtitleGenerationTracks.isNotEmpty(),
                    onGenerateSubtitlesForSelectedFiles = { selectedFiles ->
                        val tracks = selectedFiles.mapNotNull { file ->
                            subtitleGenerationTrackForFile(file, unavailableTrackIds = emptySet())
                        }
                        if (tracks.isEmpty()) {
                            onSubtitleGenerationUnavailable(subtitleGenerationSelectionRequirementMessage(selectedFiles))
                        } else {
                            startSubtitleGeneration(tracks)
                        }
                    },
                    canGenerateSubtitleForSelectedFile = { file ->
                        subtitleFeatureSupported &&
                            subtitleGenerationTrackForFile(file, unavailableTrackIds = emptySet()) != null
                    },
                    subtitleModelAvailable = subtitleModelAvailable,
                    animateIntro = animateIntro,
                    preferredPath = preferredCurrentPath,
                    onTogglePreferredPath = { enabled ->
                        onTogglePreferredCurrentPath(currentPath, enabled)
                    },
                    folderKeyPrefix = "folder",
                    fileKeyPrefix = "file",
                    fileContent = { file, selectionMode, selected, selectedPosition, enterSelectionMode, onSelectedChange ->
                        val track = file.track
                        val downloadableTrack = downloadableOnlineAudioTrack(file)
                        DirectoryFileRow(
                            file = file,
                            loadAudioMetadata = !listState.isScrollInProgress,
                            albumCv = album.cv,
                            loadRemoteFileSize = { null },
                            onPrimary = {
                                scope.launch {
                                    val prepared = withContext(Dispatchers.Default) {
                                        val artwork = albumArtworkLabel(album)
                                        val artist = albumArtistLabel(album)
                                        if ((file.fileType == TreeFileType.Audio && track != null) || file.fileType == TreeFileType.Video) {
                                            val nodes = treeIndex?.let { siblingPlayableNodesForEntry(it, file.path) }.orEmpty()
                                            val siblingItems = nodes.mapNotNull { node ->
                                                val abs = node.absolutePath ?: return@mapNotNull null
                                                when (node.fileType) {
                                                    TreeFileType.Audio -> node.track?.let { MediaItemFactory.fromTrack(album, it) }
                                                    TreeFileType.Video -> buildVideoMediaItem(
                                                        title = node.name,
                                                        uriOrPath = abs,
                                                        artworkUri = artwork,
                                                        artist = artist
                                                    )
                                                    else -> null
                                                }
                                            }
                                            val clickedId = when (file.fileType) {
                                                TreeFileType.Audio -> track?.path?.trim().orEmpty()
                                                TreeFileType.Video -> file.absolutePath.trim()
                                                else -> ""
                                            }
                                            val items = if (siblingItems.isNotEmpty()) {
                                                siblingItems
                                            } else {
                                                when (file.fileType) {
                                                    TreeFileType.Audio -> queueTracks.map { MediaItemFactory.fromTrack(album, it) }
                                                    TreeFileType.Video -> listOfNotNull(
                                                        buildVideoMediaItem(
                                                            title = file.title,
                                                            uriOrPath = file.absolutePath,
                                                            artworkUri = artwork,
                                                            artist = artist
                                                        )
                                                    )
                                                    else -> emptyList()
                                                }
                                            }
                                            if (items.isNotEmpty()) {
                                                val startIndex = items.indexOfFirst { it.mediaId.trim() == clickedId }
                                                    .takeIf { it >= 0 } ?: 0
                                                return@withContext PreparedMediaPlayback(items, startIndex)
                                            }
                                        }
                                        null
                                    }
                                    if (prepared != null) {
                                        onPlayMediaItems(prepared.items, prepared.startIndex)
                                    } else {
                                        if (file.fileType == TreeFileType.Image) {
                                            buildDirectoryImagePreviewRequest(
                                                files = browserValue.files,
                                                clickedPath = file.path,
                                                toPreviewItem = { imageFile ->
                                                    imageFile.absolutePath.takeIf { it.isNotBlank() }?.let { path ->
                                                        ImagePreviewItem(
                                                            key = imageFile.path,
                                                            title = imageFile.title,
                                                            openPathOrUrl = path,
                                                            prepareImage = {
                                                                com.asmr.player.ui.common.ImagePreviewPreparedItem(
                                                                    imageModel = path,
                                                                    openPathOrUrl = path
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            )?.let(onPreviewImages) ?: onPreviewFile(file)
                                        } else {
                                            onPreviewFile(file)
                                        }
                                    }
                                }
                            },
                            selectionMode = selectionMode,
                            selected = selected,
                            selectedPosition = selectedPosition,
                            onEnterSelectionMode = enterSelectionMode,
                            onSelectedChange = onSelectedChange,
                            isCoverSelected = album.manualCoverPath.isNotBlank() && album.manualCoverPath == file.absolutePath,
                            onToggleCover = if (canSetDirectoryImageAsLocalCover(file)) {
                                { onToggleCoverFromImage(file.absolutePath) }
                            } else {
                                null
                            },
                            onDownload = downloadableTrack?.let {
                                { onDownloadOnlineTrack(it, file.path) }
                            },
                            onAddToQueue = track?.let { { onAddToQueue(it); Unit } },
                            onAddToPlaylist = track?.let { { onAddToPlaylist(it) } },
                            onGenerateSubtitles = if (subtitleFeatureSupported) {
                                subtitleGenerationTrackForFile(
                                    file = file,
                                    unavailableTrackIds = emptySet()
                                )?.let { subtitleGenerationTrack ->
                                    {
                                        if (subtitleModelAvailable) {
                                            startSubtitleGeneration(listOf(subtitleGenerationTrack))
                                        } else {
                                            onSubtitleGenerationUnavailable(SubtitleModelRepository.MODEL_REQUIRED_MESSAGE)
                                        }
                                    }
                                }
                            } else null,
                            subtitleGenerationEnabled = subtitleModelAvailable,
                            onManageTags = track?.let { if (!isOnlineTrackPath(it.path)) { { onManageTrackTags(it) } } else null },
                            onRemoveFromAlbum = track?.let { { onRemoveTrack(it) } },
                            onDelete = if (file.fileType != TreeFileType.Audio) {
                                {
                                    pendingDeletion = LocalTreeDeletionTarget(
                                        title = file.title,
                                        relativePath = file.path,
                                        absolutePath = file.absolutePath,
                                        isDirectory = false,
                                        trackIds = file.track?.id?.takeIf { it > 0L }?.let(::listOf).orEmpty(),
                                        hasLocalContent = !file.absolutePath.startsWith("http", ignoreCase = true),
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                )
            }
        }
    }

    pendingDeletion?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(if (target.isDirectory) appString(R.string.ui_delete_directory_9295d5) else appString(R.string.ui_delete_files)) },
            text = {
                val message = when {
                    target.isDirectory && target.hasLocalContent ->
                        appString(R.string.ui_directory_and_its_entire_contents_will_be_permanently, target.title)
                    target.isDirectory ->
                        appString(R.string.ui_and_the_online_resources_it_contains_will_be, target.title)
                    target.hasLocalContent ->
                        appString(R.string.ui_file_will_be_permanently_deleted_this_operation_is, target.title)
                    else ->
                        appString(R.string.ui_resource_will_be_removed_from_the_local_library, target.title)
                }
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        onDeleteTreeEntry(target) { deleted ->
                            if (deleted) {
                                locallyDeletedTrackIds = locallyDeletedTrackIds + target.trackIds
                                treeRevision += 1
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ui_cancel))
                }
            }
        )
    }

}
