package com.yuka.musicplayer.player

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.SystemClock
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortField
import com.yuka.musicplayer.model.SortOrder
import com.yuka.musicplayer.model.sortScannedItemsByField
import com.yuka.musicplayer.util.AlbumArtThumbnailCache
import com.yuka.musicplayer.util.MusicLibraryDatabaseHelper
import com.yuka.musicplayer.util.MusicScannerHelper
import com.yuka.musicplayer.util.PlaylistStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Stable
class LibraryStateHolder(
    initialMusicRootDirectory: File,
    initialIsMusicDirectoryConfigured: Boolean,
    initialMakeFolderAsPlaylist: Boolean,
    initialLibrarySortField: SortField,
    initialLibrarySortOrder: SortOrder
) {
    var musicRootDirectory by mutableStateOf(initialMusicRootDirectory)
    var isMusicDirectoryConfigured by mutableStateOf(initialIsMusicDirectoryConfigured)
    var isScanningLibrary by mutableStateOf(false)
    var forceFullRescanRequested by mutableStateOf(false)
    var scannedLibraryItems by mutableStateOf<List<ScannedMusicItem>>(emptyList())
    var allPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var folderPlaylists by mutableStateOf<List<Playlist>>(emptyList())
    var selectedPlaylistId by mutableStateOf<String?>(null)
    var makeFolderAsPlaylist by mutableStateOf(initialMakeFolderAsPlaylist)
    var librarySortField by mutableStateOf(initialLibrarySortField)
    var librarySortOrder by mutableStateOf(initialLibrarySortOrder)

    lateinit var triggerFullRescan: () -> Unit
    lateinit var refreshLibrary: () -> Unit
    lateinit var reloadPlaylists: () -> Unit

    val effectivePlaylists: List<Playlist> by androidx.compose.runtime.derivedStateOf {
        allPlaylists + folderPlaylists
    }

    val selectedPlaylist: Playlist? by androidx.compose.runtime.derivedStateOf {
        effectivePlaylists.firstOrNull { it.id == selectedPlaylistId } ?: effectivePlaylists.firstOrNull()
    }

    val playlistPaths: List<String> by androidx.compose.runtime.derivedStateOf {
        selectedPlaylist?.trackPaths ?: emptyList()
    }

    val playlistSet: Set<String> by androidx.compose.runtime.derivedStateOf {
        effectivePlaylists.flatMap { it.trackPaths }.toSet()
    }

    val scannedItemsMap: Map<String, ScannedMusicItem> by androidx.compose.runtime.derivedStateOf {
        scannedLibraryItems.associateBy { it.file.absolutePath }
    }

    val playableFiles: List<File> by androidx.compose.runtime.derivedStateOf {
        MusicScannerHelper.filterPlayableFiles(scannedLibraryItems)
    }

    val supportedFileMap: Map<String, File> by androidx.compose.runtime.derivedStateOf {
        scannedLibraryItems.asSequence()
            .filter { it.isSupported }
            .associate { it.file.absolutePath to it.file }
    }
}

