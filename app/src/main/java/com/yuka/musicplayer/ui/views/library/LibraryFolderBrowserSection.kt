package com.yuka.musicplayer.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.yuka.musicplayer.model.FolderBrowserSlice
import com.yuka.musicplayer.model.FolderSortField
import com.yuka.musicplayer.model.MusicFolderNode
import com.yuka.musicplayer.model.ScannedMusicItem
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
internal fun LibraryFolderBrowserSection(
    rawFolderSlice: FolderBrowserSlice,
    subfolders: List<MusicFolderNode>,
    directTracks: List<ScannedMusicItem>,
    isMusicDirectoryConfigured: Boolean,
    isScanning: Boolean,
    searchQuery: String,
    folderSortField: FolderSortField,
    folderSortOrder: SortOrder,
    onOpenFolderSortModal: () -> Unit,
    onPlayFolderTracks: ((List<File>, Boolean) -> Unit)?,
    currentFolderPlayableTracks: List<File>,
    breadcrumbs: List<File>,
    musicRootDirectory: File,
    onNavigateToFolder: (File) -> Unit,
    folderLazyListState: LazyListState,
    letterProvider: (Int) -> String,
    currentPlayingPath: String?,
    playlistSet: Set<String>,
    isSelectionMode: Boolean,
    selectedFilePaths: Set<String>,
    onToggleTrackSelection: (String) -> Unit,
    onTrackSelected: (ScannedMusicItem) -> Unit,
    onTrackLongPressed: (File) -> Unit,
    onChangeDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()

    val totalFolderListItems = (if (subfolders.isNotEmpty()) 1 + subfolders.size else 0) +
        (if (directTracks.isNotEmpty()) 1 + directTracks.size else 0)

    Column(modifier = modifier.fillMaxSize()) {
        // 2. Folder Mode Sub-Header: Sort Folder + Play Folder + Shuffle Folder + Stats
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
                        onOpenFolderSortModal()
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
                                onNavigateToFolder(dir)
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
            val folderRowShape = remember { RoundedCornerShape(14.dp) }
            val folderIconShape = remember { RoundedCornerShape(12.dp) }
            val onSurfaceColor = m3OnSurface()
            val onSurfaceVariantColor = m3OnSurfaceVariant()
            val folderIconBg = accent.copy(alpha = 0.14f)
            val chevronColor = onSurfaceVariantColor.copy(alpha = 0.6f)

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
                            val nodePlayableTracks = node.recursivePlayableFiles

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(folderRowShape)
                                    .clickable {
                                        onNavigateToFolder(node.directory)
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
                }

                // Accurate Folder/DirectTrack Letter Provider for Fast Scroller
                val folderLetterProvider: (Int) -> String = remember(subfolders, directTracks) {
                    { targetIndex ->
                        val subHeaderCount = if (subfolders.isNotEmpty()) 1 else 0
                        val subTotal = subHeaderCount + subfolders.size
                        val label = when {
                            subfolders.isNotEmpty() && targetIndex in 1..subfolders.size -> subfolders[targetIndex - 1].name
                            directTracks.isNotEmpty() && targetIndex >= subTotal + 1 -> {
                                val trackIdx = targetIndex - (subTotal + 1)
                                directTracks.getOrNull(trackIdx)?.displayTitle ?: ""
                            }
                            else -> subfolders.firstOrNull()?.name ?: directTracks.firstOrNull()?.displayTitle ?: ""
                        }
                        label.trimStart().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
                    }
                }

                // Fast Scroller for Folder Tab
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
