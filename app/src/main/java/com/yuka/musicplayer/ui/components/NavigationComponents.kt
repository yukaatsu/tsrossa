package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.ui.theme.blendWithWhite

@Composable
fun AppHeader(
    viewState: ViewState,
    isHardwareVolumeActive: Boolean,
    isForceSoftwareVolume: Boolean,
    isHardwareVolumeLockedBySystem: Boolean,
    onToggleLogs: () -> Unit,
    onToggleUpdate: () -> Unit,
    hasUpdateAvailable: Boolean = false,
    onWarmupClick: () -> Unit = {},
    isWarmingUp: Boolean = false,
    onToggleLockTask: () -> Unit = {},
    isTaskLocked: Boolean = false
) {
    val accent = LocalAccentColor.current
    val viewLabel = when (viewState) {
        ViewState.LIBRARY -> "FILE EXPLORER"
        ViewState.PLAYLIST -> "CURRENT PLAYLIST"
        ViewState.TRACK -> "AUDIO PLAYER ENGINE"
        ViewState.SETTINGS -> "SYSTEM CONFIG"
    }

    val (hwVolumeText, statusColor) = when {
        isHardwareVolumeLockedBySystem -> "100% BIT-PERFECT" to Color(0xFFFF9100)
        isForceSoftwareVolume -> "64-BIT DITHERED SW" to Color(0xFFFF5252)
        isHardwareVolumeActive -> "32-BIT HARDWARE CHIP" to Color(0xFFFFD700)
        else -> "BIT-PERFECT" to accent.copy(alpha = 0.9f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Row 1: Brand & Current View + Tactile Action Cluster
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Clean Typographic Branding
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TSROSSA",
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 2.sp,
                        color = TerminalWhite
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HI-RES",
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = accent,
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                            .border(0.6.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "// $viewLabel",
                    color = TerminalGray,
                    fontFamily = TerminalFont,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.8.sp
                )
            }

            // Right: Unified Tactile Action Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: LOGS
                TactileBox(
                    onClick = onToggleLogs,
                    pressedScale = 0.92f,
                    modifier = Modifier
                        .background(Color(0xFF0F1524), RoundedCornerShape(6.dp))
                        .border(0.8.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LOGS",
                            color = accent,
                            fontSize = 9.5.sp,
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 2: UPDATES
                TactileBox(
                    onClick = onToggleUpdate,
                    pressedScale = 0.92f,
                    modifier = Modifier
                        .background(
                            if (hasUpdateAvailable) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF0F1524),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            0.8.dp,
                            if (hasUpdateAvailable) Color(0xFF00E676).copy(alpha = 0.8f) else accent.copy(alpha = 0.25f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasUpdateAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (hasUpdateAvailable) "NEW" else "UPD",
                            color = if (hasUpdateAvailable) Color(0xFF00E676) else accent,
                            fontSize = 9.5.sp,
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 3: WARMUP DAC
                TactileBox(
                    onClick = onWarmupClick,
                    pressedScale = 0.92f,
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (isWarmingUp) Color(0xFF00E5FF).copy(alpha = 0.20f) else Color(0xFF0F1524),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            0.8.dp,
                            if (isWarmingUp) Color(0xFF00E5FF) else accent.copy(alpha = 0.25f),
                            RoundedCornerShape(6.dp)
                        ),
                    content = {
                        Text(
                            text = "⚡",
                            color = if (isWarmingUp) Color(0xFF00E5FF) else TerminalWhite.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                )

                // Button 4: SCREEN PINNING / LOCK
                TactileBox(
                    onClick = onToggleLockTask,
                    pressedScale = 0.92f,
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (isTaskLocked) Color(0xFFFFB300).copy(alpha = 0.20f) else Color(0xFF0F1524),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            0.8.dp,
                            if (isTaskLocked) Color(0xFFFFB300) else accent.copy(alpha = 0.25f),
                            RoundedCornerShape(6.dp)
                        ),
                    content = {
                        Text(
                            text = if (isTaskLocked) "🔒" else "🔓",
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Status Bar (Hardware Bit-Perfect Status Strip)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B101D), RoundedCornerShape(5.dp))
                .border(0.6.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hintText = when (viewState) {
                ViewState.LIBRARY -> "TAP: PLAY · HOLD: ACTIONS"
                ViewState.PLAYLIST -> "TAP: PLAY · QUEUED ORDER"
                ViewState.TRACK -> "PRECISION 64-BIT DSP"
                ViewState.SETTINGS -> "DEVICE & AUDIO PARAMS"
            }
            Text(
                text = hintText,
                fontFamily = TerminalFont,
                fontSize = 9.sp,
                color = TerminalGray,
                letterSpacing = 0.4.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "DAC: $hwVolumeText",
                    fontFamily = TerminalFont,
                    fontSize = 9.sp,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    Divider(color = accent.copy(alpha = 0.18f), thickness = 0.8.dp)
}

@Composable
fun MiniPlayerView(
    track: TrackInfo,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicColor = LocalAccentColor.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clickable { onNavigateToNowPlaying() }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C101C))
            .border(0.8.dp, dynamicColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modifierImage = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141A28))
            
        if (track.coverArt != null) {
            Image(
                bitmap = track.coverArt.asImageBitmap(),
                contentDescription = "Cover",
                modifier = modifierImage,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = modifierImage,
                contentAlignment = Alignment.Center
            ) {
                Text("♪", color = dynamicColor, fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = TerminalWhite,
                fontSize = 11.5.sp,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = dynamicColor.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = TerminalFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        TactileBox(
            onClick = onTogglePlay,
            pressedScale = 0.88f,
            modifier = Modifier
                .size(32.dp)
                .background(dynamicColor, CircleShape)
        ) {
            val iconColor = Color(0xFF070A12)
            if (isPlaying) {
                GlyphPause(color = iconColor, modifier = Modifier.size(14.dp))
            } else {
                Box(modifier = Modifier.padding(start = 2.dp)) {
                    GlyphPlay(color = iconColor, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(currentView: ViewState, onNavClick: (ViewState) -> Unit) {
    val items = listOf(
        ViewState.LIBRARY to "LIBRARY",
        ViewState.PLAYLIST to "PLAYLIST",
        ViewState.TRACK to "PLAYER",
        ViewState.SETTINGS to "CONFIG"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF070A12))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (view, label) ->
                val isSelected = currentView == view
                TactileBox(
                    onClick = { onNavClick(view) },
                    pressedScale = 0.94f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) LocalAccentColor.current.copy(alpha = 0.12f) else Color.Transparent
                        )
                        .border(
                            width = 0.7.dp,
                            color = if (isSelected) LocalAccentColor.current.copy(alpha = 0.40f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 9.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) LocalAccentColor.current else TerminalGray.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = TerminalFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 12.dp, height = 2.dp)
                                    .background(LocalAccentColor.current, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
