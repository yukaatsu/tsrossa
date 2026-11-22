package com.yuka.musicplayer.ui.views

import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.model.ViewState
import java.io.File
import kotlin.math.absoluteValue

/**
 * Pure helper determining whether a horizontal drag/fling delta should be blocked at the outer
 * boundaries of the 2-page HorizontalPager (Page 0 = Library left boundary, Page 1 = Playlist right boundary).
 * - On Page 0 (Library): blocks rightward drag (deltaX > 0f) past the left boundary.
 * - On Page 1 (Playlist): blocks leftward drag (deltaX < 0f) past the right boundary.
 */
internal fun shouldBlockPagerHorizontalDrag(
    currentPage: Int,
    currentPageOffsetFraction: Float,
    deltaX: Float,
    pageCount: Int = 2
): Boolean {
    if (deltaX == 0f || pageCount <= 0) return false
    val atLeftBoundary = currentPage <= 0 && currentPageOffsetFraction <= 0.001f
    if (atLeftBoundary && deltaX > 0f) return true
    val atRightBoundary = currentPage >= (pageCount - 1) && currentPageOffsetFraction >= -0.001f
    if (atRightBoundary && deltaX < 0f) return true
    return false
}

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
    isAllFilesAccessGranted: Boolean = true,
    onRequestAllFilesAccess: () -> Unit = {},
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
    onPlayFolderTracks: ((List<File>, Boolean) -> Unit)? = null,
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
    scannedItemsMap: Map<String, com.yuka.musicplayer.model.ScannedMusicItem> = emptyMap(),
    isNowPlayingVisible: Boolean = false
) {
    val pagerState = rememberPagerState(
        initialPage = if (viewState == ViewState.PLAYLIST) 1 else 0,
        pageCount = { 2 }
    )

    val targetPage = when (viewState) {
        ViewState.LIBRARY -> 0
        ViewState.PLAYLIST -> 1
        else -> 0
    }

    // 1. Synchronize pager whenever targetPage (viewState) changes, even if a previous scroll was in progress
    //    or if currentPageOffsetFraction was left non-zero.
    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage || pagerState.currentPageOffsetFraction.absoluteValue > 0.0005f) {
            try {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    pageOffsetFraction = 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 250,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            } catch (_: Exception) {
                runCatching { pagerState.scrollToPage(targetPage, 0f) }
            }
        }
    }

    // 2. Self-Healing Center-Snap Guard: whenever user touch/scroll stops (isScrollInProgress == false),
    //    if the pager is ever left at a fractional horizontal offset (e.g. edge gesture cancel at right boundary),
    //    immediately animate/snap it back to exact center (pageOffsetFraction = 0f) and sync viewState.
    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(
                pagerState.isScrollInProgress,
                pagerState.currentPage,
                pagerState.currentPageOffsetFraction
            )
        }.collect { (isScrolling, currentPage, offsetFraction) ->
            if (!isScrolling) {
                if (offsetFraction.absoluteValue > 0.0005f) {
                    val snapPage = if (offsetFraction > 0.5f) {
                        (currentPage + 1).coerceIn(0, 1)
                    } else if (offsetFraction < -0.5f) {
                        (currentPage - 1).coerceIn(0, 1)
                    } else {
                        currentPage.coerceIn(0, 1)
                    }
                    try {
                        pagerState.animateScrollToPage(
                            page = snapPage,
                            pageOffsetFraction = 0f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            )
                        )
                    } catch (_: Exception) {
                        runCatching { pagerState.scrollToPage(snapPage, 0f) }
                    }
                } else {
                    val settledTarget = if (currentPage == 0) ViewState.LIBRARY else ViewState.PLAYLIST
                    if (viewState != settledTarget && viewState != ViewState.TRACK && viewState != ViewState.SETTINGS) {
                        onNavChange(settledTarget)
                    }
                }
            }
        }
    }

    // 3. Directional Boundary-Lock NestedScrollConnection: prevents pulling Playlist (page 1)
    //    leftward past the right boundary or pulling Library (page 0) rightward past the left boundary.
    val boundaryLockNestedScroll = remember(pagerState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (kotlin.math.abs(available.x) > kotlin.math.abs(available.y)) {
                    if (shouldBlockPagerHorizontalDrag(
                            currentPage = pagerState.currentPage,
                            currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                            deltaX = available.x,
                            pageCount = 2
                        )
                    ) {
                        return Offset(available.x, 0f)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (shouldBlockPagerHorizontalDrag(
                        currentPage = pagerState.currentPage,
                        currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                        deltaX = available.x,
                        pageCount = 2
                    )
                ) {
                    return Velocity(available.x, 0f)
                }
                return Velocity.Zero
            }
        }
    }

    val isPlaylistSettledActive = remember {
        derivedStateOf { pagerState.settledPage == 1 }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(boundaryLockNestedScroll)
    ) {
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null
        ) {
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                pageSpacing = 0.dp,
                key = { it },
                modifier = Modifier.fillMaxSize()
            ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
                        // Hide off-screen pages completely in the Draw Phase with zero recomposition
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
                        isAllFilesAccessGranted = isAllFilesAccessGranted,
                        onRequestAllFilesAccess = onRequestAllFilesAccess,
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
                        onPlayFolderTracks = onPlayFolderTracks,
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
                        scannedItemsMap = scannedItemsMap,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            }
        }
    }
}
