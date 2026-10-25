package com.yuka.musicplayer.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3ActivePillContainer
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceBase
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.util.FuzzySearchHelper
import com.yuka.musicplayer.util.TrackAlbumThumbnail
import com.yuka.musicplayer.util.TrackTitleParser
import java.io.File

/**
 * Material 3 Glassmorphic Playlist Screen:
 * - Level 1: Playlist Cards Overview with Fuzzy Search & New Playlist button.
 * - Level 2: Playlist Detail with sequential Play & random Shuffle buttons, track list, and search.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
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
                // Opening a Playlist Detail: 3D Spring Zoom-In + Slide from Right
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f),
                    initialOffsetX = { fullWidth -> fullWidth / 3 }
                ) + scaleIn(
                    initialScale = 0.88f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                        targetOffsetX = { fullWidth -> -fullWidth / 4 }
                    ) + scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(durationMillis = 200)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 160)
                    )
                )
            } else {
                // Returning to Playlist Overview: 3D Spring Pop-Out + Restore Overview
                (slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f),
                    initialOffsetX = { fullWidth -> -fullWidth / 4 }
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 360f)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                        targetOffsetX = { fullWidth -> fullWidth / 3 }
                    ) + scaleOut(
                        targetScale = 0.88f,
                        animationSpec = tween(durationMillis = 190)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 150)
                    )
                )
            }
        },
        label = "playlistNavigationTransition",
        modifier = modifier.fillMaxSize()
    ) { playlistTarget ->
        if (playlistTarget == null) {
            // ==========================================
            // LEVEL 1: PLAYLIST CARDS OVERVIEW
            // ==========================================
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Search Bar with "+ New" button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphSearch(color = accent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (playlistSearchQuery.isEmpty()) {
                            Text(
                                text = "Search playlists...",
                                color = m3OnSurfaceVariant().copy(alpha = 0.65f),
                                fontSize = 14.sp,
                                fontFamily = ModernSansFont
                            )
                        }
                        BasicTextField(
                            value = playlistSearchQuery,
                            onValueChange = { playlistSearchQuery = it },
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

                    if (playlistSearchQuery.isNotEmpty()) {
                        TactileBox(
                            onClick = { playlistSearchQuery = "" },
                            pressedScale = 0.85f,
                            modifier = Modifier.size(30.dp).clip(CircleShape)
                        ) {
                            GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // "+ New" Playlist Button
                    TactileBox(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCreatePlaylistClick()
                        },
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .glassMorphic(
                                backgroundColor = accent.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphAdd(color = accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "New",
                                color = accent,
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Playlist Count
                Text(
                    text = "${filteredPlaylists.size} Playlists",
                    color = m3OnSurfaceVariant(),
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Cards List
                if (filteredPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlyphPlaylists(color = m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (playlistSearchQuery.isNotEmpty()) "No matching playlists" else "No playlists created yet",
                                color = m3OnSurfaceVariant(),
                                fontSize = 15.sp,
                                fontFamily = ModernSansFont
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 168.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredPlaylists, key = { it.id }) { playlist ->
                            val isPlayingThis = activePlayingPlaylistId == playlist.id
                            val firstTrackPath = playlist.trackPaths.firstOrNull() ?: ""

                            val baseCardSurface = com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh().copy(alpha = 0.85f)
                            val playlistCardBgColor = if (isPlayingThis) {
                                com.yuka.musicplayer.ui.theme.resolveM3TonalSurface(
                                    baseSurface = baseCardSurface,
                                    accent = accent,
                                    tintRatio = 0.28f,
                                    alpha = 0.85f
                                )
                            } else {
                                baseCardSurface
                            }

                            // Glassmorphic Playlist Card (0.85 opacity)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassMorphic(
                                        backgroundColor = playlistCardBgColor,
                                        borderColor = if (isPlayingThis) accent.copy(alpha = 0.35f) else com.yuka.musicplayer.ui.theme.m3GlassBorder(),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        activeDetailPlaylistId = playlist.id
                                        onSelectPlaylistTab(playlist.id)
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail / Artwork
                                TrackAlbumThumbnail(
                                    filePath = firstTrackPath,
                                    isCurrentTrack = isPlayingThis,
                                    currentTrackArt = if (isPlayingThis) currentTrack?.coverArt else null,
                                    modifier = Modifier.size(52.dp),
                                    cornerRadius = 14.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isPlayingThis) accent.copy(alpha = 0.25f) else com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        GlyphPlaylists(color = accent, modifier = Modifier.size(24.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Playlist Info Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = playlist.name,
                                            color = if (isPlayingThis) accent else m3OnSurface(),
                                            fontSize = 15.sp,
                                            fontFamily = ModernSansFont,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (playlist.isFolderPlaylist) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(accent.copy(alpha = 0.18f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    GlyphFolder(color = accent, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "FOLDER",
                                                        color = accent,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = ModernSansFont
                                                    )
                                                }
                                            }
                                        }
                                        if (playlist.isPinned) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            GlyphPin(
                                                color = accent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        if (isPlayingThis) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(accent.copy(alpha = 0.25f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "PLAYING",
                                                    color = accent,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = ModernSansFont
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${playlist.trackPaths.size} tracks",
                                        color = m3OnSurfaceVariant(),
                                        fontSize = 12.sp,
                                        fontFamily = ModernSansFont
                                    )
                                }

                                // Quick Actions: Pin, Rename & Delete
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TactileBox(
                                        onClick = { onTogglePinPlaylist(playlist) },
                                        pressedScale = 0.85f,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (playlist.isPinned) accent.copy(alpha = 0.18f) else Color.Transparent)
                                    ) {
                                        GlyphPin(
                                            color = if (playlist.isPinned) accent else m3OnSurfaceVariant(),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    if (!playlist.isFolderPlaylist) {
                                        TactileBox(
                                            onClick = { onRenamePlaylistClick(playlist) },
                                            pressedScale = 0.88f,
                                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                        ) {
                                            GlyphClipboard(color = m3OnSurfaceVariant(), modifier = Modifier.size(16.dp))
                                        }

                                        TactileBox(
                                            onClick = { onDeletePlaylistClick(playlist) },
                                            pressedScale = 0.88f,
                                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                        ) {
                                            GlyphDelete(color = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // LEVEL 2: PLAYLIST DETAIL VIEW
            // ==========================================
            val playlist = playlistTarget
            val currentTracks = playlist.trackPaths
            val isCurrentPlaying = activePlayingPlaylistId == playlist.id

            val filteredTracks = remember(currentTracks, trackSearchQuery) {
                if (trackSearchQuery.isEmpty()) {
                    currentTracks
                } else {
                    currentTracks.filter { path ->
                        val file = File(path)
                        val name = file.nameWithoutExtension.ifEmpty { file.name }
                        FuzzySearchHelper.matchesAny(trackSearchQuery, name, path)
                    }
                }
            }

            val detailEnterAnim = remember { Animatable(0f) }
            LaunchedEffect(playlist.id) {
                detailEnterAnim.snapTo(0f)
                detailEnterAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.74f, stiffness = 340f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = (1f - detailEnterAnim.value) * 24.dp.toPx()
                        alpha = detailEnterAnim.value.coerceIn(0f, 1f)
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Detail Header with Back Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TactileBox(
                        onClick = {
                            activeDetailPlaylistId = null
                            trackSearchQuery = ""
                            focusManager.clearFocus()
                        },
                        pressedScale = 0.90f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .glassMorphic(
                                backgroundColor = com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphArrowBack(color = accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Playlists",
                                color = m3OnSurface(),
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playlist.name,
                                color = m3OnSurface(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ModernSansFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (playlist.isFolderPlaylist) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GlyphFolder(color = accent, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "FOLDER",
                                            color = accent,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = ModernSansFont
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "${currentTracks.size} tracks",
                            color = m3OnSurfaceVariant(),
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont
                        )
                    }

                    // Rename & Clear Quick Options
                    if (!playlist.isFolderPlaylist) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (currentTracks.isNotEmpty()) {
                                TactileBox(
                                    onClick = { onClearPlaylist(playlist.id) },
                                    pressedScale = 0.88f,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(accent.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("CLEAR", color = accent, fontSize = 9.sp, fontFamily = ModernSansFont, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            TactileBox(
                                onClick = { onRenamePlaylistClick(playlist) },
                                pressedScale = 0.88f,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("RENAME", color = accent, fontSize = 9.sp, fontFamily = ModernSansFont, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 2 HERO BUTTONS: PLAY (SEQUENTIAL) & SHUFFLE
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Play Button (Sequential from start)
                    TactileBox(
                        onClick = {
                            if (currentTracks.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayPlaylistSequential(playlist)
                            }
                        },
                        pressedScale = 0.94f,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .glassMorphic(
                                backgroundColor = accent.copy(alpha = 0.22f),
                                borderColor = accent.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphPlay(color = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Play",
                                color = accent,
                                fontSize = 14.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 2. Shuffle Button (Random)
                    TactileBox(
                        onClick = {
                            if (currentTracks.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayPlaylistShuffled(playlist)
                            }
                        },
                        pressedScale = 0.94f,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .glassMorphic(
                                backgroundColor = com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh(),
                                borderColor = com.yuka.musicplayer.ui.theme.m3GlassBorder(),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphShuffle(color = m3OnSurface(), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shuffle",
                                color = m3OnSurface(),
                                fontSize = 14.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Transport Quick Strip (Repeat, Queue)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isRepActive = repeatMode != RepeatMode.OFF
                    TactileBox(
                        onClick = onCycleRepeat,
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .glassMorphic(
                                backgroundColor = if (isRepActive) accent.copy(alpha = 0.22f) else com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f),
                                shape = RoundedCornerShape(50)
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphRepeat(color = if (isRepActive) accent else m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val repText = when (repeatMode) {
                                RepeatMode.OFF -> "Repeat"
                                RepeatMode.ALL -> "Repeat All"
                                RepeatMode.ONE -> "Repeat One"
                            }
                            Text(
                                text = repText,
                                color = if (isRepActive) accent else m3OnSurfaceVariant(),
                                fontSize = 11.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    TactileBox(
                        onClick = onOpenQueue,
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .glassMorphic(
                                backgroundColor = if (priorityQueueSize > 0) accent.copy(alpha = 0.22f) else com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f),
                                shape = RoundedCornerShape(50)
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphQueue(color = if (priorityQueueSize > 0) accent else m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Queue ($priorityQueueSize)",
                                color = if (priorityQueueSize > 0) accent else m3OnSurfaceVariant(),
                                fontSize = 11.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar inside Playlist
                if (currentTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.06f))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlyphSearch(color = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (trackSearchQuery.isEmpty()) {
                                Text(
                                    text = "Search tracks in playlist...",
                                    color = m3OnSurfaceVariant().copy(alpha = 0.60f),
                                    fontSize = 13.sp,
                                    fontFamily = ModernSansFont
                                )
                            }
                            BasicTextField(
                                value = trackSearchQuery,
                                onValueChange = { trackSearchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    color = m3OnSurface(),
                                    fontSize = 13.sp,
                                    fontFamily = ModernSansFont
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(accent)
                            )
                        }

                        if (trackSearchQuery.isNotEmpty()) {
                            TactileBox(
                                onClick = { trackSearchQuery = "" },
                                pressedScale = 0.85f,
                                modifier = Modifier.size(26.dp).clip(CircleShape)
                            ) {
                                GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Track list inside playlist
                if (currentTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlyphMusicNote(color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant().copy(alpha = 0.40f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "This playlist is empty",
                                color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                                fontSize = 14.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select tracks in Library then tap 'Add to playlist'",
                                color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont
                            )
                        }
                    }
                } else if (filteredTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching tracks",
                            color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 168.dp)
                    ) {
                        items(filteredTracks, key = { it }) { path ->
                            val file = File(path)
                            val isPlaying = file.absolutePath == currentTrack?.file?.absolutePath && isCurrentPlaying
                            val displayName = file.nameWithoutExtension.ifEmpty { file.name }
                            val (parsedTitle, parsedArtist) = remember(displayName) {
                                TrackTitleParser.parse(displayName)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isPlaying) accent.copy(alpha = 0.12f) else Color.Transparent)
                                    .combinedClickable(
                                        onClick = { onFileSelected(file, playlist.id) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTrackLongPressed(file)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackAlbumThumbnail(
                                    filePath = file.absolutePath,
                                    isCurrentTrack = isPlaying,
                                    currentTrackArt = if (isPlaying) currentTrack?.coverArt else null,
                                    modifier = Modifier.size(46.dp),
                                    cornerRadius = 12.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isPlaying) accent.copy(alpha = 0.22f) else com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPlaying) {
                                            GlyphNowPlaying(color = accent, modifier = Modifier.size(20.dp))
                                        } else {
                                            GlyphMusicNote(color = m3OnSurfaceVariant(), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = parsedTitle,
                                        color = if (isPlaying) accent else m3OnSurface(),
                                        fontFamily = ModernSansFont,
                                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val secondary = if (parsedArtist.isNotEmpty()) {
                                        "$parsedArtist • ${file.extension.uppercase()}"
                                    } else file.extension.uppercase()
                                    Text(
                                        text = secondary,
                                        color = if (isPlaying) accent.copy(alpha = 0.80f) else m3OnSurfaceVariant(),
                                        fontSize = 12.sp,
                                        fontFamily = ModernSansFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!playlist.isFolderPlaylist) {
                                    // Quick remove from playlist button
                                    TactileBox(
                                        onClick = { onFileRemoved(path, playlist.id) },
                                        pressedScale = 0.85f,
                                        modifier = Modifier.size(32.dp).clip(CircleShape)
                                    ) {
                                        GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                                    }
                                }

                                // 3-dot options
                                TactileBox(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTrackLongPressed(file)
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
            }
        }
    }
}
