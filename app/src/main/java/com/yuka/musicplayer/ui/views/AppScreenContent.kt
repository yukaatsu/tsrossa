package com.yuka.musicplayer.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.model.ViewState
import java.io.File
import kotlin.math.absoluteValue

/**
 * Top-level swipeable container providing smooth swipe gestures between LIBRARY and PLAYLIST views,
 * and seamlessly displays SETTINGS view.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppScreenContent(
    viewState: ViewState,
    modifier: Modifier = Modifier,
    onNavChange: (ViewState) -> Unit = {},
    // Library
    musicRootDirectory: File,
    scannedItems: List<com.yuka.musicplayer.model.ScannedMusicItem>,
    isScanningLibrary: Boolean,
    isMusicDirectoryConfigured: Boolean = true,
    onChangeDirectory: () -> Unit,
    currentTrack: TrackInfo?,
    playlistSet: Set<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    librarySortMode: LibrarySortMode = LibrarySortMode.NAME_ASC,
    librarySortField: com.yuka.musicplayer.model.SortField = com.yuka.musicplayer.model.SortField.TITLE,
    librarySortOrder: com.yuka.musicplayer.model.SortOrder = com.yuka.musicplayer.model.SortOrder.ASCENDING,
    onCycleSort: () -> Unit = {},
    onSelectSort: ((LibrarySortMode) -> Unit)? = null,
    onSelectSortField: ((com.yuka.musicplayer.model.SortField, com.yuka.musicplayer.model.SortOrder) -> Unit)? = null,
    onShuffleLibrary: () -> Unit = {},
    onBulkAddToPlaylist: (List<File>) -> Unit = {},
    onBulkDelete: (List<File>) -> Unit = {},
    onRefreshLibrary: () -> Unit,
    onTrackActionTarget: (File) -> Unit,
    onSelectLibraryItem: (com.yuka.musicplayer.model.ScannedMusicItem) -> Unit,
    onLibraryModalOpenChanged: (Boolean) -> Unit = {},
    initialSelectedFile: File? = null,
    onConsumedInitialSelectedFile: () -> Unit = {},
    // Playlist
    playlists: List<com.yuka.musicplayer.model.Playlist>,
    selectedPlaylistId: String,
    activePlayingPlaylistId: String?,
    onSelectPlaylistTab: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onRenamePlaylistClick: (com.yuka.musicplayer.model.Playlist) -> Unit,
    onDeletePlaylistClick: (com.yuka.musicplayer.model.Playlist) -> Unit,
    isShuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: RepeatMode,
    onCycleRepeat: () -> Unit,
    priorityQueueSize: Int,
    onOpenQueue: () -> Unit,
    onPlayPlaylistFile: (File, String) -> Unit,
    onPlaylistFileRemoved: (String, String) -> Unit,
    onClearPlaylistAction: (String) -> Unit,
    onPlayPlaylistSequential: (com.yuka.musicplayer.model.Playlist) -> Unit = {},
    onPlayPlaylistShuffled: (com.yuka.musicplayer.model.Playlist) -> Unit = {},
    onTogglePinPlaylist: (com.yuka.musicplayer.model.Playlist) -> Unit = {},
    isNowPlayingVisible: Boolean = false
) {
    val pagerState = rememberPagerState(
        initialPage = if (viewState == ViewState.PLAYLIST) 1 else 0,
        pageCount = { 2 }
    )

    LaunchedEffect(viewState) {
        val targetPage = when (viewState) {
            ViewState.LIBRARY -> 0
            ViewState.PLAYLIST -> 1
            else -> 0
        }
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 250,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val target = if (pagerState.settledPage == 0) ViewState.LIBRARY else ViewState.PLAYLIST
        if (viewState != target && viewState != ViewState.TRACK && viewState != ViewState.SETTINGS) {
            onNavChange(target)
        }
    }

    val isPlaylistSettledActive = remember {
        derivedStateOf { pagerState.settledPage == 1 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 1,
            pageSpacing = 12.dp,
            key = { it },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Hide off-screen pages completely so adjacent pages never bleed into view
                        alpha = if (absOffset >= 0.995f) 0f else (1f - (0.15f * absOffset))
                        clip = true
                    }
            ) {
                if (page == 0) {
                    LibraryView(
                        musicRootDirectory = musicRootDirectory,
                        scannedItems = scannedItems,
                        isScanning = isScanningLibrary,
                        isMusicDirectoryConfigured = isMusicDirectoryConfigured,
                        currentTrack = currentTrack,
                        playlistSet = playlistSet,
                        searchQuery = searchQuery,
                        sortField = librarySortField,
                        sortOrder = librarySortOrder,
                        sortMode = librarySortMode,
                        onCycleSort = onCycleSort,
                        onSelectSort = onSelectSort,
                        onSelectSortField = onSelectSortField,
                        onShufflePlay = onShuffleLibrary,
                        onRefresh = onRefreshLibrary,
                        onChangeDirectory = onChangeDirectory,
                        onSearchChange = onSearchChange,
                        onTrackSelected = onSelectLibraryItem,
                        onTrackLongPressed = onTrackActionTarget,
                        onBulkAddToPlaylist = onBulkAddToPlaylist,
                        onBulkDelete = onBulkDelete,
                        onLocalModalOpenChanged = onLibraryModalOpenChanged,
                        initialSelectedFile = initialSelectedFile,
                        onConsumedInitialSelectedFile = onConsumedInitialSelectedFile,
                        isNowPlayingVisible = isNowPlayingVisible,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PlaylistView(
                        playlists = playlists,
                        selectedPlaylistId = selectedPlaylistId,
                        activePlayingPlaylistId = activePlayingPlaylistId,
                        currentTrack = currentTrack,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        priorityQueueSize = priorityQueueSize,
                        onSelectPlaylistTab = onSelectPlaylistTab,
                        onCreatePlaylistClick = onCreatePlaylistClick,
                        onRenamePlaylistClick = onRenamePlaylistClick,
                        onDeletePlaylistClick = onDeletePlaylistClick,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onOpenQueue = onOpenQueue,
                        onFileSelected = onPlayPlaylistFile,
                        onFileRemoved = onPlaylistFileRemoved,
                        onClearPlaylist = onClearPlaylistAction,
                        onPlayPlaylistSequential = onPlayPlaylistSequential,
                        onPlayPlaylistShuffled = onPlayPlaylistShuffled,
                        onTrackLongPressed = onTrackActionTarget,
                        isPlaylistTabActive = isPlaylistSettledActive.value,
                        isNowPlayingVisible = isNowPlayingVisible,
                        onTogglePinPlaylist = onTogglePinPlaylist,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
