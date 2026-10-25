package com.yuka.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.m3SurfaceBase

@Composable
fun LibraryFastScroller(
    lazyListState: LazyListState,
    itemCount: Int,
    currentLetter: String,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (itemCount <= 5) return

    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(1f) }

    val thumbHeightDp = 28.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    // Live scroll position calculation when not dragging
    val derivedScrollProgress by remember(itemCount) {
        derivedStateOf {
            if (itemCount <= 1) 0f
            else {
                val firstIdx = lazyListState.firstVisibleItemIndex
                (firstIdx.toFloat() / (itemCount - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val activeProgress = if (isDragging) dragProgress else derivedScrollProgress

    // Calculate vertical offset ensuring thumb never overflows
    val maxScrollableDistancePx = (barHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetPx = activeProgress * maxScrollableDistancePx
    val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .onGloballyPositioned { coordinates ->
                barHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(itemCount, barHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val p = ((offset.y - (thumbHeightPx / 2f)) / maxScrollableDistancePx).coerceIn(0f, 1f)
                        dragProgress = p
                        val targetIndex = (p * (itemCount - 1)).toInt()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onScrollToIndex(targetIndex)
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val p = ((change.position.y - (thumbHeightPx / 2f)) / maxScrollableDistancePx).coerceIn(0f, 1f)
                        dragProgress = p
                        val targetIndex = (p * (itemCount - 1)).toInt()
                        onScrollToIndex(targetIndex)
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // Vertical guide track (centered horizontally in 16.dp width)
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(m3SubtleFill(alpha = if (isDragging) 0.25f else 0.12f))
        )

        // Active thumb pill (centered horizontally in 16.dp width, 6.dp wide)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffsetDp)
                .size(width = 6.dp, height = thumbHeightDp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isDragging) accent else m3OnSurface().copy(alpha = 0.40f))
        )

        // Floating Glassmorphic Letter Bubble following thumb position
        AnimatedVisibility(
            visible = isDragging && currentLetter.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = (-46).dp,
                    y = (thumbOffsetDp - 12.dp).coerceAtLeast(0.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .requiredSize(50.dp)
                    .glassMorphic(
                        backgroundColor = m3SurfaceBase().copy(alpha = 0.92f),
                        borderColor = accent.copy(alpha = 0.40f),
                        borderWidth = 1.2.dp,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentLetter,
                    color = accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont
                )
            }
        }
    }
}
