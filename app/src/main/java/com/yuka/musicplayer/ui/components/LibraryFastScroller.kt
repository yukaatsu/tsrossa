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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.m3SurfaceBase

/**
 * Pure calculation of normalized [0f..1f] scroll progress for [LibraryFastScroller].
 * Accounts for sub-item pixel offset and the number of items fitting in the usable viewport
 * so the scrollbar thumb smoothly reaches 1.0f when the user scrolls to the bottom of the list.
 */
internal fun calculateFastScrollerProgress(
    itemCount: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
    rowItemSize: Int,
    usableViewportHeightPx: Int
): Float {
    if (itemCount <= 1) return 0f
    val subItemFraction = if (firstVisibleItemSize > 0) {
        (firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val effectiveRowSize = if (rowItemSize > 0) rowItemSize else firstVisibleItemSize
    val visibleItemsSpan = if (effectiveRowSize > 0 && usableViewportHeightPx > 0) {
        (usableViewportHeightPx.toFloat() / effectiveRowSize.toFloat()).coerceAtLeast(1f)
    } else {
        1f
    }
    val maxFirstIndexFloat = (itemCount.toFloat() - visibleItemsSpan).coerceAtLeast(1f)
    val currentItemPosFloat = (firstVisibleItemIndex.toFloat() + subItemFraction).coerceAtLeast(0f)
    return (currentItemPosFloat / maxFirstIndexFloat).coerceIn(0f, 1f)
}

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

    // Live scroll position calculation when not dragging (deferred to Layout/Draw phase -> 0 recompositions/px!)
    val derivedScrollProgressState = remember(lazyListState, itemCount) {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val firstItemSize = visibleItems.firstOrNull()?.size ?: 0
            val rowItemSize = visibleItems.maxOfOrNull { it.size } ?: firstItemSize
            val usableViewportHeightPx = (
                layoutInfo.viewportEndOffset -
                    layoutInfo.viewportStartOffset -
                    layoutInfo.afterContentPadding -
                    layoutInfo.beforeContentPadding
                ).coerceAtLeast(1)
            calculateFastScrollerProgress(
                itemCount = itemCount,
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset,
                firstVisibleItemSize = firstItemSize,
                rowItemSize = rowItemSize,
                usableViewportHeightPx = usableViewportHeightPx
            )
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
                        val targetIndex = (p * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
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
                        val targetIndex = (p * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
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

        // Active thumb pill (centered horizontally in 16.dp width, 6.dp wide) — translated on RenderThread!
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val p = if (isDragging) dragProgress else derivedScrollProgressState.value
                    val maxDist = (barHeightPx - thumbHeightPx).coerceAtLeast(0f)
                    translationY = (p.coerceIn(0f, 1f) * maxDist).coerceIn(0f, maxDist)
                }
                .size(width = 6.dp, height = thumbHeightDp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isDragging) accent else m3OnSurface().copy(alpha = 0.40f))
        )

        // Floating Glassmorphic Letter Bubble following thumb position (Placement-phase offset)
        AnimatedVisibility(
            visible = isDragging && currentLetter.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val p = if (isDragging) dragProgress else derivedScrollProgressState.value
                    val maxDist = (barHeightPx - thumbHeightPx).coerceAtLeast(0f)
                    val yPx = ((p.coerceIn(0f, 1f) * maxDist) - 12.dp.toPx()).coerceAtLeast(0f)
                    IntOffset((-46).dp.roundToPx(), yPx.roundToInt())
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
