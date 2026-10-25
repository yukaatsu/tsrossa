package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.yuka.musicplayer.audio.AudioPlayerManager
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.sortLibraryFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Controller/handler for all modal actions (Queue manipulation, Playlist operations, Track Context actions, Permissions).
 */
class AppModalActionHandler(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val sharedPref: SharedPreferences,
    private val getPriorityQueue: () -> List<File>,
    private val setPriorityQueue: (List<File>) -> Unit,
    private val getAllPlaylists: () -> List<com.yuka.musicplayer.model.Playlist>,
    private val setAllPlaylists: (List<com.yuka.musicplayer.model.Playlist>) -> Unit,
    private val getSelectedPlaylistId: () -> String,
    private val setSelectedPlaylistId: (String) -> Unit,
    private val getPlaylistPaths: () -> List<String>,
    private val getCurrentPlaybackSourceStr: () -> String,
    private val isShuffleEnabled: () -> Boolean,
    private val getShuffledList: () -> List<File>,
    private val setShuffledList: (List<File>) -> Unit,
    private val syncPreparedNextTrack: () -> Unit,
    private val playTrack: (File, Boolean, Boolean, PlaybackSource, String?) -> Unit,
    private val onDismissPermissionDialog: () -> Unit,
    private val onDismissQueuePanel: () -> Unit,
    private val onDismissTrackOptions: () -> Unit,
    private val onShowSelectPlaylistDialog: (File) -> Unit
) {

    fun onCompletePermissions() {
        sharedPref.edit().putBoolean("completed_permission_onboarding", true).apply()
        onDismissPermissionDialog()
    }

    fun onRemoveFromQueue(idx: Int) {
        val currentQueue = getPriorityQueue()
        if (idx in currentQueue.indices) {
            val updated = currentQueue.filterIndexed { i, _ -> i != idx }
            setPriorityQueue(updated)
            AudioPlayerManager.priorityQueue = updated
            syncPreparedNextTrack()
        }
    }

    fun onClearQueue() {
        setPriorityQueue(emptyList())
        AudioPlayerManager.priorityQueue = emptyList()
        syncPreparedNextTrack()
    }

    fun onSelectQueueTrack(file: File) {
        onDismissQueuePanel()
        val currentQueue = getPriorityQueue()
        if (currentQueue.any { it.absolutePath == file.absolutePath }) {
            val updated = currentQueue.filter { it.absolutePath != file.absolutePath }
            setPriorityQueue(updated)
            AudioPlayerManager.priorityQueue = updated
        }
        val targetSource = if (getPlaylistPaths().any { it == file.absolutePath }) {
            PlaybackSource.PLAYLIST
        } else {
            PlaybackSource.LIBRARY
        }
        playTrack(file, false, true, targetSource, null)
    }

    fun onPlayTrackAction(target: File, targetSource: PlaybackSource) {
        onDismissTrackOptions()
        val playlistId = if (targetSource == PlaybackSource.PLAYLIST) getSelectedPlaylistId() else null
        playTrack(target, false, true, targetSource, playlistId)
    }

    fun onPlayNextInQueue(target: File) {
        val updated = listOf(target) + getPriorityQueue()
        setPriorityQueue(updated)
        AudioPlayerManager.priorityQueue = updated
        syncPreparedNextTrack()
        Toast.makeText(context, "Added as next track", Toast.LENGTH_SHORT).show()
    }

    fun onAddToQueue(target: File) {
        val updated = getPriorityQueue() + target
        setPriorityQueue(updated)
        AudioPlayerManager.priorityQueue = updated
        syncPreparedNextTrack()
        Toast.makeText(context, "Added to queue (#${updated.size})", Toast.LENGTH_SHORT).show()
    }

    fun onOpenAddToPlaylist(target: File) {
        onDismissTrackOptions()
        onShowSelectPlaylistDialog(target)
    }

    fun onAddToPlaylist(playlistId: String, pathToAdd: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val updated = com.yuka.musicplayer.util.PlaylistStorageHelper.addTrackToPlaylist(context, playlistId, pathToAdd)
            if (updated != null) {
                withContext(Dispatchers.Main) {
                    setAllPlaylists(updated)
                    Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onRemoveFromPlaylist(playlistId: String, pathToRemove: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val updated = com.yuka.musicplayer.util.PlaylistStorageHelper.removeTrackFromPlaylist(context, playlistId, pathToRemove)
            if (updated != null) {
                withContext(Dispatchers.Main) {
                    setAllPlaylists(updated)
                    if (getCurrentPlaybackSourceStr() == PlaybackSource.PLAYLIST.name && AudioPlayerManager.activePlayingPlaylistId == playlistId) {
                        if (isShuffleEnabled()) {
                            setShuffledList(getShuffledList().filter { it.absolutePath != pathToRemove })
                        }
                        syncPreparedNextTrack()
                    }
                    Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
