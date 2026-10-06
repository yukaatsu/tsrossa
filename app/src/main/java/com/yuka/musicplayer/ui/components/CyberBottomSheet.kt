package com.yuka.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalWhite

/**
 * Modern Ergonomic Bottom Sheet for Dialogs, Panels, and Context Menus.
 * Slides gracefully from bottom, supports backdrop dismiss, top drag-handle, and rounded top corners.
 */
@Composable
fun CyberBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.88f,
    maxWidth: Dp = 600.dp,
    wrapHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        Popup(
            alignment = Alignment.BottomCenter,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissRequest() },
                contentAlignment = Alignment.BottomCenter
            ) {
                val boxMod = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .then(
                        if (wrapHeight) {
                            Modifier.fillMaxHeight(maxHeightFraction).wrapContentHeight(Alignment.Bottom)
                        } else {
                            Modifier.fillMaxHeight(maxHeightFraction)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {} // consume touch inside sheet
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFF090D18))
                    .border(
                        width = 0.8.dp,
                        color = LocalAccentColor.current.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )

                Box(modifier = boxMod) {
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .then(if (wrapHeight) Modifier.wrapContentHeight() else Modifier.fillMaxHeight())
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        // Top Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp)
                                .size(width = 38.dp, height = 4.dp)
                                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
                        )

                        content()
                    }
                }
            }
        }
    }
}
