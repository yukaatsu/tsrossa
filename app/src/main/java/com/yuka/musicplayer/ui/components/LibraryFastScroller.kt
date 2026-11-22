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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.m3SurfaceBase
import kotlin.math.roundToInt

@Composable
fun LibraryFastScroller(
    lazyListState: LazyListState,
    itemCount: Int,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    letterProvider: ((Int) -> String)? = null
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

    // Live scroll position calculation when not dragging (smooth sub-item interpolation)
    val derivedScrollProgress by remember(itemCount) {
        derivedStateOf {
            if (itemCount <= 1) 0f
            else {
                val firstIdx = lazyListState.firstVisibleItemIndex
                val firstItemSize = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                val subItemFraction = if (firstItemSize > 0) {
                    (lazyListState.firstVisibleItemScrollOffset.toFloat() / firstItemSize.toFloat()).coerceIn(0f, 1f)
                } else 0f
                ((firstIdx.toFloat() + subItemFraction) / (itemCount - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    // Letter is ONLY evaluated when dragging, preventing recomposition storms during regular scroll
    val currentLetter by remember(letterProvider, isDragging, itemCount) {
        derivedStateOf {
            if (!isDragging || letterProvider == null || itemCount <= 0) ""
            else {
                val p = dragProgress.coerceIn(0f, 1f)
                val targetIndex = (p * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
                letterProvider(targetIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .onGloballyPositioned { coordinates ->
                barHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(itemCount, barHeightPx) {
                val maxScrollable = (barHeightPx - thumbHeightPx).coerceAtLeast(1f)
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val p = ((offset.y - (thumbHeightPx / 2f)) / maxScrollable).coerceIn(0f, 1f)
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
                        val p = ((change.position.y - (thumbHeightPx / 2f)) / maxScrollable).coerceIn(0f, 1f)
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

        // Active thumb pill: uses layout-phase offset lambda so scrolling never triggers Composition
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset {
                    val p = if (isDragging) dragProgress else derivedScrollProgress
                    val maxScrollable = (barHeightPx - thumbHeightPx).coerceAtLeast(0f)
                    val offsetPx = (p * maxScrollable).roundToInt()
                    IntOffset(0, offsetPx)
                }
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
                .offset {
                    val maxScrollable = (barHeightPx - thumbHeightPx).coerceAtLeast(0f)
                    val offsetPx = (dragProgress * maxScrollable).roundToInt()
                    val bubbleOffsetPx = (offsetPx - with(density) { 12.dp.roundToPx() }).coerceAtLeast(0)
                    IntOffset(with(density) { (-46).dp.roundToPx() }, bubbleOffsetPx)
                }
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
