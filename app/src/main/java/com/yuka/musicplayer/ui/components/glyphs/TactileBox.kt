package com.yuka.musicplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Ultra-responsive tactile touch wrapper:
 * - Uses fast single-tap Modifier.clickable when onLongClick == null (zero gesture disambiguation lag).
 * - Animates GPU graphicsLayer scale immediately on finger touch-down (ACTION_DOWN) via collectIsPressedAsState().
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TactileBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.93f,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val effectiveTargetScale = if (isPressed && enabled) {
        if (pressedScale < 0.999f) pressedScale else 0.93f
    } else {
        1.0f
    }
    val animatedScale by animateFloatAsState(
        targetValue = effectiveTargetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 900f),
        label = "tactilePressScale"
    )

    val clickModifier = if (currentOnLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = { currentOnClick() },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongClick?.invoke()
            }
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = { currentOnClick() }
        )
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .then(modifier)
            .then(clickModifier)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content
    )
}
