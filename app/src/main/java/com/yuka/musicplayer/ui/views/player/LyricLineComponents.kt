package com.yuka.musicplayer.ui.views

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.util.LyricLine
import com.yuka.musicplayer.util.VoiceType

/**
 * Apple Music style breathing dots (• • •) indicating instrumental intro or mid-song breaks.
 */
@Composable
internal fun InterludeBreathingDots(
    dynamicColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsBreathing")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(750, delayMillis = 200, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(750, delayMillis = 400, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { scaleX = dot1Scale; scaleY = dot1Scale }
                .background(dynamicColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { scaleX = dot2Scale; scaleY = dot2Scale }
                .background(dynamicColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { scaleX = dot3Scale; scaleY = dot3Scale }
                .background(dynamicColor, CircleShape)
        )
    }
}

/**
 * Word-by-word karaoke line with Apple Music style horizontal fill, micro-lift, and duet alignment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordSyncedLyricLine(
    line: LyricLine,
    smoothPosition: Double,
    isActive: Boolean,
    dynamicColor: Color,
    onClick: () -> Unit
) {
    val alignment = when (line.voiceType) {
        VoiceType.SECONDARY -> Alignment.End
        else -> Alignment.Start
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp, horizontal = 8.dp),
        horizontalAlignment = alignment
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = when (line.voiceType) {
                VoiceType.SECONDARY -> Arrangement.End
                else -> Arrangement.Start
            },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (word in line.words) {
                val duration = (word.endTimeSec - word.startTimeSec).coerceAtLeast(0.08)
                val wordProgress = ((smoothPosition - word.startTimeSec) / duration).toFloat().coerceIn(0f, 1f)
                val isCurrentlySinging = isActive && wordProgress > 0f && wordProgress < 1f
                val isPast = wordProgress >= 1f

                val liftY = if (isCurrentlySinging) -2f else 0f

                val textColor = when {
                    !isActive -> m3OnSurface().copy(alpha = 0.40f)
                    isPast -> Color.White
                    isCurrentlySinging -> Color.White
                    else -> Color.White.copy(alpha = 0.35f)
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = liftY
                        }
                        .padding(end = 4.dp)
                ) {
                    if (isCurrentlySinging) {
                        Text(
                            text = word.text,
                            style = TextStyle(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.White,
                                    wordProgress to Color.White,
                                    (wordProgress + 0.06f).coerceAtMost(1f) to Color.White.copy(alpha = 0.35f),
                                    1.0f to Color.White.copy(alpha = 0.35f)
                                ),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ModernSansFont
                            )
                        )
                    } else {
                        Text(
                            text = word.text,
                            color = textColor,
                            fontSize = if (isActive) 21.sp else 18.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                    }
                }
            }
        }

        if (!line.backgroundText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "(${line.backgroundText})",
                color = if (isActive) dynamicColor.copy(alpha = 0.85f) else m3OnSurfaceVariant().copy(alpha = 0.40f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ModernSansFont
            )
        }
    }
}

/**
 * Standard line-synced lyric line for songs with line-level timestamps.
 */
@Composable
internal fun StandardLyricLine(
    line: LyricLine,
    isActive: Boolean,
    dynamicColor: Color,
    onClick: () -> Unit
) {
    val alignment = when (line.voiceType) {
        VoiceType.SECONDARY -> Alignment.End
        else -> Alignment.Start
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp, horizontal = 8.dp),
        horizontalAlignment = alignment
    ) {
        Text(
            text = line.text.ifEmpty { "♪" },
            color = if (isActive) Color.White else m3OnSurface().copy(alpha = 0.40f),
            fontSize = if (isActive) 21.sp else 18.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
            fontFamily = ModernSansFont,
            textAlign = when (line.voiceType) {
                VoiceType.SECONDARY -> TextAlign.End
                else -> TextAlign.Start
            }
        )

        if (!line.backgroundText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "(${line.backgroundText})",
                color = if (isActive) dynamicColor.copy(alpha = 0.85f) else m3OnSurfaceVariant().copy(alpha = 0.40f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ModernSansFont
            )
        }
    }
}
