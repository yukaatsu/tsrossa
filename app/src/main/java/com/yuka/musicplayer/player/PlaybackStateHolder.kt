package com.yuka.musicplayer.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.audio.AudioForegroundService
import com.yuka.musicplayer.audio.AudioPlayerManager
import com.yuka.musicplayer.audio.DacVolumeState
import com.yuka.musicplayer.metadata.extractMetadata
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.player.queue.QueueHelper
import com.yuka.musicplayer.util.AlbumArtThumbnailCache
import com.yuka.musicplayer.util.MusicLibraryDatabaseHelper
import com.yuka.musicplayer.util.MusicScannerHelper
import com.yuka.musicplayer.util.checkStoragePermission
import com.yuka.musicplayer.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pure gatekeeper determining whether a track play/resume request is permitted.
 * tsrossa streams bit-perfect audio exclusively to an external USB DAC; when no USB DAC
 * is connected (isDacConnected == false), playback must be blocked and the user notified.
 */
internal fun canProceedWithDacPlayback(isDacConnected: Boolean): Boolean = isDacConnected

@Stable
class PlaybackStateHolder(
    initialTrack: TrackInfo?,
    initialIsPlaying: Boolean,
    initialIsShuffleEnabled: Boolean,
    initialRepeatMode: RepeatMode,
    initialPlaybackSourceStr: String,
    val playbackPositionHolder: PlaybackPositionHolder
) {
    var currentTrack by mutableStateOf<TrackInfo?>(initialTrack)
    var sequenceAnchorTrackFile by mutableStateOf<File?>(initialTrack?.file)
    var isPlaying by mutableStateOf(initialIsPlaying)
    var isNowPlayingExpanded by mutableStateOf(initialIsPlaying && initialTrack != null)
    var isShuffleEnabled by mutableStateOf(initialIsShuffleEnabled)
    var repeatMode by mutableStateOf(initialRepeatMode)
    var priorityQueue by mutableStateOf<List<File>>(AudioPlayerManager.priorityQueue)
    var playbackLibraryFiles by mutableStateOf<List<File>>(emptyList())
    var shuffledList by mutableStateOf<List<File>>(emptyList())
    var currentShuffleIndex by mutableIntStateOf(-1)
    val playbackHistory = mutableListOf<File>()
    var activePlayingPlaylistId by mutableStateOf<String?>(null)
    var currentPlaybackSourceStr by mutableStateOf(initialPlaybackSourceStr)
    var pausedByTransientLoss by mutableStateOf(false)
    var formatIncompatibleError by mutableStateOf<String?>(null)
    var showDacRequiredNoticeDialog by mutableStateOf(false)

    var onPlayTrackInternal: ((
        file: File,
        isAutoAdvance: Boolean,
        recordHistory: Boolean,
        source: PlaybackSource?,
        playlistId: String?,
        targetSource: PlaybackSource?,
        initialSeekSeconds: Double,
        fromPriorityQueue: Boolean
    ) -> Unit)? = null

    fun playTrack(
        file: File,
        isAutoAdvance: Boolean = false,
        recordHistory: Boolean = true,
        source: PlaybackSource? = null,
        playlistId: String? = null,
        targetSource: PlaybackSource? = null,
        initialSeekSeconds: Double = -1.0,
        fromPriorityQueue: Boolean = false
    ) {
        onPlayTrackInternal?.invoke(
            file,
            isAutoAdvance,
            recordHistory,
            source,
            playlistId,
            targetSource,
            initialSeekSeconds,
            fromPriorityQueue
        )
    }

    lateinit var playNext: (isAutoAdvance: Boolean) -> Unit
    lateinit var playPrev: () -> Unit
    lateinit var toggleShuffle: () -> Unit
    lateinit var cycleRepeat: () -> Unit
    lateinit var sharedOnTogglePlay: () -> Unit
    lateinit var handleTracksDeleted: (List<File>) -> Unit
    lateinit var syncPreparedNextTrack: () -> Unit
    lateinit var getActiveTrackList: (source: PlaybackSource) -> List<File>

    val isNowPlayingVisible: Boolean
        get() = isNowPlayingExpanded && currentTrack != null

    val currentPlaybackSource: PlaybackSource
        get() = runCatching { PlaybackSource.valueOf(currentPlaybackSourceStr) }.getOrDefault(PlaybackSource.LIBRARY)
}

