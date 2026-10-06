package com.yuka.musicplayer.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuka.musicplayer.ui.theme.LocalAccentColor
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
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
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
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(0.92f)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF090D18))
                .border(
                    width = 0.9.dp,
                    color = if (isDone) LocalAccentColor.current else LocalAccentColor.current.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Flashing or solid indicator
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    color = if (isDone) LocalAccentColor.current else LocalAccentColor.current.copy(alpha = pulseAlpha),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDone) "DAC READY · BIT-PERFECT" else "DAC WARMUP · PRIMING FIFO",
                            color = if (isDone) LocalAccentColor.current else TerminalWhite,
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = LocalAccentColor.current,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Slim Progress Bar Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color(0xFF141A28), RoundedCornerShape(1.5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(LocalAccentColor.current, RoundedCornerShape(1.5.dp))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dacName.ifBlank { "BIT-PERFECT USB DAC" }.uppercase(),
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = TerminalFont,
                    fontSize = 8.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}
