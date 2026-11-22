package com.yuka.musicplayer.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.util.FuzzySearchHelper
import java.io.File

/**
 * Material 3 Glassmorphic Playlist Screen:
 * - Level 1: Playlist Cards Overview with Fuzzy Search & New Playlist button.
 * - Level 2: Playlist Detail with sequential Play & random Shuffle buttons, track list, and search.
 */
@Composable
fun PlaylistView(
    playlists: List<Playlist>,
    selectedPlaylistId: String,
    activePlayingPlaylistId: String?,
    currentTrack: TrackInfo?,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    priorityQueueSize: Int,
    onSelectPlaylistTab: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onRenamePlaylistClick: (Playlist) -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onFileSelected: (File, String) -> Unit,
    onFileRemoved: (String, String) -> Unit,
    onClearPlaylist: (String) -> Unit,
    onPlayPlaylistSequential: (Playlist) -> Unit = {},
    onPlayPlaylistShuffled: (Playlist) -> Unit = {},
    onTrackLongPressed: (File) -> Unit = {},
    isPlaylistTabActive: Boolean = true,
    isNowPlayingVisible: Boolean = false,
    onTogglePinPlaylist: (Playlist) -> Unit = {},
    scannedItemsMap: Map<String, com.yuka.musicplayer.model.ScannedMusicItem> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    // Navigation state: null = Level 1 (Cards Overview), non-null = Level 2 (Playlist Detail)
    var activeDetailPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var trackSearchQuery by remember { mutableStateOf("") }

    // Synchronize if selectedPlaylistId was changed externally
    LaunchedEffect(selectedPlaylistId) {
        if (selectedPlaylistId.isNotEmpty() && activeDetailPlaylistId != null && activeDetailPlaylistId != selectedPlaylistId) {
            activeDetailPlaylistId = selectedPlaylistId
        }
    }

    val currentPlaylist = remember(playlists, activeDetailPlaylistId) {
        playlists.firstOrNull { it.id == activeDetailPlaylistId }
    }

    // Handle Android system back gesture to navigate from Detail -> Cards Overview
    // Only active when Now Playing is NOT displayed over it
    BackHandler(enabled = isPlaylistTabActive && !isNowPlayingVisible && activeDetailPlaylistId != null) {
        activeDetailPlaylistId = null
        trackSearchQuery = ""
        focusManager.clearFocus()
    }

    AnimatedContent(
        targetState = currentPlaylist,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> fullWidth / 4 }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> -fullWidth / 5 }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 140)
                    )
                )
            } else {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> -fullWidth / 5 }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> fullWidth / 4 }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 140)
                    )
                )
            }
        },
        label = "playlistNavigationTransition",
        modifier = modifier.fillMaxSize()
    ) { playlistTarget ->
        if (playlistTarget == null) {
            val filteredPlaylists = remember(playlists, playlistSearchQuery) {
                val list = if (playlistSearchQuery.isEmpty()) {
                    playlists
                } else {
                    playlists.filter { playlist ->
                        FuzzySearchHelper.matches(playlist.name, playlistSearchQuery)
                    }
                }
                list.sortedWith(compareByDescending<Playlist> { it.isPinned }.thenBy { it.createdAt })
            }

            PlaylistOverviewSection(
                filteredPlaylists = filteredPlaylists,
                playlistSearchQuery = playlistSearchQuery,
                onPlaylistSearchQueryChange = { playlistSearchQuery = it },
                activePlayingPlaylistId = activePlayingPlaylistId,
                currentTrack = currentTrack,
                onCreatePlaylistClick = onCreatePlaylistClick,
                onSelectPlaylist = { id ->
                    activeDetailPlaylistId = id
                    onSelectPlaylistTab(id)
                },
                onTogglePinPlaylist = onTogglePinPlaylist,
                onRenamePlaylistClick = onRenamePlaylistClick,
                onDeletePlaylistClick = onDeletePlaylistClick
            )
        } else {
            PlaylistDetailSection(
                playlist = playlistTarget,
                isCurrentPlaying = activePlayingPlaylistId == playlistTarget.id,
                currentTrack = currentTrack,
                repeatMode = repeatMode,
                priorityQueueSize = priorityQueueSize,
                trackSearchQuery = trackSearchQuery,
                onTrackSearchQueryChange = { trackSearchQuery = it },
                onBackClick = {
                    activeDetailPlaylistId = null
                    trackSearchQuery = ""
                    focusManager.clearFocus()
                },
                onClearPlaylist = onClearPlaylist,
                onRenamePlaylistClick = onRenamePlaylistClick,
                onPlayPlaylistSequential = onPlayPlaylistSequential,
                onPlayPlaylistShuffled = onPlayPlaylistShuffled,
                onCycleRepeat = onCycleRepeat,
                onOpenQueue = onOpenQueue,
                onFileSelected = onFileSelected,
                onFileRemoved = onFileRemoved,
                onTrackLongPressed = onTrackLongPressed,
                scannedItemsMap = scannedItemsMap
            )
        }
    }
}
