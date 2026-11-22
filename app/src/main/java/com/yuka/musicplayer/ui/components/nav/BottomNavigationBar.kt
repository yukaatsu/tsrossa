package com.yuka.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant

/**
 * Authentic Material 3 Glassmorphic NavigationBar:
 * - Contains Library and Playlist (Settings moved to Top App Banner).
 * - Full glassmorphism (opacity = 0.85f + accent tint).
 * - Long-press hold reveals floating tooltip with menu name.
 */
@Composable
fun BottomNavigationBar(
    currentView: ViewState,
    @Suppress("UNUSED_PARAMETER") bgMode: String = "SIGNATURE",
    onNavClick: (ViewState) -> Unit
) {
    val items = listOf(
        ViewState.LIBRARY to "Library",
        ViewState.PLAYLIST to "Playlist"
    )

    val accent = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current
    val navBarBg = com.yuka.musicplayer.ui.theme.m3SurfaceContainer().copy(alpha = 1f)

    var heldItem by remember { mutableStateOf<ViewState?>(null) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .glassMorphic(
                backgroundColor = navBarBg,
                borderColor = Color.Unspecified,
                shape = RoundedCornerShape(26.dp),
                borderWidth = 0.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (view, label) ->
                val isSelected = currentView == view

                val tabScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.06f else 0.94f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
                    label = "tabScale"
                )
                val iconBounceY by animateFloatAsState(
                    targetValue = if (isSelected) -2f else 0f,
                    animationSpec = spring(dampingRatio = 0.50f, stiffness = 500f),
                    label = "iconBounceY"
                )
                val pillWidthFraction by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.85f,
                    animationSpec = spring(dampingRatio = 0.60f, stiffness = 480f),
                    label = "pillWidthFraction"
                )
                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) com.yuka.musicplayer.ui.theme.m3ActivePillContainer() else Color.Transparent,
                    animationSpec = tween(durationMillis = 220),
                    label = "pillBgColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Floating Tooltip Popup directly centered above the held tab item
                    if (heldItem == view) {
                        androidx.compose.ui.window.Popup(
                            alignment = Alignment.TopCenter,
                            offset = androidx.compose.ui.unit.IntOffset(0, with(LocalDensity.current) { (-44).dp.roundToPx() }),
                            onDismissRequest = { heldItem = null }
                        ) {
                            Box(
                                modifier = Modifier
                                    .glassMorphic(
                                        backgroundColor = accent,
                                        shape = RoundedCornerShape(50),
                                        borderWidth = 0.dp
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.Black else Color.White
                                )
                            }
                        }
                    }
                    TactileBox(
                        onClick = {
                            heldItem = null
                            onNavClick(view)
                        },
                        onLongClick = {
                            heldItem = view
                        },
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(30.dp)
                                    .graphicsLayer {
                                        scaleX = tabScale * pillWidthFraction
                                        scaleY = tabScale
                                    }
                                    .clip(RoundedCornerShape(50))
                                    .background(pillBgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconColor = if (isSelected) accent else m3OnSurfaceVariant()
                                Box(modifier = Modifier.graphicsLayer { translationY = iconBounceY }) {
                                    when (view) {
                                        ViewState.LIBRARY -> GlyphLibrary(color = iconColor, modifier = Modifier.size(20.dp))
                                        ViewState.PLAYLIST -> GlyphPlaylists(color = iconColor, modifier = Modifier.size(20.dp))
                                        else -> GlyphNowPlaying(color = iconColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = label,
                                color = if (isSelected) accent else m3OnSurfaceVariant(),
                                fontSize = 11.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
