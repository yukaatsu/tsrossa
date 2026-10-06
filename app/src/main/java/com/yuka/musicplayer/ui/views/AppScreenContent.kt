package com.yuka.musicplayer.ui.views

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.model.clearPlaylist
import com.yuka.musicplayer.model.removePlaylist
import com.yuka.musicplayer.model.sortLibraryFiles
import com.yuka.musicplayer.update.UpdateCheckState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen Content Container that switches between LIBRARY, PLAYLIST, TRACK, and SETTINGS views.
 */
@Composable
fun ColumnScope.AppScreenContent(
    viewState: ViewState,
    context: Context,
    coroutineScope: CoroutineScope,
    audioEngine: AudioEngine,
    audioManager: AudioManager,
    audioFocusRequest: AudioFocusRequest?,
    focusChangeListener: AudioManager.OnAudioFocusChangeListener,
    // Library
    currentDirectory: File,
    onNavigateDirectory: (File) -> Unit,
    filesInDir: List<File>,
    currentTrack: TrackInfo?,
    playlistSet: Set<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    librarySortMode: LibrarySortMode,
    onCycleSort: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onTrackActionTarget: (File) -> Unit,
    onPlayLibraryFile: (File) -> Unit,
    // Playlist
    playlistPaths: List<String>,
    onUpdatePlaylistPaths: (List<String>) -> Unit,
    isShuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: RepeatMode,
    onCycleRepeat: () -> Unit,
    priorityQueueSize: Int,
    onOpenQueue: () -> Unit,
    onPlayPlaylistFile: (File) -> Unit,
    onPlaylistFileRemoved: (String) -> Unit,
    onClearPlaylistAction: () -> Unit,
    // Track Player
    playbackPosition: Double,
    onSeekTo: (Double) -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onStopPlayback: () -> Unit,
    // Settings
    sharedPref: SharedPreferences,
    bgMode: String,
    onBgModeChange: (String) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit,
    keepAwake: Boolean,
    onKeepAwakeChange: (Boolean) -> Unit,
    defaultScreenStr: String,
    onDefaultScreenChange: (String) -> Unit,
    accentMode: String,
    onAccentModeChange: (String) -> Unit,
    accentFixedColorStr: String,
    onAccentFixedColorChange: (String) -> Unit,
    isNotificationGranted: Boolean,
    isStorageGranted: Boolean,
    isDndGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestDnd: () -> Unit,
    onOpenPermissionDialog: () -> Unit,
    onOpenHelp: () -> Unit,
    updateCheckState: UpdateCheckState,
    onCheckForUpdate: () -> Unit,
    onOpenUpdateDialog: () -> Unit
) {
    when (viewState) {
        ViewState.LIBRARY -> {
            LibraryView(
                currentDirectory = currentDirectory,
                filesInDir = filesInDir,
                playingFile = currentTrack?.file,
                playlistSet = playlistSet,
                searchQuery = searchQuery,
                sortMode = librarySortMode,
                onCycleSort = onCycleSort,
                onRefresh = onRefreshLibrary,
                onSearchChange = onSearchChange,
                onFileSelected = onPlayLibraryFile,
                onFileLongPressed = { file ->
                    if (!file.isDirectory) {
                        onTrackActionTarget(file)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        ViewState.PLAYLIST -> {
            PlaylistView(
                playlistPaths = playlistPaths,
                playingFile = currentTrack?.file,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                priorityQueueSize = priorityQueueSize,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onOpenQueue = onOpenQueue,
                onFileSelected = onPlayPlaylistFile,
                onFileRemoved = onPlaylistFileRemoved,
                onClearPlaylist = onClearPlaylistAction,
                onTrackLongPressed = onTrackActionTarget,
                modifier = Modifier.weight(1f)
            )
        }
        ViewState.TRACK -> {
            TrackView(
                track = currentTrack,
                playbackPosition = playbackPosition,
                isPlaying = isPlaying,
                currentVolume = currentVolume,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                priorityQueueSize = priorityQueueSize,
                onTogglePlay = onTogglePlay,
                onVolumeChange = onVolumeChange,
                onPlayNext = onPlayNext,
                onPlayPrev = onPlayPrev,
                onStop = onStopPlayback,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onOpenQueue = onOpenQueue,
                onSeekTo = onSeekTo,
                modifier = Modifier.weight(1f)
            )
        }
        ViewState.SETTINGS -> {
            SettingsView(
                prefs = sharedPref,
                bgMode = bgMode,
                onBgModeChange = onBgModeChange,
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
                hapticEnabled = hapticEnabled,
                onHapticChange = onHapticChange,
                keepAwake = keepAwake,
                onKeepAwakeChange = onKeepAwakeChange,
                defaultScreen = defaultScreenStr,
                onDefaultScreenChange = onDefaultScreenChange,
                accentMode = accentMode,
                onAccentModeChange = onAccentModeChange,
                accentFixedColorStr = accentFixedColorStr,
                onAccentFixedColorChange = onAccentFixedColorChange,
                isNotificationGranted = isNotificationGranted,
                isStorageGranted = isStorageGranted,
                isDndGranted = isDndGranted,
                onRequestNotification = onRequestNotification,
                onRequestStorage = onRequestStorage,
                onRequestDnd = onRequestDnd,
                onOpenPermissionDialog = onOpenPermissionDialog,
                onOpenHelp = onOpenHelp,
                updateCheckState = updateCheckState,
                onCheckForUpdate = onCheckForUpdate,
                onOpenUpdateDialog = onOpenUpdateDialog,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
