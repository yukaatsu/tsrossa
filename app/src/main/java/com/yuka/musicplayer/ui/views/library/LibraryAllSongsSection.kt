package com.yuka.musicplayer.ui.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortField
import com.yuka.musicplayer.model.SortOrder
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun LibraryAllSongsSection(
    filteredItems: List<ScannedMusicItem>,
    isMusicDirectoryConfigured: Boolean,
    isScanning: Boolean,
    searchQuery: String,
    musicRootDirectory: File,
    isAllFilesAccessGranted: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onRefresh: () -> Unit,
    onChangeDirectory: () -> Unit,
    sortField: SortField,
    sortOrder: SortOrder,
    onOpenSortModal: () -> Unit,
    onShufflePlay: () -> Unit,
    showFilterChips: Boolean,
    onToggleFilterChips: () -> Unit,
    filterArtist: String?,
    filterGenre: String?,
    filterAlbum: String?,
    filterYear: String?,
    filterFormat: String?,
    onOpenFilterCategory: (String) -> Unit,
    onClearArtist: () -> Unit,
    onClearGenre: () -> Unit,
    onClearAlbum: () -> Unit,
    onClearYear: () -> Unit,
    onClearFormat: () -> Unit,
    lazyListState: LazyListState,
    letterProvider: (Int) -> String,
    currentPlayingPath: String?,
    playlistSet: Set<String>,
    isSelectionMode: Boolean,
    selectedFilePaths: Set<String>,
    onToggleTrackSelection: (String) -> Unit,
    onTrackSelected: (ScannedMusicItem) -> Unit,
    onTrackLongPressed: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // 2. Sub-Header: Sort Button + Shuffle Button + Track Counter + Filter Toggle
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
                        onOpenSortModal()
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

                // Shuffle Button
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

            // Right side: Track counter + Filter Toggle Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = accent
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = "${filteredItems.size} ${if (filteredItems.size == 1) "track" else "tracks"}",
                    color = m3OnSurfaceVariant(),
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont
                )

                // Category Filter Chips Toggle Button
                val hasActiveFilters = filterArtist != null || filterGenre != null || filterAlbum != null || filterYear != null || filterFormat != null
                TactileBox(
                    onClick = onToggleFilterChips,
                    pressedScale = 0.88f,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasActiveFilters) accent.copy(alpha = 0.22f)
                            else if (showFilterChips) m3SubtleFill(0.12f)
                            else Color.Transparent
                        )
                ) {
                    GlyphFilter(
                        color = if (hasActiveFilters) accent else m3OnSurfaceVariant(),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 3. Category Filter Chips Row
        AnimatedVisibility(
            visible = showFilterChips,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                LibraryFilterChipsRow(
                    filterArtist = filterArtist,
                    filterGenre = filterGenre,
                    filterAlbum = filterAlbum,
                    filterYear = filterYear,
                    filterFormat = filterFormat,
                    onOpenCategory = onOpenFilterCategory,
                    onClearArtist = onClearArtist,
                    onClearGenre = onClearGenre,
                    onClearAlbum = onClearAlbum,
                    onClearYear = onClearYear,
                    onClearFormat = onClearFormat
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
                        val path = item.filePath
                        val isPlaying = path == currentPlayingPath
                        val isSelected = selectedFilePaths.contains(path)
                        val inPlaylist = playlistSet.contains(path)

                        LibraryTrackRow(
                            item = item,
                            isPlaying = isPlaying,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            inPlaylist = inPlaylist,
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleTrackSelection(path)
                                } else {
                                    onTrackSelected(item)
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    onToggleTrackSelection(path)
                                } else {
                                    onTrackLongPressed(item.file)
                                }
                            },
                            onMenuClick = {
                                onTrackLongPressed(item.file)
                            }
                        )
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
    }
}
