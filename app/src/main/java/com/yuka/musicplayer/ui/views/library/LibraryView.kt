package com.yuka.musicplayer.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.model.FolderBrowserSlice
import com.yuka.musicplayer.model.FolderSortField
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.LibraryTabMode
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortField
import com.yuka.musicplayer.model.SortOrder
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.RetroButton
import com.yuka.musicplayer.ui.dialogs.FilterSelectionModalSheet
import com.yuka.musicplayer.ui.dialogs.FolderSortModalSheet
import com.yuka.musicplayer.ui.dialogs.LibrarySortModalSheet
import com.yuka.musicplayer.util.FuzzySearchHelper
import com.yuka.musicplayer.util.MusicScannerHelper
import java.io.File

/**
 * Material 3 Glassmorphic Library Screen:
 * - Search bar with Fuzzy Search (`FuzzySearchHelper`), folder picker & refresh actions.
 * - Multi-selection mode (Select All, Bulk to Playlist, Bulk Delete with warning).
 * - Sort Modal Sheet supporting 11 SortField criteria with Ascending/Descending order.
 * - Shuffle button to shuffle play library tracks.
 * - Category Filter chips (Artist, Genre, Album, Year, Format).
 * - Fast Scrollbar with A–Z bubble indicator.
 * - Flat track rows with real artwork thumbnails and 3-dot context menu.
 */
