package com.yuka.musicplayer.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.FolderBrowserSlice
import com.yuka.musicplayer.model.FolderSortField
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.LibraryTabMode
import com.yuka.musicplayer.model.MusicFolderNode
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortField
import com.yuka.musicplayer.model.SortOrder
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.dialogs.FilterSelectionModalSheet
import com.yuka.musicplayer.ui.dialogs.FolderSortModalSheet
import com.yuka.musicplayer.ui.dialogs.LibrarySortModalSheet
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.ui.theme.TrueBlack
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.util.FuzzySearchHelper
import com.yuka.musicplayer.util.MusicScannerHelper
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import com.yuka.musicplayer.util.TrackTitleParser
import kotlinx.coroutines.launch
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
@OptIn(ExperimentalFoundationApi::class)
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
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()
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

    // Modal blur is handled directly by CyberBottomSheet's RegisterModalBlur in Sort & Filter sheets

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
            // Category filters
            if (filterArtist != null && (item.artist.ifBlank { "Unknown Artist" }) != filterArtist) return@filter false
            if (filterGenre != null && (item.genre.ifBlank { "Unknown Genre" }) != filterGenre) return@filter false
            if (filterAlbum != null && (item.album.ifBlank { "Unknown Album" }) != filterAlbum) return@filter false
            if (filterYear != null && ((if (item.year > 0) item.year.toString() else "Unknown Year") != filterYear)) return@filter false
            if (filterFormat != null && item.formatLabel != filterFormat) return@filter false

            // Fuzzy Search filter
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

    // Fast Scroller letter provider callback (Lazy: evaluated ONLY when user is dragging the thumb)
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

    // Folder calculations (Pure-RAM, ZERO FUSE canonicalPath/listFiles syscalls on UI thread)
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

    // Decoupled Step 1: Pure-RAM Single-Pass Folder Indexer (does NOT depend on sort criteria!)
    val rawFolderSlice = remember(
        musicRootDirectory,
        currentFolderDirectory,
        scannedItems,
        selectedTab
    ) {
        if (selectedTab != LibraryTabMode.FOLDERS) {
            FolderBrowserSlice(
                currentDirectory = currentFolderDirectory,
                isRoot = true,
                parentDirectory = null,
                subfolders = emptyList(),
                directTracks = emptyList(),
                allRecursivePlayableFiles = emptyList(),
                totalDirectMusicCount = 0,
                totalRecursiveMusicCount = 0
            )
        } else {
            MusicScannerHelper.buildFolderBrowserSliceUnordered(
                rootDir = musicRootDirectory,
                currentDir = currentFolderDirectory,
                scannedItems = scannedItems
            )
        }
    }

    // Decoupled Step 2: Instant O(K log K) in-memory sort when user changes Sort Field / Order
    val sortedSubfolders = remember(rawFolderSlice.subfolders, folderSortField, folderSortOrder) {
        MusicScannerHelper.sortFolderNodes(
            nodes = rawFolderSlice.subfolders,
            folderSortField = folderSortField,
            folderSortOrder = folderSortOrder
        )
    }

    // O(1) lookup from pre-indexed FolderBrowserSlice (replaces redundant O(N) canonicalPath loop)
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

    // Clear focus from search bar ONLY when search bar is actually focused and user starts scrolling.
    // Using snapshotFlow inside LaunchedEffect(isSearchFocused) prevents reading isScrollInProgress
    // in LibraryView's composition scope (zero recomposition on scroll start/stop & zero hide(ime()) spam).
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // 1. Top Header: Search Bar OR Selection Action Bar
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .glassMorphic(
                        backgroundColor = accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileBox(
                    onClick = {
                        isSelectionMode = false
                        selectedFilePaths = emptySet()
                    },
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                ) {
                    GlyphClose(color = accent, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "${selectedFilePaths.size} selected",
                    color = m3OnSurface(),
                    fontSize = 14.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Select All / Deselect All
                val allSelected = selectedFilePaths.size == filteredItems.size && filteredItems.isNotEmpty()
                TactileBox(
                    onClick = {
                        if (allSelected) {
                            selectedFilePaths = emptySet()
                        } else {
                            selectedFilePaths = filteredItems.map { it.file.absolutePath }.toSet()
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(m3SubtleFill(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (allSelected) "Deselect All" else "Select All",
                        color = m3OnSurface(),
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Bulk Add to Playlist
                if (selectedFilePaths.isNotEmpty()) {
                    TactileBox(
                        onClick = {
                            val selectedFiles = filteredItems
                                .filter { it.file.absolutePath in selectedFilePaths }
                                .map { it.file }
                            onBulkAddToPlaylist(selectedFiles)
                        },
                        modifier = Modifier.size(34.dp).clip(CircleShape)
                    ) {
                        GlyphPlaylists(color = accent, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Bulk Delete
                    TactileBox(
                        onClick = {
                            val selectedFiles = filteredItems
                                .filter { it.file.absolutePath in selectedFilePaths }
                                .map { it.file }
                            onBulkDelete(selectedFiles)
                        },
                        modifier = Modifier.size(34.dp).clip(CircleShape)
                    ) {
                        GlyphDelete(color = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else {
            // Standard Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(m3SubtleFill(alpha = 0.08f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphSearch(color = accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search titles, artists, albums...",
                            color = m3OnSurfaceVariant(),
                            fontSize = 14.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        textStyle = TextStyle(
                            color = m3OnSurface(),
                            fontSize = 14.sp,
                            fontFamily = ModernSansFont
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(accent)
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    TactileBox(
                        onClick = { onSearchChange("") },
                        pressedScale = 0.85f,
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                    ) {
                        GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Folder Picker Icon Button
                TactileBox(
                    onClick = onChangeDirectory,
                    pressedScale = 0.88f,
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                ) {
                    GlyphFolder(color = accent, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                TactileBox(
                    onClick = onRefresh,
                    pressedScale = 0.88f,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isScanning) accent.copy(alpha = 0.16f) else Color.Transparent)
                ) {
                    ScanningRefreshIcon(
                        isScanning = isScanning,
                        accent = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

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
        // 2. Sub-Header: Sort Button + Shuffle Button + Track Counter
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sort Modal Trigger Button
                TactileBox(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSortModal = true
                    },
                    pressedScale = 0.92f,
                    modifier = Modifier
                        .glassMorphic(
                            backgroundColor = m3SubtleFill(alpha = 0.08f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphSort(color = accent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val orderSymbol = if (sortOrder == SortOrder.ASCENDING) "↑" else "↓"
                        Text(
                            text = "${sortField.displayName} $orderSymbol",
                            color = m3OnSurface(),
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        GlyphChevronDown(
                            color = accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Filter Toggle Button
                val activeFilterCount = listOfNotNull(filterArtist, filterGenre, filterAlbum, filterYear, filterFormat).size
                TactileBox(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showFilterChips = !showFilterChips
                    },
                    pressedScale = 0.92f,
                    modifier = Modifier
                        .glassMorphic(
                            backgroundColor = if (showFilterChips || activeFilterCount > 0) accent.copy(alpha = 0.20f) else m3SubtleFill(alpha = 0.08f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphFilter(color = if (showFilterChips || activeFilterCount > 0) accent else m3OnSurface(), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (activeFilterCount > 0) "Filter ($activeFilterCount)" else "Filter",
                            color = if (showFilterChips || activeFilterCount > 0) accent else m3OnSurface(),
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Shuffle Play Button
                TactileBox(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShufflePlay()
                    },
                    pressedScale = 0.92f,
                    modifier = Modifier
                        .glassMorphic(
                            backgroundColor = accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphShuffle(color = accent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shuffle",
                            color = accent,
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Track Count & Scanning Indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = accent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "${filteredItems.size} tracks",
                    color = m3OnSurfaceVariant(),
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont
                )
            }
        }

        // 3. Category Filter Chips (Artist, Genre, Album, Year, Format) - Hidden by default, toggled via Filter button
        AnimatedVisibility(
            visible = showFilterChips,
            enter = androidx.compose.animation.expandVertically() + fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChipItem(
                    label = "Artist",
                    activeValue = filterArtist,
                    onClick = { activeFilterCategory = "Artist" },
                    onClear = { filterArtist = null }
                )
                FilterChipItem(
                    label = "Genre",
                    activeValue = filterGenre,
                    onClick = { activeFilterCategory = "Genre" },
                    onClear = { filterGenre = null }
                )
                FilterChipItem(
                    label = "Album",
                    activeValue = filterAlbum,
                    onClick = { activeFilterCategory = "Album" },
                    onClear = { filterAlbum = null }
                )
                FilterChipItem(
                    label = "Year",
                    activeValue = filterYear,
                    onClick = { activeFilterCategory = "Year" },
                    onClear = { filterYear = null }
                )
                FilterChipItem(
                    label = "Format",
                    activeValue = filterFormat,
                    onClick = { activeFilterCategory = "Format" },
                    onClear = { filterFormat = null }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Track List with Fast Scroller
        if (!isMusicDirectoryConfigured) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphFolderOpen(color = accent, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Set Your Music Directory",
                        color = m3OnSurface(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose the folder where your audio collection is stored to load your library.",
                        color = m3OnSurfaceVariant(),
                        fontSize = 13.sp,
                        fontFamily = ModernSansFont,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TactileBox(
                        onClick = onChangeDirectory,
                        pressedScale = 0.94f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .glassMorphic(
                                backgroundColor = accent.copy(alpha = 0.22f),
                                borderColor = accent.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphFolder(color = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Music Directory",
                                color = accent,
                                fontSize = 13.5.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accent,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Scanning music library...",
                            color = m3OnSurface(),
                            fontSize = 14.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = musicRootDirectory.name,
                            color = m3OnSurfaceVariant(),
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                } else if (searchQuery.isNotEmpty() || filterArtist != null || filterGenre != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlyphMusicNote(color = m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching tracks found",
                            color = m3OnSurfaceVariant(),
                            fontSize = 14.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                } else {
                    val isAndroid11Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                    val blockingNomedia = remember(musicRootDirectory) {
                        com.yuka.musicplayer.util.MusicScannerHelper.findBlockingNomediaFiles(musicRootDirectory)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    ) {
                        GlyphFolder(color = m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No music files found",
                            color = m3OnSurface(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Active Folder: ${musicRootDirectory.absolutePath}",
                            color = m3OnSurfaceVariant(),
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (blockingNomedia.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFF9100).copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "⚠️ .nomedia detected in ancestor folder:\n${File(blockingNomedia.first()).parentFile?.name ?: "parent"}",
                                    color = Color(0xFFFF9100),
                                    fontSize = 11.sp,
                                    fontFamily = ModernSansFont,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        if (isAndroid11Plus && !isAllFilesAccessGranted) {
                            Spacer(modifier = Modifier.height(14.dp))
                            TactileBox(
                                onClick = onRequestAllFilesAccess,
                                pressedScale = 0.94f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Enable All Files Access",
                                        color = accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ModernSansFont
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Samsung One UI / Android 11 hides FLAC files under subfolders until All Files Access is granted.",
                                        color = m3OnSurfaceVariant(),
                                        fontSize = 10.5.sp,
                                        fontFamily = ModernSansFont,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TactileBox(
                                onClick = onRefresh,
                                pressedScale = 0.94f,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(m3SubtleFill(0.12f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Rescan Library",
                                    color = m3OnSurface(),
                                    fontSize = 12.sp,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TactileBox(
                                onClick = onChangeDirectory,
                                pressedScale = 0.94f,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accent.copy(alpha = 0.20f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Change Folder",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val currentPlayingPath = currentTrack?.file?.absolutePath
            val trackRowShape = remember { RoundedCornerShape(14.dp) }
            val onSurfaceColor = m3OnSurface()
            val onSurfaceVariantColor = m3OnSurfaceVariant()
            val subtleFill08 = m3SubtleFill(alpha = 0.08f)
            val subtleFill15 = m3SubtleFill(alpha = 0.15f)
            val playingRowBg = remember(accent) { accent.copy(alpha = 0.12f) }
            val selectedRowBg = remember(accent) { accent.copy(alpha = 0.20f) }
            val nowPlayingThumbBg = remember(accent) { accent.copy(alpha = 0.22f) }
            val secondaryPlayingColor = remember(accent) { accent.copy(alpha = 0.85f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 168.dp, end = 12.dp)
                ) {
                    items(
                        items = filteredItems,
                        key = { it.filePath },
                        contentType = { "library_track_row" }
                    ) { item ->
                        val file = item.file
                        val path = item.filePath
                        val isSupported = item.isSupported
                        val isPlaying = path == currentPlayingPath
                        val inPlaylist = playlistSet.contains(path)
                        val isSelected = selectedFilePaths.contains(path)
                        val itemAlpha = if (isSupported) 1f else 0.42f

                        val displayTitle = item.displayTitle.ifEmpty { item.title }
                        val secondaryMetadata = item.secondaryMetadata.ifEmpty { item.formatLabel }

                        // Flat M3 Row with ripple / tactile touch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(itemAlpha)
                                .clip(trackRowShape)
                                .background(
                                    when {
                                        isSelected -> selectedRowBg
                                        isPlaying -> playingRowBg
                                        else -> Color.Transparent
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedFilePaths = if (isSelected) {
                                                selectedFilePaths - path
                                            } else {
                                                selectedFilePaths + path
                                            }
                                        } else {
                                            onTrackSelected(item)
                                        }
                                    },
                                    onLongClick = {
                                        if (isSupported) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isSelectionMode) {
                                                selectedFilePaths = if (isSelected) {
                                                    selectedFilePaths - path
                                                } else {
                                                    selectedFilePaths + path
                                                }
                                            } else {
                                                onTrackLongPressed(file)
                                            }
                                        }
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selection checkbox indicator when in selection mode
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) accent else subtleFill15
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        GlyphCheck(color = TrueBlack, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            // Artwork Squircle (46dp, RoundedCornerShape(12.dp))
                            TrackAlbumThumbnail(
                                filePath = path,
                                isCurrentTrack = isPlaying,
                                currentTrackArt = null,
                                modifier = Modifier.size(46.dp),
                                cornerRadius = 12.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isPlaying) nowPlayingThumbBg else subtleFill08
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPlaying) {
                                        GlyphNowPlaying(color = accent, modifier = Modifier.size(20.dp))
                                    } else {
                                        GlyphMusicNote(
                                            color = if (isSupported) onSurfaceVariantColor else Color(0xFFFF5252),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Metadata Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayTitle,
                                    color = when {
                                        isPlaying -> accent
                                        isSupported -> onSurfaceColor
                                        else -> onSurfaceVariantColor
                                    },
                                    fontFamily = ModernSansFont,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = secondaryMetadata,
                                    color = if (isPlaying) secondaryPlayingColor else onSurfaceVariantColor,
                                    fontSize = 12.sp,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Trailing star for playlist items & 3-dot options
                            if (inPlaylist && isSupported) {
                                GlyphPin(color = accent, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            if (!isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (isSupported) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onTrackLongPressed(file)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    GlyphMoreVert(color = onSurfaceVariantColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Fast Scroller
                LibraryFastScroller(
                    lazyListState = lazyListState,
                    itemCount = filteredItems.size,
                    letterProvider = letterProvider,
                    onScrollToIndex = { targetIndex ->
                        coroutineScope.launch {
                            lazyListState.scrollToItem(targetIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        } else {
            // 2. Folder Mode Sub-Header: Sort Folder + Shuffle Folder + Play Folder + Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Folder Sort Button
                    TactileBox(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showFolderSortModal = true
                        },
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .glassMorphic(
                                backgroundColor = m3SubtleFill(alpha = 0.08f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphSort(color = accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val orderSymbol = if (folderSortOrder == SortOrder.ASCENDING) "↑" else "↓"
                            Text(
                                text = "${folderSortField.displayName} $orderSymbol",
                                color = m3OnSurface(),
                                fontSize = 11.5.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            GlyphChevronDown(color = accent, modifier = Modifier.size(14.dp))
                        }
                    }

                    // Play Folder Button
                    TactileBox(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayFolderTracks?.invoke(currentFolderPlayableTracks, false)
                        },
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .glassMorphic(
                                backgroundColor = accent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphPlay(color = accent, modifier = Modifier.size(13.dp), filled = true)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Play",
                                color = accent,
                                fontSize = 11.5.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Shuffle Folder Button
                    TactileBox(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayFolderTracks?.invoke(currentFolderPlayableTracks, true)
                        },
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .glassMorphic(
                                backgroundColor = accent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphShuffle(color = accent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Shuffle",
                                color = accent,
                                fontSize = 11.5.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Stats: Total recursive music count in current directory
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = accent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "${rawFolderSlice.totalRecursiveMusicCount} ${if (rawFolderSlice.totalRecursiveMusicCount == 1) "track" else "tracks"}",
                        color = m3OnSurfaceVariant(),
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Breadcrumbs Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                breadcrumbs.forEachIndexed { index, dir ->
                    val isLast = index == breadcrumbs.lastIndex
                    val isRootCrumb = index == 0
                    val label = if (isRootCrumb) "Root (${musicRootDirectory.name})" else dir.name

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TactileBox(
                            onClick = {
                                if (!isLast) {
                                    currentFolderDirectory = dir
                                }
                            },
                            pressedScale = 0.95f,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isLast) accent.copy(alpha = 0.15f) else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isRootCrumb) {
                                    GlyphFolder(
                                        color = if (isLast) accent else m3OnSurfaceVariant(),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = label,
                                    color = if (isLast) accent else m3OnSurfaceVariant(),
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    fontFamily = ModernSansFont
                                )
                            }
                        }
                        if (!isLast) {
                            Text(
                                text = "/",
                                color = m3OnSurfaceVariant().copy(alpha = 0.45f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Folder & Track Lists
            val subfolders = searchFilteredSubfolders
            val directTracks = searchFilteredDirectTracks

            if (!isMusicDirectoryConfigured) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphFolderOpen(color = accent, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Set Your Music Directory",
                            color = m3OnSurface(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModernSansFont,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Choose the folder where your audio collection is stored to load your library.",
                            color = m3OnSurfaceVariant(),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        TactileBox(
                            onClick = onChangeDirectory,
                            pressedScale = 0.94f,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .glassMorphic(
                                    backgroundColor = accent.copy(alpha = 0.22f),
                                    borderColor = accent.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphFolder(color = accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select Music Directory",
                                    color = accent,
                                    fontSize = 13.5.sp,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else if (subfolders.isEmpty() && directTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = accent,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Scanning folders...",
                                color = m3OnSurface(),
                                fontSize = 14.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlyphFolder(color = m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "No matching folders or tracks"
                                } else {
                                    "This folder is empty"
                                },
                                color = m3OnSurfaceVariant(),
                                fontSize = 14.sp,
                                fontFamily = ModernSansFont
                            )
                        }
                    }
                }
            } else {
                // Hoist shapes & colors outside LazyColumn for zero per-frame allocations during scrolling
                val currentPlayingPath = currentTrack?.file?.absolutePath
                val folderRowShape = remember { RoundedCornerShape(14.dp) }
                val folderIconShape = remember { RoundedCornerShape(12.dp) }
                val onSurfaceColor = m3OnSurface()
                val onSurfaceVariantColor = m3OnSurfaceVariant()
                val folderIconBg = accent.copy(alpha = 0.14f)
                val chevronColor = onSurfaceVariantColor.copy(alpha = 0.6f)
                val selectedRowBg = accent.copy(alpha = 0.20f)
                val playingRowBg = accent.copy(alpha = 0.12f)
                val nowPlayingThumbBg = accent.copy(alpha = 0.22f)
                val secondaryPlayingColor = accent.copy(alpha = 0.85f)
                val subtleFill08 = m3SubtleFill(alpha = 0.08f)
                val subtleFill15 = m3SubtleFill(alpha = 0.15f)

                val trackSectionHeaderOffset = if (subfolders.isNotEmpty()) 1 + subfolders.size else 0
                val totalFolderListItems = (if (subfolders.isNotEmpty()) 1 + subfolders.size else 0) +
                    (if (directTracks.isNotEmpty()) 1 + directTracks.size else 0)

                val folderLetterProvider: (Int) -> String = remember(subfolders, directTracks) {
                    { index ->
                        val label = when {
                            subfolders.isNotEmpty() && index in 1..subfolders.size -> {
                                subfolders[index - 1].name
                            }
                            directTracks.isNotEmpty() -> {
                                val trackIdx = index - trackSectionHeaderOffset - 1
                                directTracks.getOrNull(trackIdx)?.let { it.displayTitle.ifBlank { it.title } } ?: ""
                            }
                            else -> ""
                        }
                        val firstAlpha = label.trimStart().firstOrNull { it.isLetter() }?.uppercaseChar()
                        firstAlpha?.toString() ?: "#"
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = folderLazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 168.dp)
                    ) {
                        // Subfolders section
                        if (subfolders.isNotEmpty()) {
                            item(
                                key = "section_subfolders_header",
                                contentType = "folder_section_header"
                            ) {
                                Text(
                                    text = "Folders (${subfolders.size})",
                                    color = onSurfaceVariantColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ModernSansFont,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            items(
                                items = subfolders,
                                key = { "folder_${it.directoryPath}" },
                                contentType = { "folder_node_row" }
                            ) { node ->
                                // Pure O(1) lookup precomputed in MusicScannerHelper (ZERO canonicalPath / loop!)
                                val nodePlayableTracks = node.recursivePlayableFiles

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(folderRowShape)
                                        .clickable {
                                            currentFolderDirectory = node.directory
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(folderIconShape)
                                            .background(folderIconBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        GlyphFolder(color = accent, modifier = Modifier.size(22.dp))
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = node.name,
                                            color = onSurfaceColor,
                                            fontFamily = ModernSansFont,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val trackLabel = if (node.recursiveMusicCount == 1) "track" else "tracks"
                                        val subInfo = if (node.subfoldersCount > 0) {
                                            val folderLabel = if (node.subfoldersCount == 1) "folder" else "folders"
                                            "${node.recursiveMusicCount} $trackLabel • ${node.subfoldersCount} $folderLabel"
                                        } else {
                                            "${node.recursiveMusicCount} $trackLabel"
                                        }
                                        Text(
                                            text = subInfo,
                                            color = onSurfaceVariantColor,
                                            fontSize = 12.sp,
                                            fontFamily = ModernSansFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (nodePlayableTracks.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onPlayFolderTracks?.invoke(nodePlayableTracks, false)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            GlyphPlay(color = accent, modifier = Modifier.size(16.dp), filled = true)
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onPlayFolderTracks?.invoke(nodePlayableTracks, true)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            GlyphShuffle(color = accent, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    GlyphChevronRight(
                                        color = chevronColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Direct tracks section
                        if (directTracks.isNotEmpty()) {
                            item(
                                key = "section_tracks_header",
                                contentType = "folder_section_header"
                            ) {
                                Text(
                                    text = "Tracks (${directTracks.size})",
                                    color = onSurfaceVariantColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ModernSansFont,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = if (subfolders.isNotEmpty()) 12.dp else 6.dp)
                                )
                            }
                            items(
                                items = directTracks,
                                key = { "track_${it.filePath}" },
                                contentType = { "folder_track_row" }
                            ) { item ->
                                val file = item.file
                                val path = item.filePath
                                val isSupported = item.isSupported
                                val isPlaying = path == currentPlayingPath
                                val inPlaylist = playlistSet.contains(path)
                                val isSelected = selectedFilePaths.contains(path)
                                val itemAlpha = if (isSupported) 1f else 0.42f

                                val displayTitle = item.displayTitle.ifEmpty { item.title }
                                val secondaryMetadata = item.secondaryMetadata.ifEmpty { item.formatLabel }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(itemAlpha)
                                        .clip(folderRowShape)
                                        .background(
                                            when {
                                                isSelected -> selectedRowBg
                                                isPlaying -> playingRowBg
                                                else -> Color.Transparent
                                            }
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedFilePaths = if (isSelected) {
                                                        selectedFilePaths - path
                                                    } else {
                                                        selectedFilePaths + path
                                                    }
                                                } else {
                                                    onTrackSelected(item)
                                                }
                                            },
                                            onLongClick = {
                                                if (isSupported) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (isSelectionMode) {
                                                        selectedFilePaths = if (isSelected) {
                                                            selectedFilePaths - path
                                                        } else {
                                                            selectedFilePaths + path
                                                        }
                                                    } else {
                                                        onTrackLongPressed(file)
                                                    }
                                                }
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) accent else subtleFill15
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                GlyphCheck(color = TrueBlack, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }

                                    TrackAlbumThumbnail(
                                        filePath = path,
                                        isCurrentTrack = isPlaying,
                                        currentTrackArt = null,
                                        modifier = Modifier.size(46.dp),
                                        cornerRadius = 12.dp
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (isPlaying) nowPlayingThumbBg else subtleFill08
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isPlaying) {
                                                GlyphNowPlaying(color = accent, modifier = Modifier.size(20.dp))
                                            } else {
                                                GlyphMusicNote(
                                                    color = if (isSupported) onSurfaceVariantColor else Color(0xFFFF5252),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayTitle,
                                            color = when {
                                                isPlaying -> accent
                                                isSupported -> onSurfaceColor
                                                else -> onSurfaceVariantColor
                                            },
                                            fontFamily = ModernSansFont,
                                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = secondaryMetadata,
                                            color = if (isPlaying) secondaryPlayingColor else onSurfaceVariantColor,
                                            fontSize = 12.sp,
                                            fontFamily = ModernSansFont,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (inPlaylist && isSupported) {
                                        GlyphPin(color = accent, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    if (!isSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    if (isSupported) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onTrackLongPressed(file)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            GlyphMoreVert(color = onSurfaceVariantColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Fast Scroller for Folder Tab (matches All Songs tab smoothness)
                    LibraryFastScroller(
                        lazyListState = folderLazyListState,
                        itemCount = totalFolderListItems,
                        letterProvider = folderLetterProvider,
                        onScrollToIndex = { targetIndex ->
                            coroutineScope.launch {
                                folderLazyListState.scrollToItem(targetIndex)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
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

    // Filter Selection Modal Sheet (remember last category so exit animation & unblur are smooth)
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

@Composable
private fun FilterChipItem(
    label: String,
    activeValue: String?,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    val accent = LocalAccentColor.current
    val isActive = activeValue != null

    Row(
        modifier = Modifier
            .glassMorphic(
                backgroundColor = if (isActive) accent.copy(alpha = 0.18f) else m3SubtleFill(alpha = 0.08f),
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isActive) "$label: $activeValue" else label,
            color = if (isActive) accent else m3OnSurface(),
            fontSize = 11.sp,
            fontFamily = ModernSansFont,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isActive) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onClear() },
                contentAlignment = Alignment.Center
            ) {
                GlyphClose(color = accent, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun ScanningRefreshIcon(
    isScanning: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (isScanning) {
        val infiniteTransition = rememberInfiniteTransition(label = "rescanSpin")
        val rescanRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = LinearEasing)
            ),
            label = "rescanRotation"
        )
        GlyphRefresh(
            color = accent,
            modifier = modifier.graphicsLayer {
                rotationZ = rescanRotation
            }
        )
    } else {
        GlyphRefresh(
            color = accent,
            modifier = modifier
        )
    }
}
