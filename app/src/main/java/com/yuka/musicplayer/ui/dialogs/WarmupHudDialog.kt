package com.yuka.musicplayer.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuka.musicplayer.ui.components.GlyphBitPerfect
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite
import kotlinx.coroutines.delay

/**
 * Top Dynamic Capsule Banner for DAC Warmup & Status.
 * Replaces the full-screen blocking modal with an elegant floating dynamic status capsule.
 */
@Composable
fun WarmupHudDialog(
    dacName: String,
    onWarmupFinished: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var isDone by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(Unit) {
        val totalDurationMs = 1600L
        val intervalMs = 40L
        val totalTicks = totalDurationMs / intervalMs
        for (i in 1..totalTicks) {
            delay(intervalMs)
            progress = (i.toFloat() / totalTicks).coerceIn(0f, 1f)
        }
        isDone = true
        delay(400) // brief display of completed state
        onWarmupFinished()
    }

    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(focusable = false)
    ) {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 20.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(20.dp))
                .background(m3SurfaceContainer())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isDone) LocalAccentColor.current else LocalAccentColor.current.copy(alpha = pulseAlpha),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isDone) "DAC Ready · Bit-Perfect" else "Initializing DAC · Priming FIFO",
                                color = TerminalWhite,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = dacName.ifBlank { "USB Audio Device" },
                                color = Color.White.copy(alpha = 0.6f),
                                fontFamily = ModernSansFont,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = LocalAccentColor.current,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // M3 Smooth Progress Pill Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(LocalAccentColor.current)
                    )
                }
            }
        }
    }
}