@Composable
fun rememberLibraryStateHolder(
    context: Context,
    sharedPref: SharedPreferences,
    isStorageGranted: Boolean,
    isAllFilesAccessGranted: Boolean,
    onShowFolderPicker: () -> Unit = {}
): LibraryStateHolder {
    val coroutineScope = rememberCoroutineScope()

    val initialConfigured = remember {
        sharedPref.getBoolean("music_directory_configured", false) &&
            sharedPref.getString("music_root_directory", null)?.let { File(it).exists() && File(it).isDirectory } == true
    }

    val defaultMusicDir = remember {
        val savedPath = sharedPref.getString("music_root_directory", null)
        val rawDir = if (savedPath != null && File(savedPath).exists()) {
            File(savedPath)
        } else {
            val standardMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (standardMusic != null && standardMusic.exists()) standardMusic
            else File(Environment.getExternalStorageDirectory(), "Music")
        }
        MusicScannerHelper.canonicalizeFile(rawDir)
    }

    val initialSortField = remember {
        runCatching {
            SortField.valueOf(
                sharedPref.getString("library_sort_field", SortField.TITLE.name) ?: SortField.TITLE.name
            )
        }.getOrDefault(SortField.TITLE)
    }

    val initialSortOrder = remember {
        runCatching {
            SortOrder.valueOf(
                sharedPref.getString("library_sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name
            )
        }.getOrDefault(SortOrder.ASCENDING)
    }

    val initialMakeFolderAsPlaylist = remember {
        sharedPref.getBoolean("make_folder_as_playlist", false)
    }

    var libraryRefreshTrigger by remember { mutableIntStateOf(0) }
    var lastKnownDirectoryFingerprint by remember { mutableLongStateOf(0L) }

    val state = remember {
        LibraryStateHolder(
            initialMusicRootDirectory = defaultMusicDir,
            initialIsMusicDirectoryConfigured = initialConfigured,
            initialMakeFolderAsPlaylist = initialMakeFolderAsPlaylist,
            initialLibrarySortField = initialSortField,
            initialLibrarySortOrder = initialSortOrder
        )
    }

    state.refreshLibrary = remember(state, onShowFolderPicker) {
        {
            if (!state.isMusicDirectoryConfigured) {
                onShowFolderPicker()
            } else if (!state.isScanningLibrary) {
                state.isScanningLibrary = true
                state.forceFullRescanRequested = true
                libraryRefreshTrigger++
            }
        }
    }

    state.triggerFullRescan = remember(state) {
        {
            if (!state.isScanningLibrary) {
                state.isScanningLibrary = true
                state.forceFullRescanRequested = true
                libraryRefreshTrigger++
            }
        }
    }

    state.reloadPlaylists = remember(state, context) {
        {
            coroutineScope.launch(Dispatchers.IO) {
                val loaded = PlaylistStorageHelper.loadAllPlaylists(context)
                withContext(Dispatchers.Main) {
                    state.allPlaylists = loaded
                    if (loaded.isNotEmpty() && state.selectedPlaylistId == null) {
                        state.selectedPlaylistId = loaded.first().id
                    }
                }
            }
        }
    }

    // 1. Initial Playlists load
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!sharedPref.getBoolean("playlists_v2_migrated", false)) {
                PlaylistStorageHelper.performOneTimeLegacyReset(context, sharedPref)
            }
            val loaded = PlaylistStorageHelper.loadAllPlaylists(context)
            withContext(Dispatchers.Main) {
                state.allPlaylists = loaded
                if (loaded.isNotEmpty()) {
                    state.selectedPlaylistId = loaded.first().id
                }
            }
        }
    }

    // 2. Folder playlists derivation
    LaunchedEffect(state.makeFolderAsPlaylist, state.musicRootDirectory, state.scannedLibraryItems) {
        state.folderPlaylists = if (state.makeFolderAsPlaylist) {
            withContext(Dispatchers.Default) {
                MusicScannerHelper.buildFolderPlaylists(
                    rootDir = state.musicRootDirectory,
                    scannedItems = state.scannedLibraryItems
                )
            }
        } else {
            emptyList()
        }
    }

    // 3. Persist music root directory changes
    LaunchedEffect(state.musicRootDirectory, state.isMusicDirectoryConfigured) {
        if (state.isMusicDirectoryConfigured) {
            val rootPath = state.musicRootDirectory.absolutePath
            sharedPref.edit()
                .putString("music_root_directory", rootPath)
                .putBoolean("music_directory_configured", true)
                .apply()
        }
    }

    // 4. Persist sorting preferences & resort in-memory items
    LaunchedEffect(state.librarySortField, state.librarySortOrder) {
        sharedPref.edit()
            .putString("library_sort_field", state.librarySortField.name)
            .putString("library_sort_order", state.librarySortOrder.name)
            .apply()
        if (state.scannedLibraryItems.isNotEmpty()) {
            val resorted = withContext(Dispatchers.Default) {
                sortScannedItemsByField(state.scannedLibraryItems, state.librarySortField, state.librarySortOrder)
            }
            state.scannedLibraryItems = resorted
        }
    }

    // 5. Lifecycle ON_RESUME fingerprint observer
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.musicRootDirectory, state.isMusicDirectoryConfigured, isStorageGranted, isAllFilesAccessGranted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasAccess = isStorageGranted || isAllFilesAccessGranted
                if (state.isMusicDirectoryConfigured && hasAccess && state.musicRootDirectory.exists() && !state.isScanningLibrary) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val currentFp = MusicScannerHelper.computeDirectoryStructureFingerprint(state.musicRootDirectory)
                        if (lastKnownDirectoryFingerprint != 0L && currentFp != lastKnownDirectoryFingerprint) {
                            withContext(Dispatchers.Main) {
                                libraryRefreshTrigger++
                            }
                        }
                        lastKnownDirectoryFingerprint = currentFp
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 6. Two-stage Library Loader & Full Rescan Engine
    LaunchedEffect(state.musicRootDirectory, isStorageGranted, isAllFilesAccessGranted, state.isMusicDirectoryConfigured, libraryRefreshTrigger) {
        if (!state.isMusicDirectoryConfigured) {
            state.scannedLibraryItems = emptyList()
            state.isScanningLibrary = false
            return@LaunchedEffect
        }
        val effectiveStorageGranted = isStorageGranted || isAllFilesAccessGranted
        if (effectiveStorageGranted) {
            val isManualForceRescan = state.forceFullRescanRequested
            state.forceFullRescanRequested = false

            withContext(Dispatchers.IO) {
                val db = MusicLibraryDatabaseHelper.getInstance(context)

                if (isManualForceRescan) {
                    withContext(Dispatchers.Main) {
                        state.isScanningLibrary = true
                    }
                    val startTime = SystemClock.elapsedRealtime()
                    val freshItems = MusicScannerHelper.scanMusicDirectory(
                        rootDir = state.musicRootDirectory,
                        context = context,
                        isForceRescan = true
                    )
                    db.replaceAllTracksForRoot(state.musicRootDirectory, freshItems)
                    lastKnownDirectoryFingerprint = MusicScannerHelper.computeDirectoryStructureFingerprint(state.musicRootDirectory)
                    val elapsed = SystemClock.elapsedRealtime() - startTime
                    if (elapsed < 450L) {
                        delay(450L - elapsed)
                    }
                    val sortedFresh = sortScannedItemsByField(
                        freshItems,
                        state.librarySortField,
                        state.librarySortOrder
                    )
                    withContext(Dispatchers.Main) {
                        state.scannedLibraryItems = sortedFresh
                        state.isScanningLibrary = false
                        Toast.makeText(
                            context,
                            "Rescan complete • ${freshItems.size} tracks found in ${state.musicRootDirectory.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        state.isScanningLibrary = true
                    }
                    val cached = db.getCachedTracksForRoot(state.musicRootDirectory)
                    if (cached.isNotEmpty()) {
                        val sortedCached = sortScannedItemsByField(
                            cached,
                            state.librarySortField,
                            state.librarySortOrder
                        )
                        withContext(Dispatchers.Main) {
                            state.scannedLibraryItems = sortedCached
                        }
                    }

                    val scanResult = MusicScannerHelper.scanMusicDirectoryIncremental(
                        context = context,
                        rootDir = state.musicRootDirectory,
                        cachedItems = cached.takeIf { it.isNotEmpty() }
                    )
                    lastKnownDirectoryFingerprint = MusicScannerHelper.computeDirectoryStructureFingerprint(state.musicRootDirectory)
                    if (scanResult.hasChanges || cached.isEmpty()) {
                        val sortedFresh = sortScannedItemsByField(
                            scanResult.items,
                            state.librarySortField,
                            state.librarySortOrder
                        )
                        withContext(Dispatchers.Main) {
                            state.scannedLibraryItems = sortedFresh
                            state.isScanningLibrary = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            state.isScanningLibrary = false
                        }
                    }
                }
            }
        }
    }

    // 7. Thumbnail prewarming
    LaunchedEffect(state.scannedLibraryItems) {
        if (state.scannedLibraryItems.isNotEmpty()) {
            AlbumArtThumbnailCache.prewarmLibraryThumbnails(state.scannedLibraryItems)
        }
    }

    return state
}