@Composable
fun LibraryView(
    musicRootDirectory: File,
    scannedItems: List<ScannedMusicItem>,
    isScanning: Boolean,
    currentTrack: TrackInfo?,
    playlistSet: Set<String>,
    searchQuery: String,
    sortField: SortField = SortField.TITLE,
    sortOrder: SortOrder = SortOrder.ASCENDING,
    sortMode: LibrarySortMode = LibrarySortMode.NAME_ASC,
    isMusicDirectoryConfigured: Boolean = true,
    isAllFilesAccessGranted: Boolean = true,
    onRequestAllFilesAccess: () -> Unit = {},
    onCycleSort: () -> Unit = {},
    onSelectSort: ((LibrarySortMode) -> Unit)? = null,
    onSelectSortField: ((SortField, SortOrder) -> Unit)? = null,
    onShufflePlay: () -> Unit = {},
    onRefresh: () -> Unit,
    onChangeDirectory: () -> Unit,
    onSearchChange: (String) -> Unit,
    onTrackSelected: (ScannedMusicItem) -> Unit,
    onTrackLongPressed: (File) -> Unit,
    onBulkAddToPlaylist: (List<File>) -> Unit = {},
    onBulkDelete: (List<File>) -> Unit = {},
    onLocalModalOpenChanged: (Boolean) -> Unit = {},
    initialSelectedFile: File? = null,
    onConsumedInitialSelectedFile: () -> Unit = {},
    isNowPlayingVisible: Boolean = false,
    onPlayFolderTracks: ((List<File>, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    val folderLazyListState = rememberLazyListState()

    // Library Tab Mode State (All Songs vs Folders)
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTabMode.ALL_SONGS) }
    var currentFolderDirectory by remember(musicRootDirectory) { mutableStateOf(musicRootDirectory) }
    var folderSortField by rememberSaveable { mutableStateOf(FolderSortField.NAME) }
    var folderSortOrder by rememberSaveable { mutableStateOf(SortOrder.ASCENDING) }
    var showFolderSortModal by remember { mutableStateOf(false) }

    // Reset Folder tab scroll position cleanly to top when navigating between directories
    LaunchedEffect(currentFolderDirectory) {
        if (folderLazyListState.firstVisibleItemIndex > 0 || folderLazyListState.firstVisibleItemScrollOffset > 0) {
            folderLazyListState.scrollToItem(0)
        }
    }

    // Multi-Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFilePaths by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(initialSelectedFile) {
        if (initialSelectedFile != null) {
            isSelectionMode = true
            selectedFilePaths = selectedFilePaths + initialSelectedFile.absolutePath
            onConsumedInitialSelectedFile()
        }
    }

    // Dialog & Filter States
    var showSortModal by remember { mutableStateOf(false) }
    var activeFilterCategory by remember { mutableStateOf<String?>(null) } // "Artist", "Genre", "Album", "Year", "Format"
    var showFilterChips by rememberSaveable { mutableStateOf(false) }
    var filterArtist by remember { mutableStateOf<String?>(null) }
    var filterGenre by remember { mutableStateOf<String?>(null) }
    var filterAlbum by remember { mutableStateOf<String?>(null) }
    var filterYear by remember { mutableStateOf<String?>(null) }
    var filterFormat by remember { mutableStateOf<String?>(null) }

    // Fuzzy Search & Category Filtering
    val filteredItems = remember(
        scannedItems,
        searchQuery,
        filterArtist,
        filterGenre,
        filterAlbum,
        filterYear,
        filterFormat
    ) {
        scannedItems.filter { item ->
            if (filterArtist != null && (item.artist.ifBlank { "Unknown Artist" }) != filterArtist) return@filter false
            if (filterGenre != null && (item.genre.ifBlank { "Unknown Genre" }) != filterGenre) return@filter false
            if (filterAlbum != null && (item.album.ifBlank { "Unknown Album" }) != filterAlbum) return@filter false
            if (filterYear != null && ((if (item.year > 0) item.year.toString() else "Unknown Year") != filterYear)) return@filter false
            if (filterFormat != null && item.formatLabel != filterFormat) return@filter false

            if (searchQuery.isEmpty()) {
                true
            } else {
                FuzzySearchHelper.matchesAny(
                    searchQuery,
                    item.title,
                    item.artist,
                    item.album,
                    item.albumArtist,
                    item.genre,
                    item.composer,
                    item.file.name,
                    item.breadcrumb,
                    item.formatLabel
                )
            }
        }
    }

    // Fast Scroller letter provider callback for All Songs
    val letterProvider: (Int) -> String = remember(filteredItems, sortField) {
        { targetIndex ->
            if (targetIndex in filteredItems.indices) {
                val item = filteredItems[targetIndex]
                val primaryText = when (sortField) {
                    SortField.ARTIST -> item.displayArtist.ifBlank { item.displayTitle }
                    SortField.ALBUM -> item.album.ifBlank { item.displayTitle }
                    SortField.ALBUM_ARTIST -> item.albumArtist.ifBlank { item.displayArtist.ifBlank { item.displayTitle } }
                    SortField.GENRE -> item.genre.ifBlank { item.displayTitle }
                    SortField.COMPOSER -> item.composer.ifBlank { item.displayTitle }
                    else -> item.displayTitle.ifBlank { item.title }
                }
                val firstAlpha = primaryText.trimStart().firstOrNull { it.isLetter() }?.uppercaseChar()
                    ?: item.displayTitle.firstOrNull { it.isLetter() }?.uppercaseChar()
                if (firstAlpha != null && firstAlpha in 'A'..'Z') {
                    firstAlpha.toString()
                } else if (firstAlpha != null) {
                    firstAlpha.toString()
                } else {
                    "#"
                }
            } else {
                "#"
            }
        }
    }

    // Folder calculations
    val isInsideSubfolder = remember(musicRootDirectory, currentFolderDirectory, selectedTab) {
        if (selectedTab != LibraryTabMode.FOLDERS) false
        else {
            val rootPath = MusicScannerHelper.normalizeFolderPath(musicRootDirectory.path)
            val curPath = MusicScannerHelper.normalizeFolderPath(currentFolderDirectory.path)
            val rootPrefix = if (rootPath == "/") "/" else "$rootPath/"
            curPath != rootPath && curPath.startsWith(rootPrefix)
        }
    }

    val breadcrumbs = remember(musicRootDirectory, currentFolderDirectory, selectedTab) {
        if (selectedTab != LibraryTabMode.FOLDERS) emptyList()
        else {
            val rootPath = MusicScannerHelper.normalizeFolderPath(musicRootDirectory.path)
            val curPath = MusicScannerHelper.normalizeFolderPath(currentFolderDirectory.path)
            val rootPrefix = if (rootPath == "/") "/" else "$rootPath/"
            val rootFile = File(rootPath)
            if (curPath == rootPath || !curPath.startsWith(rootPrefix)) {
                listOf(rootFile)
            } else {
                val relSegments = curPath.substring(rootPrefix.length).split('/').filter { it.isNotEmpty() }
                val list = ArrayList<File>(relSegments.size + 1)
                list.add(rootFile)
                var runningPath = rootPath
                for (seg in relSegments) {
                    runningPath = if (runningPath == "/") "/$seg" else "$runningPath/$seg"
                    list.add(File(runningPath))
                }
                list
            }
        }
    }

    val rawFolderSlice by produceState(
        initialValue = FolderBrowserSlice(
            currentDirectory = currentFolderDirectory,
            isRoot = true,
            parentDirectory = null,
            subfolders = emptyList(),
            directTracks = emptyList(),
            allRecursivePlayableFiles = emptyList(),
            totalDirectMusicCount = 0,
            totalRecursiveMusicCount = 0
        ),
        key1 = musicRootDirectory,
        key2 = currentFolderDirectory,
        key3 = scannedItems
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            MusicScannerHelper.buildFolderBrowserSliceUnordered(
                rootDir = musicRootDirectory,
                currentDir = currentFolderDirectory,
                scannedItems = scannedItems
            )
        }
    }

    val sortedSubfolders = remember(rawFolderSlice.subfolders, folderSortField, folderSortOrder) {
        MusicScannerHelper.sortFolderNodes(
            nodes = rawFolderSlice.subfolders,
            folderSortField = folderSortField,
            folderSortOrder = folderSortOrder
        )
    }

    val currentFolderPlayableTracks = rawFolderSlice.allRecursivePlayableFiles

    val searchFilteredSubfolders = remember(sortedSubfolders, searchQuery) {
        if (searchQuery.isBlank()) sortedSubfolders
        else sortedSubfolders.filter { node ->
            node.name.contains(searchQuery, ignoreCase = true)
        }
    }

    val searchFilteredDirectTracks = remember(rawFolderSlice.directTracks, searchQuery) {
        if (searchQuery.isBlank()) rawFolderSlice.directTracks
        else rawFolderSlice.directTracks.filter { item ->
            FuzzySearchHelper.matchesAny(
                searchQuery,
                item.title,
                item.artist,
                item.album,
                item.file.name,
                item.formatLabel
            )
        }
    }

    var isSearchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isSearchFocused) {
        if (!isSearchFocused) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            lazyListState.isScrollInProgress || folderLazyListState.isScrollInProgress
        }.collect { scrolling ->
            if (scrolling && isSearchFocused) {
                focusManager.clearFocus()
            }
        }
    }

    // Back handlers
    BackHandler(enabled = !isNowPlayingVisible && isSelectionMode) {
        isSelectionMode = false
        selectedFilePaths = emptySet()
    }

    BackHandler(enabled = !isNowPlayingVisible && !isSelectionMode && searchQuery.isNotEmpty()) {
        onSearchChange("")
        focusManager.clearFocus()
    }

    BackHandler(enabled = !isNowPlayingVisible && !isSelectionMode && searchQuery.isEmpty() && selectedTab == LibraryTabMode.FOLDERS && isInsideSubfolder) {
        val rootPath = MusicScannerHelper.normalizeFolderPath(musicRootDirectory.path)
        val curPath = MusicScannerHelper.normalizeFolderPath(currentFolderDirectory.path)
        val rootPrefix = if (rootPath == "/") "/" else "$rootPath/"
        val parent = File(curPath).parentFile
        val parentPath = parent?.path?.let { MusicScannerHelper.normalizeFolderPath(it) } ?: ""
        if (parent != null && (parentPath == rootPath || parentPath.startsWith(rootPrefix))) {
            currentFolderDirectory = parent
        } else {
            currentFolderDirectory = musicRootDirectory
        }
    }

    val allSelected = selectedFilePaths.size == filteredItems.size && filteredItems.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // 1. Top Header: Search Bar OR Selection Action Bar
        LibraryHeaderAndSearchBar(
            isSelectionMode = isSelectionMode,
            selectedCount = selectedFilePaths.size,
            allSelected = allSelected,
            onCloseSelection = {
                isSelectionMode = false
                selectedFilePaths = emptySet()
            },
            onToggleSelectAll = {
                if (allSelected) {
                    selectedFilePaths = emptySet()
                } else {
                    selectedFilePaths = filteredItems.map { it.file.absolutePath }.toSet()
                }
            },
            onBulkAddToPlaylist = {
                val selectedFiles = filteredItems
                    .filter { it.file.absolutePath in selectedFilePaths }
                    .map { it.file }
                onBulkAddToPlaylist(selectedFiles)
            },
            onBulkDelete = {
                val selectedFiles = filteredItems
                    .filter { it.file.absolutePath in selectedFilePaths }
                    .map { it.file }
                onBulkDelete(selectedFiles)
            },
            searchQuery = searchQuery,
            onSearchChange = onSearchChange,
            isScanning = isScanning,
            onRefresh = onRefresh,
            onChangeDirectory = onChangeDirectory,
            onSearchFocusChanged = { isSearchFocused = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Library Tab Switcher (All Songs vs Folders)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RetroButton(
                text = "All Songs",
                onClick = { selectedTab = LibraryTabMode.ALL_SONGS },
                isSelected = selectedTab == LibraryTabMode.ALL_SONGS,
                modifier = Modifier.weight(1f)
            )
            RetroButton(
                text = "Folders",
                onClick = { selectedTab = LibraryTabMode.FOLDERS },
                isSelected = selectedTab == LibraryTabMode.FOLDERS,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (selectedTab == LibraryTabMode.ALL_SONGS) {
            LibraryAllSongsSection(
                filteredItems = filteredItems,
                isMusicDirectoryConfigured = isMusicDirectoryConfigured,
                isScanning = isScanning,
                searchQuery = searchQuery,
                musicRootDirectory = musicRootDirectory,
                isAllFilesAccessGranted = isAllFilesAccessGranted,
                onRequestAllFilesAccess = onRequestAllFilesAccess,
                onRefresh = onRefresh,
                onChangeDirectory = onChangeDirectory,
                sortField = sortField,
                sortOrder = sortOrder,
                onOpenSortModal = { showSortModal = true },
                onShufflePlay = onShufflePlay,
                showFilterChips = showFilterChips,
                onToggleFilterChips = { showFilterChips = !showFilterChips },
                filterArtist = filterArtist,
                filterGenre = filterGenre,
                filterAlbum = filterAlbum,
                filterYear = filterYear,
                filterFormat = filterFormat,
                onOpenFilterCategory = { activeFilterCategory = it },
                onClearArtist = { filterArtist = null },
                onClearGenre = { filterGenre = null },
                onClearAlbum = { filterAlbum = null },
                onClearYear = { filterYear = null },
                onClearFormat = { filterFormat = null },
                lazyListState = lazyListState,
                letterProvider = letterProvider,
                currentPlayingPath = currentTrack?.file?.absolutePath,
                playlistSet = playlistSet,
                isSelectionMode = isSelectionMode,
                selectedFilePaths = selectedFilePaths,
                onToggleTrackSelection = { path ->
                    selectedFilePaths = if (selectedFilePaths.contains(path)) {
                        selectedFilePaths - path
                    } else {
                        selectedFilePaths + path
                    }
                },
                onTrackSelected = onTrackSelected,
                onTrackLongPressed = onTrackLongPressed
            )
        } else {
            LibraryFolderBrowserSection(
                rawFolderSlice = rawFolderSlice,
                subfolders = searchFilteredSubfolders,
                directTracks = searchFilteredDirectTracks,
                isMusicDirectoryConfigured = isMusicDirectoryConfigured,
                isScanning = isScanning,
                searchQuery = searchQuery,
                folderSortField = folderSortField,
                folderSortOrder = folderSortOrder,
                onOpenFolderSortModal = { showFolderSortModal = true },
                onPlayFolderTracks = onPlayFolderTracks,
                currentFolderPlayableTracks = currentFolderPlayableTracks,
                breadcrumbs = breadcrumbs,
                musicRootDirectory = musicRootDirectory,
                onNavigateToFolder = { currentFolderDirectory = it },
                folderLazyListState = folderLazyListState,
                letterProvider = letterProvider,
                currentPlayingPath = currentTrack?.file?.absolutePath,
                playlistSet = playlistSet,
                isSelectionMode = isSelectionMode,
                selectedFilePaths = selectedFilePaths,
                onToggleTrackSelection = { path ->
                    selectedFilePaths = if (selectedFilePaths.contains(path)) {
                        selectedFilePaths - path
                    } else {
                        selectedFilePaths + path
                    }
                },
                onTrackSelected = onTrackSelected,
                onTrackLongPressed = onTrackLongPressed,
                onChangeDirectory = onChangeDirectory
            )
        }
    }

    // Sort Modal Sheet
    LibrarySortModalSheet(
        visible = showSortModal,
        currentField = sortField,
        currentOrder = sortOrder,
        onDismissRequest = { showSortModal = false },
        onApplySort = { field, order ->
            if (onSelectSortField != null) {
                onSelectSortField(field, order)
            } else if (onSelectSort != null) {
                val legacyMode = if (order == SortOrder.ASCENDING) LibrarySortMode.NAME_ASC else LibrarySortMode.NAME_DESC
                onSelectSort(legacyMode)
            }
        }
    )

    // Folder Sort Modal Sheet
    FolderSortModalSheet(
        visible = showFolderSortModal,
        currentField = folderSortField,
        currentOrder = folderSortOrder,
        onDismissRequest = { showFolderSortModal = false },
        onApplySort = { field, order ->
            folderSortField = field
            folderSortOrder = order
            showFolderSortModal = false
        }
    )

    // Filter Selection Modal Sheet
    var lastFilterCategory by remember { mutableStateOf("Artist") }
    if (activeFilterCategory != null) {
        lastFilterCategory = activeFilterCategory!!
    }
    val cat = activeFilterCategory ?: lastFilterCategory
    val isFilterOpen = activeFilterCategory != null
    val options: List<Pair<String, Int>> = remember(scannedItems, cat, isFilterOpen) {
        if (!isFilterOpen) emptyList()
        else when (cat) {
            "Artist" -> scannedItems
                .map { it.artist.ifBlank { "Unknown Artist" } }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
            "Genre" -> scannedItems
                .map { it.genre.ifBlank { "Unknown Genre" } }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
            "Album" -> scannedItems
                .map { it.album.ifBlank { "Unknown Album" } }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
            "Year" -> scannedItems
                .map { if (it.year > 0) it.year.toString() else "Unknown Year" }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.first }
            "Format" -> scannedItems
                .groupingBy { it.formatLabel }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
            else -> emptyList()
        }
    }
    val currentVal = when (cat) {
        "Artist" -> filterArtist
        "Genre" -> filterGenre
        "Album" -> filterAlbum
        "Year" -> filterYear
        "Format" -> filterFormat
        else -> null
    }
    FilterSelectionModalSheet(
        visible = activeFilterCategory != null,
        title = cat,
        options = options,
        selectedValue = currentVal,
        onDismissRequest = { activeFilterCategory = null },
        onSelectOption = { selected ->
            when (cat) {
                "Artist" -> filterArtist = selected
                "Genre" -> filterGenre = selected
                "Album" -> filterAlbum = selected
                "Year" -> filterYear = selected
                "Format" -> filterFormat = selected
            }
        }
    )
}
