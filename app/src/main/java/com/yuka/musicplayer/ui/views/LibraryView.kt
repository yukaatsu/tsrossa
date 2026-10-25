package com.yuka.musicplayer.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortField
import com.yuka.musicplayer.model.SortOrder
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.dialogs.FilterSelectionModalSheet
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
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()

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

    val currentFilteredItems by rememberUpdatedState(filteredItems)
    val currentSortField by rememberUpdatedState(sortField)

    // Current bubble letter for Fast Scroller
    val currentBubbleLetter by remember {
        derivedStateOf {
            val list = currentFilteredItems
            val firstVisibleIdx = lazyListState.firstVisibleItemIndex
            if (firstVisibleIdx in list.indices) {
                val item = list[firstVisibleIdx]
                val primaryText = when (currentSortField) {
                    SortField.ARTIST -> item.displayArtist.ifBlank { item.displayTitle }
                    SortField.ALBUM -> item.album.ifBlank { item.displayTitle }
                    SortField.ALBUM_ARTIST -> item.albumArtist.ifBlank { item.displayArtist.ifBlank { item.displayTitle } }
                    SortField.GENRE -> item.genre.ifBlank { item.displayTitle }
                    SortField.COMPOSER -> item.composer.ifBlank { item.displayTitle }
                    else -> item.displayTitle.ifBlank { item.title }
                }
                // Find the first letter (A-Z), skipping leading track numbers or punctuation
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

    // Back handlers
    BackHandler(enabled = !isNowPlayingVisible && isSelectionMode) {
        isSelectionMode = false
        selectedFilePaths = emptySet()
    }

    BackHandler(enabled = !isNowPlayingVisible && !isSelectionMode && searchQuery.isNotEmpty()) {
        onSearchChange("")
        focusManager.clearFocus()
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
                        modifier = Modifier.fillMaxWidth(),
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

                // Refresh Icon Button
                TactileBox(
                    onClick = onRefresh,
                    pressedScale = 0.88f,
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                ) {
                    GlyphRefresh(color = accent, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlyphMusicNote(color = m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty() || filterArtist != null || filterGenre != null) {
                            "No matching tracks found"
                        } else {
                            "No music files found"
                        },
                        color = m3OnSurfaceVariant(),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont
                    )
                }
            }
        } else {
            val currentPlayingPath = currentTrack?.file?.absolutePath
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
                        key = { it.file.absolutePath },
                        contentType = { "library_track_row" }
                    ) { item ->
                        val file = item.file
                        val isSupported = item.isSupported
                        val isPlaying = file.absolutePath == currentPlayingPath
                        val inPlaylist = playlistSet.contains(file.absolutePath)
                        val isSelected = selectedFilePaths.contains(file.absolutePath)
                        val itemAlpha = if (isSupported) 1f else 0.42f

                        val displayTitle = item.displayTitle.ifEmpty { item.title }
                        val secondaryMetadata = item.secondaryMetadata.ifEmpty { item.formatLabel }

                        // Flat M3 Row with ripple / tactile touch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(itemAlpha)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isSelected -> accent.copy(alpha = 0.20f)
                                        isPlaying -> accent.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedFilePaths = if (isSelected) {
                                                selectedFilePaths - file.absolutePath
                                            } else {
                                                selectedFilePaths + file.absolutePath
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
                                                    selectedFilePaths - file.absolutePath
                                                } else {
                                                    selectedFilePaths + file.absolutePath
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
                                            if (isSelected) accent else m3SubtleFill(alpha = 0.15f)
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
                                filePath = file.absolutePath,
                                isCurrentTrack = isPlaying,
                                currentTrackArt = null,
                                modifier = Modifier.size(46.dp),
                                cornerRadius = 12.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isPlaying) accent.copy(alpha = 0.22f) else m3SubtleFill(alpha = 0.08f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPlaying) {
                                        GlyphNowPlaying(color = accent, modifier = Modifier.size(20.dp))
                                    } else {
                                        GlyphMusicNote(
                                            color = if (isSupported) m3OnSurfaceVariant() else Color(0xFFFF5252),
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
                                        isSupported -> m3OnSurface()
                                        else -> m3OnSurfaceVariant()
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
                                    color = if (isPlaying) accent.copy(alpha = 0.85f) else m3OnSurfaceVariant(),
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
                                TactileBox(
                                    onClick = {
                                        if (isSupported) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTrackLongPressed(file)
                                        }
                                    },
                                    pressedScale = 0.85f,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                ) {
                                    GlyphMoreVert(color = m3OnSurfaceVariant(), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Fast Scroller
                LibraryFastScroller(
                    lazyListState = lazyListState,
                    itemCount = filteredItems.size,
                    currentLetter = currentBubbleLetter,
                    onScrollToIndex = { targetIndex ->
                        coroutineScope.launch {
                            lazyListState.scrollToItem(targetIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
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

    // Filter Selection Modal Sheet (remember last category so exit animation & unblur are smooth)
    var lastFilterCategory by remember { mutableStateOf("Artist") }
    if (activeFilterCategory != null) {
        lastFilterCategory = activeFilterCategory!!
    }
    val cat = activeFilterCategory ?: lastFilterCategory
    val options: List<Pair<String, Int>> = remember(scannedItems, cat) {
        when (cat) {
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