@Composable
fun rememberPlaybackStateHolder(
    audioEngine: AudioEngine,
    libraryState: LibraryStateHolder,
    dacVolumeState: DacVolumeState,
    sharedPref: SharedPreferences,
    context: Context
): PlaybackStateHolder {
    val coroutineScope = rememberCoroutineScope()

    val initialShuffle = remember { sharedPref.getBoolean("shuffle_enabled", false) }
    val initialRepeat = remember {
        try {
            RepeatMode.valueOf(sharedPref.getString("repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name)
        } catch (_: Exception) {
            RepeatMode.OFF
        }
    }
    val initialTrack = AudioPlayerManager.currentTrack
    val initialIsPlaying = AudioPlayerManager.isPlaying || audioEngine.isPlaying()
    val initialSourceStr = PlaybackSource.LIBRARY.name

    val playbackPositionHolder = remember { PlaybackPositionHolder(0.0) }

    val state = remember {
        PlaybackStateHolder(
            initialTrack = initialTrack,
            initialIsPlaying = initialIsPlaying,
            initialIsShuffleEnabled = initialShuffle,
            initialRepeatMode = initialRepeat,
            initialPlaybackSourceStr = initialSourceStr,
            playbackPositionHolder = playbackPositionHolder
        )
    }

    LaunchedEffect(state.currentTrack, state.isPlaying) {
        while (state.isPlaying && state.currentTrack != null) {
            playbackPositionHolder.updateFromPolling(audioEngine.getPosition())
            delay(200)
        }
    }

    var playJob by remember { mutableStateOf<Job?>(null) }
    var playRequestId by remember { mutableLongStateOf(0L) }
    var metadataJob by remember { mutableStateOf<Job?>(null) }
    var prepareJob by remember { mutableStateOf<Job?>(null) }
    var lastNowPlayingExpandTimestampMs by remember { mutableLongStateOf(0L) }

    fun getActiveTrackList(source: PlaybackSource = state.currentPlaybackSource): List<File> {
        return when (source) {
            PlaybackSource.LIBRARY -> {
                if (state.playbackLibraryFiles.isNotEmpty()) {
                    state.playbackLibraryFiles
                } else {
                    val playable = libraryState.playableFiles
                    state.playbackLibraryFiles = playable
                    playable
                }
            }
            PlaybackSource.PLAYLIST -> {
                val livePlaylists = libraryState.effectivePlaylists
                val activeId = state.activePlayingPlaylistId
                    ?: AudioPlayerManager.activePlayingPlaylistId
                    ?: libraryState.selectedPlaylistId?.ifEmpty { null }
                val playingPlaylist = if (activeId != null) {
                    livePlaylists.firstOrNull { it.id == activeId }
                } else {
                    livePlaylists.firstOrNull()
                }
                QueueHelper.resolvePlaylistTracks(
                    playlistTrackPaths = playingPlaylist?.trackPaths,
                    fallbackTrackPaths = AudioPlayerManager.playlistPaths,
                    supportedFileMap = libraryState.supportedFileMap
                )
            }
        }
    }
    state.getActiveTrackList = ::getActiveTrackList

    fun ensureShuffleDeck(activeList: List<File>, currentFile: File?) {
        val (deck, idx) = QueueHelper.ensureShuffleDeck(
            activeList = activeList,
            currentFile = currentFile,
            currentShuffledList = state.shuffledList,
            currentShuffleIndex = state.currentShuffleIndex
        )
        state.shuffledList = deck
        state.currentShuffleIndex = idx
    }

    fun getNextTrackFile(isAutoAdvance: Boolean, consumeQueue: Boolean): Pair<File?, Boolean> {
        val currentSource = runCatching {
            PlaybackSource.valueOf(state.currentPlaybackSourceStr)
        }.getOrDefault(AudioPlayerManager.currentPlaybackSource)

        val result = QueueHelper.resolveNextTrack(
            isAutoAdvance = isAutoAdvance,
            consumeQueue = consumeQueue,
            priorityQueue = state.priorityQueue,
            currentTrack = state.currentTrack,
            repeatMode = state.repeatMode,
            activeList = getActiveTrackList(currentSource),
            isShuffleEnabled = state.isShuffleEnabled,
            shuffledList = state.shuffledList,
            currentShuffleIndex = state.currentShuffleIndex,
            sequenceAnchorFile = state.sequenceAnchorTrackFile
        )
        if (consumeQueue) {
            state.priorityQueue = result.updatedQueue
            AudioPlayerManager.priorityQueue = result.updatedQueue
            state.shuffledList = result.updatedShuffledList
            state.currentShuffleIndex = result.updatedShuffleIndex
        }
        return Pair(result.nextFile, result.fromPriorityQueue)
    }

    fun peekNextTrackFile(): File? = getNextTrackFile(isAutoAdvance = true, consumeQueue = false).first

    fun syncPreparedNextTrack() {
        if (!state.isPlaying || state.currentTrack == null) {
            prepareJob?.cancel()
            audioEngine.clearNextTrack()
            return
        }
        val nextFile = peekNextTrackFile()
        if (nextFile != null) {
            prepareJob?.cancel()
            prepareJob = coroutineScope.launch(Dispatchers.IO) {
                AlbumArtThumbnailCache.prepareTrackVisuals(nextFile.absolutePath)
                delay(2200L)
                audioEngine.prepareNextTrack(nextFile.absolutePath)
            }
        } else {
            prepareJob?.cancel()
            audioEngine.clearNextTrack()
        }
    }
    state.syncPreparedNextTrack = ::syncPreparedNextTrack

    fun blockPlaybackForDisconnectedDac(showNotice: Boolean, reason: String) {
        playJob?.cancel()
        metadataJob?.cancel()
        prepareJob?.cancel()
        audioEngine.pauseAudio()
        state.isPlaying = false
        AudioPlayerManager.isPlaying = false
        if (showNotice) {
            state.showDacRequiredNoticeDialog = true
        }
        AppLogger.w("PLAYBACK", "$reason (USB DAC is disconnected)")
    }

    fun playTrack(
        file: File,
        isAutoAdvance: Boolean = false,
        recordHistory: Boolean = true,
        source: PlaybackSource? = null,
        playlistId: String? = state.activePlayingPlaylistId ?: AudioPlayerManager.activePlayingPlaylistId ?: libraryState.selectedPlaylistId?.ifEmpty { null },
        targetSource: PlaybackSource? = null,
        initialSeekSeconds: Double = -1.0,
        fromPriorityQueue: Boolean = false
    ) {
        if (!canProceedWithDacPlayback(AudioPlayerManager.isDacConnected)) {
            blockPlaybackForDisconnectedDac(
                showNotice = !isAutoAdvance,
                reason = "playTrack blocked for ${file.name}"
            )
            return
        }
        val resolvedSource = targetSource ?: source ?: AudioPlayerManager.currentPlaybackSource
        val resolvedPlaylistId = playlistId ?: (if (resolvedSource == PlaybackSource.PLAYLIST) (state.activePlayingPlaylistId ?: AudioPlayerManager.activePlayingPlaylistId ?: libraryState.selectedPlaylistId?.ifEmpty { null }) else null)
        val sourceChanged = resolvedSource != AudioPlayerManager.currentPlaybackSource
        val playlistChanged = resolvedSource == PlaybackSource.PLAYLIST && resolvedPlaylistId != null && resolvedPlaylistId != state.activePlayingPlaylistId

        AudioPlayerManager.currentPlaybackSource = resolvedSource
        state.currentPlaybackSourceStr = resolvedSource.name

        if (resolvedSource == PlaybackSource.PLAYLIST) {
            if (resolvedPlaylistId != null) {
                state.activePlayingPlaylistId = resolvedPlaylistId
                AudioPlayerManager.activePlayingPlaylistId = resolvedPlaylistId
                val livePlaylists = libraryState.effectivePlaylists
                val playlistObj = livePlaylists.firstOrNull { it.id == resolvedPlaylistId }
                if (playlistObj != null) {
                    AudioPlayerManager.playlistPaths = playlistObj.trackPaths
                }
            }
        } else {
            state.activePlayingPlaylistId = null
            AudioPlayerManager.activePlayingPlaylistId = null
        }

        if (sourceChanged || playlistChanged) {
            state.shuffledList = emptyList()
            state.currentShuffleIndex = -1
            audioEngine.clearNextTrack()
        }

        if (resolvedSource == PlaybackSource.LIBRARY && (state.playbackLibraryFiles.isEmpty() || state.playbackLibraryFiles.none { it.absolutePath == file.absolutePath })) {
            state.playbackLibraryFiles = libraryState.playableFiles
        }

        if (!fromPriorityQueue || state.sequenceAnchorTrackFile == null) {
            state.sequenceAnchorTrackFile = file
        }

        if (recordHistory && state.currentTrack?.file != null && state.currentTrack?.file?.absolutePath != file.absolutePath) {
            state.playbackHistory.add(state.currentTrack!!.file)
            if (state.playbackHistory.size > 50) {
                state.playbackHistory.removeAt(0)
            }
        }

        val currentActiveList = getActiveTrackList(resolvedSource)
        if (state.isShuffleEnabled) {
            if (!fromPriorityQueue) {
                val trackIdx = QueueHelper.indexOfTrack(state.shuffledList, file)
                if (state.shuffledList.size == currentActiveList.size && trackIdx != -1) {
                    state.currentShuffleIndex = trackIdx
                } else {
                    val remaining = currentActiveList.filter { it.absolutePath != file.absolutePath }.shuffled()
                    state.shuffledList = listOf(file) + remaining
                    state.currentShuffleIndex = 0
                }
            }
        }

        val isExpandingNow = !isAutoAdvance && (!state.isNowPlayingExpanded || state.currentTrack == null)
        if (!isAutoAdvance) {
            if (isExpandingNow) {
                lastNowPlayingExpandTimestampMs = android.os.SystemClock.uptimeMillis()
            }
            state.isNowPlayingExpanded = true
        }
        val slideUpBarrierUntilMs = if (isExpandingNow) {
            lastNowPlayingExpandTimestampMs + 310L
        } else {
            0L
        }

        val cachedItem = libraryState.scannedItemsMap[file.absolutePath]
        val knownLastMod = cachedItem?.dateModified ?: 0L
        val cachedDominant = AlbumArtThumbnailCache.getCachedDominantColorFromRam(file.absolutePath, knownLastMod)
        val cachedThumbBitmap = AlbumArtThumbnailCache.get(file.absolutePath)
        if (cachedThumbBitmap != null && AlbumArtThumbnailCache.getBlurredBackdropImageBitmap(file.absolutePath) == null) {
            runCatching {
                val instantBlur = com.yuka.musicplayer.util.WallpaperBlurHelper.createBlurredArtworkBackdrop(
                    cachedThumbBitmap
                )
                AlbumArtThumbnailCache.putBlurredBackdrop(file.absolutePath, instantBlur)
            }
        }
        val cachedHeroCover = AlbumArtThumbnailCache.getHeroCoverBitmap(file.absolutePath)
        val initialTrackObj = TrackInfo(
            file = file,
            title = cachedItem?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            artist = cachedItem?.artist?.takeIf { it.isNotBlank() } ?: "Loading...",
            album = cachedItem?.album ?: "",
            year = if (cachedItem != null && cachedItem.year > 0) cachedItem.year.toString() else "",
            durationSeconds = if (cachedItem != null && cachedItem.durationMs > 0L) cachedItem.durationMs / 1000.0 else 0.0,
            coverArt = cachedHeroCover,
            dominantColor = cachedDominant ?: state.currentTrack?.dominantColor ?: Color(0xFF00FF00)
        )
        AppLogger.i("PLAYBACK", "playTrack: ${file.name} (source=$resolvedSource, auto=$isAutoAdvance)")
        state.isPlaying = true
        AudioPlayerManager.isPlaying = true
        state.currentTrack = initialTrackObj
        AudioPlayerManager.currentTrack = initialTrackObj
        playbackPositionHolder.forceSet(if (initialSeekSeconds > 0.0) initialSeekSeconds else 0.0)

        val currentPlayId = ++playRequestId
        playJob?.cancel()
        playJob = coroutineScope.launch(Dispatchers.IO) {
            var result = audioEngine.playAudio(file.absolutePath)

            if (result == -3) {
                AppLogger.w(
                    "USB",
                    "playAudio returned -3 (USB negotiation failed). Triggering self-healing DAC FD reopen..."
                )
                val reconnected = AudioPlayerManager.usbAudioController.forceReconnectActiveDevice()
                delay(if (reconnected) 120L else 200L)
                result = audioEngine.playAudio(file.absolutePath)
            }

            when {
                result == 0 -> {
                    if (currentPlayId != playRequestId) return@launch
                    if (initialSeekSeconds > 0.0) {
                        audioEngine.seekTo(initialSeekSeconds)
                    }
                    AppLogger.i("AUDIO", "playAudio SUCCESS: ${file.name}")
                    val hwActive = audioEngine.isHardwareVolumeActive()
                    val hwSupported = audioEngine.isHardwareVolumeSupported()
                    val remPlay = slideUpBarrierUntilMs - android.os.SystemClock.uptimeMillis()
                    if (remPlay > 0L) {
                        delay(remPlay)
                    }
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        state.isPlaying = true
                        AudioPlayerManager.isPlaying = true
                        if (initialSeekSeconds > 0.0) {
                            playbackPositionHolder.forceSet(initialSeekSeconds)
                        }
                        state.formatIncompatibleError = null
                        dacVolumeState.isHardwareVolumeActive = hwActive
                        dacVolumeState.isHardwareVolumeSupported = hwSupported
                        syncPreparedNextTrack()
                    }
                }
                result == -2 -> {
                    if (currentPlayId != playRequestId) return@launch
                    AppLogger.w("AUDIO", "playAudio FORMAT REFUSED: ${file.name} (${state.formatIncompatibleError})")
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        state.isPlaying = false
                        AudioPlayerManager.isPlaying = false
                        Toast.makeText(context, state.formatIncompatibleError ?: "Format not supported by DAC", Toast.LENGTH_SHORT).show()
                        if (isAutoAdvance) state.playNext(true)
                    }
                    return@launch
                }
                result == -1 -> {
                    if (currentPlayId != playRequestId) return@launch
                    AppLogger.e("AUDIO", "playAudio FILE ERROR: ${file.name}")
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        state.isPlaying = false
                        AudioPlayerManager.isPlaying = false
                        val lastUsbDiag = audioEngine.getLastUsbDiagnostic()
                        val isAndroid11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        val isAllFilesMissing = isAndroid11Plus && !com.yuka.musicplayer.util.isAllFilesAccessGranted(context)
                        val isPermissionIssue = lastUsbDiag.contains("Permission denied", ignoreCase = true) || !checkStoragePermission(context)
                        val errorMsg = if (isAllFilesMissing && (!file.canRead() || isPermissionIssue)) {
                            "Cannot read file. Please grant 'All Files Access' in Android Settings to bypass Samsung .nomedia restriction."
                        } else if (isPermissionIssue) {
                            "Permission denied! Enable 'Music & Audio Access' for tsrossa in Android Settings."
                        } else {
                            "Failed to open audio file. Ensure it is a valid FLAC or WAV file."
                        }
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                else -> {
                    if (currentPlayId != playRequestId) return@launch
                    AppLogger.e("AUDIO", "playAudio FAILED code=$result: ${file.name}")
                    withContext(Dispatchers.Main) {
                        if (currentPlayId != playRequestId) return@withContext
                        state.isPlaying = false
                        AudioPlayerManager.isPlaying = false
                        val msg = when (result) {
                            -3 -> "USB negotiation failed"
                            -4 -> "USB DAC wedged. Unplug and reconnect the DAC."
                            else -> "Playback failed (error code $result)"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            }
        }

        metadataJob?.cancel()
        metadataJob = coroutineScope.launch(Dispatchers.IO) {
            val metadata = extractMetadata(file)
            AppLogger.i("AUDIO", "Decoded ${file.extension.uppercase()} stream: ${file.name} (${metadata.sampleRateHz} Hz, ${metadata.bitDepth}-bit, ${metadata.bitrateKbps} kbps)")
            val remMeta = slideUpBarrierUntilMs - android.os.SystemClock.uptimeMillis()
            if (remMeta > 0L) {
                delay(remMeta)
            }
            withContext(Dispatchers.Main) {
                if (currentPlayId != playRequestId) return@withContext
                state.currentTrack = metadata
                AudioPlayerManager.currentTrack = metadata
            }
            // Pre-scale notification bitmaps AFTER UI track state is committed so AlbumArtHero updates immediately
            com.yuka.musicplayer.audio.MediaNotificationHelper.getSafeCoverArt(metadata.coverArt, file.absolutePath, maxDimension = 1024)
            com.yuka.musicplayer.audio.MediaNotificationHelper.getSafeCoverArt(metadata.coverArt, file.absolutePath, maxDimension = 512)
        }
    }
    state.onPlayTrackInternal = ::playTrack

    fun playNext(isAutoAdvance: Boolean) {
        if (!canProceedWithDacPlayback(AudioPlayerManager.isDacConnected)) {
            blockPlaybackForDisconnectedDac(
                showNotice = !isAutoAdvance,
                reason = "playNext blocked"
            )
            return
        }
        val (nextFile, fromPriorityQueue) = getNextTrackFile(isAutoAdvance, consumeQueue = true)
        if (nextFile != null) {
            playTrack(
                file = nextFile,
                isAutoAdvance = isAutoAdvance,
                recordHistory = true,
                fromPriorityQueue = fromPriorityQueue
            )
        } else if (isAutoAdvance) {
            audioEngine.stopAudio()
            state.isPlaying = false
        }
    }
    state.playNext = ::playNext

    fun playPrev() {
        if (!canProceedWithDacPlayback(AudioPlayerManager.isDacConnected)) {
            blockPlaybackForDisconnectedDac(
                showNotice = true,
                reason = "playPrev blocked"
            )
            return
        }
        val currentPos = if (audioEngine.isPlaying()) audioEngine.getPosition() else playbackPositionHolder.positionSeconds
        if (currentPos > 3.0) {
            playbackPositionHolder.forceSet(0.0)
            audioEngine.seekTo(0.0)
            return
        }
        val currentSource = AudioPlayerManager.currentPlaybackSource
        val currentActiveList = getActiveTrackList(currentSource)
        val result = QueueHelper.resolvePrevTrack(
            playbackHistory = state.playbackHistory,
            activeList = currentActiveList,
            currentTrackFile = state.currentTrack?.file,
            isShuffleEnabled = state.isShuffleEnabled,
            shuffledList = state.shuffledList,
            currentShuffleIndex = state.currentShuffleIndex,
            repeatMode = state.repeatMode
        )
        state.currentShuffleIndex = result.updatedShuffleIndex
        if (result.prevFile != null) {
            playTrack(result.prevFile, isAutoAdvance = false, recordHistory = false, source = currentSource)
        } else {
            playbackPositionHolder.forceSet(0.0)
            audioEngine.seekTo(0.0)
        }
    }
    state.playPrev = ::playPrev

    val toggleShuffle = {
        val newState = !state.isShuffleEnabled
        state.isShuffleEnabled = newState
        coroutineScope.launch(Dispatchers.IO) {
            audioEngine.clearNextTrack()
        }
        if (newState) {
            val currentActiveList = getActiveTrackList(state.currentPlaybackSource)
            ensureShuffleDeck(currentActiveList, state.currentTrack?.file)
        }
        syncPreparedNextTrack()
    }
    state.toggleShuffle = toggleShuffle

    val cycleRepeat = {
        state.repeatMode = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        coroutineScope.launch(Dispatchers.IO) {
            audioEngine.clearNextTrack()
        }
        syncPreparedNextTrack()
    }
    state.cycleRepeat = cycleRepeat

    val sharedOnTogglePlay: () -> Unit = {
        if (state.isPlaying) {
            audioEngine.pauseAudio()
            playbackPositionHolder.forceSet(audioEngine.getPosition())
            state.isPlaying = false
            state.pausedByTransientLoss = false
        } else if (!canProceedWithDacPlayback(AudioPlayerManager.isDacConnected)) {
            blockPlaybackForDisconnectedDac(
                showNotice = true,
                reason = "Toggle play blocked"
            )
        } else {
            val dur = state.currentTrack?.durationSeconds ?: 0.0
            val savedResumePos = if (playbackPositionHolder.positionSeconds > 0.5 && (dur <= 0.0 || playbackPositionHolder.positionSeconds < dur - 1.0)) {
                playbackPositionHolder.positionSeconds
            } else {
                -1.0
            }
            coroutineScope.launch(Dispatchers.IO) {
                val resumed = audioEngine.resumeAudio()
                withContext(Dispatchers.Main) {
                    if (resumed) {
                        state.isPlaying = true
                    } else {
                        state.currentTrack?.file?.let { file ->
                            playTrack(
                                file = file,
                                isAutoAdvance = false,
                                source = state.currentPlaybackSource,
                                initialSeekSeconds = savedResumePos
                            )
                        }
                    }
                }
            }
        }
    }
    state.sharedOnTogglePlay = sharedOnTogglePlay

    val handleTracksDeleted: (List<File>) -> Unit = { deletedFiles ->
        if (deletedFiles.isNotEmpty()) {
            val deletedPaths = deletedFiles.map { it.absolutePath }.toSet()

            val activeTrackPath = state.currentTrack?.file?.absolutePath
            if (activeTrackPath != null && activeTrackPath in deletedPaths) {
                val remainingPlayable = libraryState.scannedLibraryItems
                    .filterNot { it.file.absolutePath in deletedPaths }
                    .filter { it.isSupported }
                if (remainingPlayable.isEmpty()) {
                    audioEngine.stopAudio()
                    state.isPlaying = false
                    state.currentTrack = null
                    playbackPositionHolder.forceSet(0.0)
                } else {
                    state.playNext(false)
                }
            }

            state.priorityQueue = state.priorityQueue.filterNot { it.absolutePath in deletedPaths }
            AudioPlayerManager.priorityQueue = state.priorityQueue
            state.shuffledList = state.shuffledList.filterNot { it.absolutePath in deletedPaths }
            state.playbackHistory.removeAll { it.absolutePath in deletedPaths }
            state.playbackLibraryFiles = state.playbackLibraryFiles.filterNot { it.absolutePath in deletedPaths }

            libraryState.scannedLibraryItems = libraryState.scannedLibraryItems.filterNot { it.file.absolutePath in deletedPaths }

            coroutineScope.launch(Dispatchers.IO) {
                val db = MusicLibraryDatabaseHelper.getInstance(context)
                db.saveTracksForRoot(libraryState.musicRootDirectory, emptyList(), deletedPaths)
            }

            Toast.makeText(
                context,
                "${deletedFiles.size} track${if (deletedFiles.size == 1) "" else "s"} deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    state.handleTracksDeleted = handleTracksDeleted

    // Consolidated currentTrack & isPlaying synchronization (deferred past Now Playing slide-up window)
    LaunchedEffect(state.currentTrack, state.isPlaying) {
        if (state.currentTrack == null) {
            state.isNowPlayingExpanded = false
        }
        AudioPlayerManager.currentTrack = state.currentTrack
        AudioPlayerManager.isPlaying = state.isPlaying

        val elapsedSinceExpand = android.os.SystemClock.uptimeMillis() - lastNowPlayingExpandTimestampMs
        if (elapsedSinceExpand in 0L..300L) {
            delay(310L - elapsedSinceExpand)
        }

        AudioPlayerManager.notifyStateChanged()
        val intent = Intent(context, AudioForegroundService::class.java).apply {
            action = if (state.isPlaying) AudioForegroundService.ACTION_PLAY else AudioForegroundService.ACTION_PAUSE
        }
        try {
            if (state.isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.w("PlaybackStateHolder", "Service intent error: ${e.message}")
        }
    }

    LaunchedEffect(state.isShuffleEnabled) {
        sharedPref.edit().putBoolean("shuffle_enabled", state.isShuffleEnabled).apply()
    }
    LaunchedEffect(state.repeatMode) {
        sharedPref.edit().putString("repeat_mode", state.repeatMode.name).apply()
    }
    LaunchedEffect(state.currentPlaybackSourceStr) {
        AudioPlayerManager.currentPlaybackSource = state.currentPlaybackSource
    }
    LaunchedEffect(state.activePlayingPlaylistId) {
        AudioPlayerManager.activePlayingPlaylistId = state.activePlayingPlaylistId
    }

    // AudioPlayerManager & AudioEngine Callbacks
    val currentPlayNext by rememberUpdatedState(::playNext)
    DisposableEffect(Unit) {
        AudioPlayerManager.onPlayNext = {
            coroutineScope.launch(Dispatchers.Main) {
                playNext(false)
            }
        }
        AudioPlayerManager.onPlayPrev = {
            coroutineScope.launch(Dispatchers.Main) {
                playPrev()
            }
        }
        AudioPlayerManager.onSeekTo = { targetSec ->
            coroutineScope.launch(Dispatchers.Main) {
                audioEngine.seekTo(targetSec)
                playbackPositionHolder.forceSet(targetSec)
            }
        }
        AudioPlayerManager.onTogglePlay = {
            coroutineScope.launch(Dispatchers.Main) {
                sharedOnTogglePlay()
            }
        }
        audioEngine.onTrackFinishedCallback = {
            android.util.Log.i("PlaybackStateHolder", "audioEngine.onTrackFinishedCallback received -> playNext(true)")
            coroutineScope.launch(Dispatchers.Main) {
                currentPlayNext(true)
            }
        }
        audioEngine.onTrackTransitionCallback = { path ->
            coroutineScope.launch(Dispatchers.Main) {
                audioEngine.cleanGarbage()
                val nextFile = File(path)

                val activeTracks = getActiveTrackList(AudioPlayerManager.currentPlaybackSource)
                val matchesPath = { f: File -> f.absolutePath == path || f.canonicalPath == nextFile.canonicalPath }
                if (state.priorityQueue.none(matchesPath) && activeTracks.none(matchesPath)) {
                    android.util.Log.w("PlaybackStateHolder", "Track transition leaked out of source ($path). Redirecting.")
                    currentPlayNext(true)
                    return@launch
                }

                if (state.currentTrack?.file != null && state.currentTrack?.file?.absolutePath != nextFile.absolutePath) {
                    state.playbackHistory.add(state.currentTrack!!.file)
                    if (state.playbackHistory.size > 50) {
                        state.playbackHistory.removeAt(0)
                    }
                }

                val isFromQueue = state.priorityQueue.isNotEmpty() && matchesPath(state.priorityQueue.first())
                if (isFromQueue) {
                    state.priorityQueue = state.priorityQueue.drop(1)
                    AudioPlayerManager.priorityQueue = state.priorityQueue
                } else {
                    state.sequenceAnchorTrackFile = nextFile
                    if (state.isShuffleEnabled && state.shuffledList.isNotEmpty()) {
                        val idx = QueueHelper.indexOfTrack(state.shuffledList, nextFile)
                        val effectiveIdx = if (idx != -1) idx else state.shuffledList.indexOfFirst { matchesPath(it) }
                        if (effectiveIdx != -1) {
                            state.currentShuffleIndex = effectiveIdx
                        }
                    }
                }

                val cachedItem = libraryState.scannedItemsMap[nextFile.absolutePath]
                val cachedDominant = AlbumArtThumbnailCache.getCachedDominantColorFromRam(
                    nextFile.absolutePath,
                    cachedItem?.dateModified ?: 0L
                )
                val cachedThumbBitmap = AlbumArtThumbnailCache.get(nextFile.absolutePath)
                if (cachedThumbBitmap != null && AlbumArtThumbnailCache.getBlurredBackdropImageBitmap(nextFile.absolutePath) == null) {
                    runCatching {
                        val instantBlur = com.yuka.musicplayer.util.WallpaperBlurHelper.createBlurredArtworkBackdrop(
                            cachedThumbBitmap
                        )
                        AlbumArtThumbnailCache.putBlurredBackdrop(nextFile.absolutePath, instantBlur)
                    }
                }
                val cachedHeroCover = AlbumArtThumbnailCache.getHeroCoverBitmap(nextFile.absolutePath)
                val initialTrackObj = TrackInfo(
                    file = nextFile,
                    title = cachedItem?.title?.takeIf { it.isNotBlank() } ?: nextFile.nameWithoutExtension,
                    artist = cachedItem?.artist?.takeIf { it.isNotBlank() } ?: "Loading...",
                    album = cachedItem?.album ?: "",
                    year = if (cachedItem != null && cachedItem.year > 0) cachedItem.year.toString() else "",
                    durationSeconds = if (cachedItem != null && cachedItem.durationMs > 0L) cachedItem.durationMs / 1000.0 else 0.0,
                    coverArt = cachedHeroCover,
                    dominantColor = cachedDominant ?: state.currentTrack?.dominantColor ?: Color(0xFF00FF00)
                )
                state.isPlaying = true
                AudioPlayerManager.isPlaying = true
                state.currentTrack = initialTrackObj
                AudioPlayerManager.currentTrack = initialTrackObj
                AudioPlayerManager.notifyStateChanged()
                playbackPositionHolder.forceSet(0.0)

                metadataJob?.cancel()
                metadataJob = coroutineScope.launch(Dispatchers.IO) {
                    val nextMetadata = extractMetadata(nextFile)
                    withContext(Dispatchers.Main) {
                        state.currentTrack = nextMetadata
                        AudioPlayerManager.currentTrack = nextMetadata
                        AudioPlayerManager.notifyStateChanged()
                        playbackPositionHolder.forceSet(audioEngine.getPosition())
                        syncPreparedNextTrack()
                    }
                }
            }
        }
        audioEngine.onUsbStallFaultCallback = {
            state.isPlaying = false
            audioEngine.pauseAudio()
            coroutineScope.launch(Dispatchers.IO) {
                AudioPlayerManager.usbAudioController.forceReconnectActiveDevice()
            }
        }
        audioEngine.onDeviceForceDisconnectedCallback = {
            AudioPlayerManager.handleDacDetached()
        }
        audioEngine.onFormatIncompatibleCallback = { filename, _, _, reason ->
            coroutineScope.launch(Dispatchers.Main) {
                state.formatIncompatibleError = "$filename: $reason"
            }
        }

        onDispose {
            if (!AudioPlayerManager.isPlaying) {
                AudioPlayerManager.onPlayNext = null
                AudioPlayerManager.onPlayPrev = null
                AudioPlayerManager.onSeekTo = null
                AudioPlayerManager.onTogglePlay = null
                audioEngine.onTrackFinishedCallback = null
                audioEngine.onTrackTransitionCallback = null
                audioEngine.onUsbStallFaultCallback = null
                audioEngine.onDeviceForceDisconnectedCallback = null
                audioEngine.onFormatIncompatibleCallback = null
            }
        }
    }

    return state
}
