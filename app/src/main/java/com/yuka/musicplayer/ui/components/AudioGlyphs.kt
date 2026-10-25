package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.TerminalWhite
import java.util.concurrent.ConcurrentHashMap

/**
 * Clean surface touch wrapper matching the Settings ripple effect (no circular halo/ring,
 * no artificial tap delay, full shape-bounded ripple with optional long-press support).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TactileBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") pressedScale: Float = 1.0f,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    currentOnClick()
                },
                onLongClick = if (currentOnLongClick != null) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongClick?.invoke()
                    }
                } else null
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Reusable, high-performance Eva Icons vector canvas.
 * Parses 24x24 SVG path strings once and caches the geometry in memory.
 */
private val evaPathCache = ConcurrentHashMap<String, Path>()

@Composable
fun EvaIconCanvas(
    svgPathData: String,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier.size(20.dp)
) {
    val basePath = remember(svgPathData) {
        evaPathCache.getOrPut(svgPathData) {
            PathParser().parsePathString(svgPathData).toPath()
        }
    }
    val scaledPath = remember { Path() }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        scaledPath.reset()
        scaledPath.addPath(basePath)
        matrix.reset()
        matrix.scale(size.width / 24f, size.height / 24f)
        scaledPath.transform(matrix)
        drawPath(path = scaledPath, color = color, style = Fill)
    }
}

// ==========================================
// OFFICIAL EVA ICONS (24x24 Vector Paths)
// https://akveo.github.io/eva-icons
// ==========================================

@Composable
fun GlyphPlay(color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    EvaIconCanvas(
        svgPathData = "M18.54 9L8.88 3.46a3.42 3.42 0 0 0-5.13 3v11.12A3.42 3.42 0 0 0 7.17 21a3.43 3.43 0 0 0 1.71-.46L18.54 15a3.42 3.42 0 0 0 0-6z",
        color = color,
        contentDescription = "Play",
        modifier = modifier
    )
}

@Composable
fun GlyphPause(color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    EvaIconCanvas(
        svgPathData = "M8.5 5A2.5 2.5 0 0 0 6 7.5v9A2.5 2.5 0 0 0 8.5 19h.5a2.5 2.5 0 0 0 2.5-2.5v-9A2.5 2.5 0 0 0 9 5zm6.5 0a2.5 2.5 0 0 0-2.5 2.5v9a2.5 2.5 0 0 0 2.5 2.5h.5a2.5 2.5 0 0 0 2.5-2.5v-9A2.5 2.5 0 0 0 15.5 5z",
        color = color,
        contentDescription = "Pause",
        modifier = modifier
    )
}

@Composable
fun GlyphPrev(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    EvaIconCanvas(
        svgPathData = "M16.66 5.25a2.77 2.77 0 0 0-2.92.26L9 9.18V7a1 1 0 0 0-2 0v10a1 1 0 0 0 2 0v-2.18l4.74 3.67a2.78 2.78 0 0 0 1.7.58a2.88 2.88 0 0 0 1.22-.28A2.77 2.77 0 0 0 18 16.31V7.69a2.77 2.77 0 0 0-1.34-2.44z",
        color = color,
        contentDescription = "Previous Track",
        modifier = modifier
    )
}

@Composable
fun GlyphNext(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    EvaIconCanvas(
        svgPathData = "M16 6a1 1 0 0 0-1 1v2.18l-4.74-3.67a2.77 2.77 0 0 0-2.92-.26A2.77 2.77 0 0 0 6 7.69v8.62a2.77 2.77 0 0 0 1.34 2.44a2.88 2.88 0 0 0 1.22.28a2.78 2.78 0 0 0 1.7-.58L15 14.82V17a1 1 0 0 0 2 0V7a1 1 0 0 0-1-1z",
        color = color,
        contentDescription = "Next Track",
        modifier = modifier
    )
}

@Composable
fun GlyphShuffle(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    EvaIconCanvas(
        svgPathData = "M18.71 14.29a1 1 0 0 0-1.42 1.42l.29.29H16a4 4 0 0 1-3.1-1.46L14.12 13l-1.24-1.54L11.65 13A6 6 0 0 1 7 15H4a1 1 0 0 0 0 2h3a8 8 0 0 0 6-2.7l.28-.33l.28.33A6 6 0 0 0 18 17h-.42l-.29.29a1 1 0 0 0 0 1.42a1 1 0 0 0 1.42 0l2-2a1 1 0 0 0 0-1.42zM18.71 5.29l-2-2a1 1 0 0 0-1.42 1.42l.29.29H16a6 6 0 0 0-4.65 2.2L10.12 8.7A6 6 0 0 0 5.5 7H4a1 1 0 0 0 0 2h1.5a4 4 0 0 1 3.1 1.46l3 3.74a1 1 0 0 0 1.56-1.25L10.88 10l1.24-1.54A4 4 0 0 1 15.22 7h2.36l-.29.29a1 1 0 0 0 1.42 1.42l2-2a1 1 0 0 0 0-1.42z",
        color = color,
        contentDescription = "Shuffle",
        modifier = modifier
    )
}

@Composable
fun GlyphRepeat(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    EvaIconCanvas(
        svgPathData = "M17.91 5.21l-2.25-2a1 1 0 0 0-1.32 1.5l.6.54H7a4 4 0 0 0-4 4v2a1 1 0 0 0 2 0v-2a2 2 0 0 1 2-2h7.86l-.52.46a1 1 0 0 0-.08 1.42a1 1 0 0 0 .75.34a1 1 0 0 0 .67-.26l2.25-2a1 1 0 0 0-.02-1.5zM20 11.75a1 1 0 0 0-1 1v2a2 2 0 0 1-2 2H9.14l.52-.46a1 1 0 0 0-1.32-1.5l-2.25 2a1 1 0 0 0 0 1.5l2.25 2a1 1 0 0 0 .66.25a1 1 0 0 0 .75-.34a1 1 0 0 0-.09-1.41l-.6-.54H17a4 4 0 0 0 4-4v-2a1 1 0 0 0-1-1z",
        color = color,
        contentDescription = "Toggle Repeat",
        modifier = modifier
    )
}

@Composable
fun GlyphBolt(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    EvaIconCanvas(
        svgPathData = "M11.11 23a1 1 0 0 1-.34-.06a1 1 0 0 1-.65-1.05l.77-7.09H5a1 1 0 0 1-.83-1.56l7.89-11.8a1 1 0 0 1 1.17-.38a1 1 0 0 1 .65 1l-.77 7.14H19a1 1 0 0 1 .83 1.56l-7.89 11.8a1 1 0 0 1-.83.44z",
        color = color,
        contentDescription = "Bolt",
        modifier = modifier
    )
}

@Composable
fun GlyphLock(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    EvaIconCanvas(
        svgPathData = "M17 8h-1V6.11a4 4 0 1 0-8 0V8H7a3 3 0 0 0-3 3v8a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-8a3 3 0 0 0-3-3zm-7-1.89A2.06 2.06 0 0 1 12 4a2.06 2.06 0 0 1 2 2.11V8h-4zM18 19a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1zm-6-5a2 2 0 1 0 2 2a2 2 0 0 0-2-2z",
        color = color,
        contentDescription = "Hardware Volume Locked",
        modifier = modifier
    )
}

@Composable
fun GlyphUnlock(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    EvaIconCanvas(
        svgPathData = "M17 8h-7V6.11a2 2 0 0 1 4 0a1 1 0 0 0 2 0a4 4 0 1 0-8 0V8H7a3 3 0 0 0-3 3v8a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-8a3 3 0 0 0-3-3zm1 11a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1zm-6-5a2 2 0 1 0 2 2a2 2 0 0 0-2-2z",
        color = color,
        contentDescription = "Hardware Volume Unlocked",
        modifier = modifier
    )
}

@Composable
fun GlyphClipboard(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    EvaIconCanvas(
        svgPathData = "M19 4h-1.18A3 3 0 0 0 15 2H9a3 3 0 0 0-2.82 2H5a3 3 0 0 0-3 3v12a3 3 0 0 0 3 3h14a3 3 0 0 0 3-3V7a3 3 0 0 0-3-3zM9 4h6a1 1 0 0 1 0 2H9a1 1 0 0 1 0-2zm11 15a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h1.18A3 3 0 0 0 9 8h6a3 3 0 0 0 2.82-2H19a1 1 0 0 1 1 1z",
        color = color,
        contentDescription = "Clipboard",
        modifier = modifier
    )
}

@Composable
fun GlyphWarning(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M22.56 16.3L14.89 3.58a3.43 3.43 0 0 0-5.78 0L1.44 16.3a3 3 0 0 0-.05 3A3.37 3.37 0 0 0 4.33 21h15.34a3.37 3.37 0 0 0 2.94-1.7a3 3 0 0 0-.05-3zM12 17a1 1 0 1 1 1-1a1 1 0 0 1-1 1zm1-4a1 1 0 0 1-2 0V9a1 1 0 0 1 2 0z",
        color = color,
        contentDescription = "Warning",
        modifier = modifier
    )
}

@Composable
fun GlyphSearch(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M20.71 19.29l-3.4-3.39A7.92 7.92 0 0 0 19 11a8 8 0 1 0-8 8a7.92 7.92 0 0 0 4.9-1.69l3.39 3.4a1 1 0 0 0 1.42 0a1 1 0 0 0 0-1.42zM5 11a6 6 0 1 1 6 6a6 6 0 0 1-6-6z",
        color = color,
        contentDescription = "Search",
        modifier = modifier
    )
}

@Composable
fun GlyphMoreVert(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M12 7a2 2 0 1 0-2-2a2 2 0 0 0 2 2zm0 10a2 2 0 1 0 2 2a2 2 0 0 0-2-2zm0-5a2 2 0 1 0 2 2a2 2 0 0 0-2-2z",
        color = color,
        contentDescription = "Options",
        modifier = modifier
    )
}

@Composable
fun GlyphFolder(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M19.5 7.05h-7L9.87 3.87a1 1 0 0 0-.77-.37H4.5A2.47 2.47 0 0 0 2 5.93v12.14A2.47 2.47 0 0 0 4.5 20.5h15a2.47 2.47 0 0 0 2.5-2.43V9.48a2.47 2.47 0 0 0-2.5-2.43zM20 18.07a.46.46 0 0 1-.5.43h-15a.46.46 0 0 1-.5-.43V5.93a.46.46 0 0 1 .5-.43h4.13l2.63 3.18a1 1 0 0 0 .77.37h7.47a.46.46 0 0 1 .5.43z",
        color = color,
        contentDescription = "Folder",
        modifier = modifier
    )
}

@Composable
fun GlyphRefresh(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M20.3 13.43a1 1 0 0 0-1.25.65A7.14 7.14 0 0 1 12.18 19A7.1 7.1 0 0 1 5 12a7.1 7.1 0 0 1 7.18-7a7.26 7.26 0 0 1 4.65 1.67l-2.17-.36a1 1 0 0 0-1.15.83a1 1 0 0 0 .83 1.15l4.24.7h.17a1 1 0 0 0 .34-.06a.33.33 0 0 0 .1-.06a.78.78 0 0 0 .2-.11l.09-.11c0-.05.09-.09.13-.15s0-.1.05-.14a1.34 1.34 0 0 0 .07-.18l.75-4a1 1 0 0 0-2-.38l-.27 1.45A9.21 9.21 0 0 0 12.18 3A9.1 9.1 0 0 0 3 12a9.1 9.1 0 0 0 9.18 9A9.12 9.12 0 0 0 21 14.68a1 1 0 0 0-.7-1.25z",
        color = color,
        contentDescription = "Refresh",
        modifier = modifier
    )
}

@Composable
fun GlyphSort(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M17.71 15.29a1 1 0 0 0-1.42 0L15 16.59V6a1 1 0 0 0-2 0v10.59l-1.29-1.3a1 1 0 0 0-1.42 1.42l3 3a1 1 0 0 0 1.42 0l3-3a1 1 0 0 0 0-1.42zm-6-8l-3-3a1 1 0 0 0-1.42 0l-3 3a1 1 0 0 0 1.42 1.42L7 7.41V18a1 1 0 0 0 2 0V7.41l1.29 1.3a1 1 0 0 0 1.42 0a1 1 0 0 0 0-1.42z",
        color = color,
        contentDescription = "Sort",
        modifier = modifier
    )
}

@Composable
fun GlyphQueue(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M7 7a1 1 0 1 0 1 1a1 1 0 0 0-1-1zm0 4a1 1 0 1 0 1 1a1 1 0 0 0-1-1zm0 4a1 1 0 1 0 1 1a1 1 0 0 0-1-1zm13-8h-9a1 1 0 0 0 0 2h9a1 1 0 0 0 0-2zm0 4h-9a1 1 0 0 0 0 2h9a1 1 0 0 0 0-2zm0 4h-9a1 1 0 0 0 0 2h9a1 1 0 0 0 0-2z",
        color = color,
        contentDescription = "Queue",
        modifier = modifier
    )
}

@Composable
fun GlyphAdd(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M19 11h-6V5a1 1 0 0 0-2 0v6H5a1 1 0 0 0 0 2h6v6a1 1 0 0 0 2 0v-6h6a1 1 0 0 0 0-2z",
        color = color,
        contentDescription = "Add",
        modifier = modifier
    )
}

@Composable
fun GlyphMinus(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M19 13H5a1 1 0 0 1 0-2h14a1 1 0 0 1 0 2z",
        color = color,
        contentDescription = "Minus",
        modifier = modifier
    )
}

@Composable
fun GlyphChevronDown(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M12 15.5a1 1 0 0 1-.71-.29l-4-4a1 1 0 1 1 1.42-1.42L12 13.1l3.3-3.18a1 1 0 1 1 1.38 1.44l-4 3.86a1 1 0 0 1-.68.28z",
        color = color,
        contentDescription = "Collapse",
        modifier = modifier
    )
}

@Composable
fun GlyphChevronRight(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M10.5 17a1 1 0 0 1-.71-.29 1 1 0 0 1 0-1.42L13.1 12 9.92 8.69a1 1 0 0 1 0-1.41 1 1 0 0 1 1.42 0l3.86 4a1 1 0 0 1 0 1.4l-4 4a1 1 0 0 1-.7.32z",
        color = color,
        contentDescription = "Next",
        modifier = modifier
    )
}

@Composable
fun GlyphDelete(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M21 6h-5V4.33A2.42 2.42 0 0 0 13.5 2h-3A2.42 2.42 0 0 0 8 4.33V6H3a1 1 0 0 0 0 2h1v11a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3V8h1a1 1 0 0 0 0-2zM10 4.33c0-.16.21-.33.5-.33h3c.29 0 .5.17.5.33V6h-4zM18 19a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V8h12zm-8-7a1 1 0 0 0-1 1v4a1 1 0 0 0 2 0v-4a1 1 0 0 0-1-1zm4 0a1 1 0 0 0-1 1v4a1 1 0 0 0 2 0v-4a1 1 0 0 0-1-1z",
        color = color,
        contentDescription = "Delete",
        modifier = modifier
    )
}

@Composable
fun GlyphClose(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M13.41 12l4.3-4.29a1 1 0 1 0-1.42-1.42L12 10.59l-4.29-4.3a1 1 0 0 0-1.42 1.42l4.3 4.29l-4.3 4.29a1 1 0 0 0 0 1.42a1 1 0 0 0 1.42 0l4.29-4.3l4.29 4.3a1 1 0 0 0 1.42 0a1 1 0 0 0 0-1.42z",
        color = color,
        contentDescription = "Close",
        modifier = modifier
    )
}

@Composable
fun GlyphMusicNote(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M19 15V4a1 1 0 0 0-.38-.78a1 1 0 0 0-.84-.2l-9 2A1 1 0 0 0 8 6v8.34a3.49 3.49 0 1 0 2 3.18a4.36 4.36 0 0 0 0-.52V6.8l7-1.55v7.09a3.49 3.49 0 1 0 2 3.17a4.57 4.57 0 0 0 0-.51z",
        color = color,
        contentDescription = "Music",
        modifier = modifier
    )
}

@Composable
fun GlyphAlbum(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8a8 8 0 0 1-8 8zm0-11a3 3 0 1 0 3 3a3 3 0 0 0-3-3z",
        color = color,
        contentDescription = "Album",
        modifier = modifier
    )
}

@Composable
fun GlyphLibrary(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M21 4.34a1.24 1.24 0 0 0-1.08-.23L13 5.89v14.27l7.56-1.94A1.25 1.25 0 0 0 21.5 17V5.32a1.25 1.25 0 0 0-.5-.98zM11 5.89L4.08 4.11A1.24 1.24 0 0 0 3 4.34a1.25 1.25 0 0 0-.5.98V17a1.25 1.25 0 0 0 .94 1.22L11 20.16z",
        color = color,
        contentDescription = "Library",
        modifier = modifier
    )
}

@Composable
fun GlyphPlaylists(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M12 2.25a1 1 0 0 0-.47.12l-8 4.3a1 1 0 0 0 0 1.76l8 4.3a1 1 0 0 0 .94 0l8-4.3a1 1 0 0 0 0-1.76l-8-4.3A1 1 0 0 0 12 2.25zm7.53 9.87L12 16.16l-7.53-4.04a1 1 0 1 0-.94 1.76l8 4.3a1 1 0 0 0 .94 0l8-4.3a1 1 0 1 0-.94-1.76zm0 4.3L12 20.46l-7.53-4.04a1 1 0 1 0-.94 1.76l8 4.3a1 1 0 0 0 .94 0l8-4.3a1 1 0 1 0-.94-1.76z",
        color = color,
        contentDescription = "Playlists",
        modifier = modifier
    )
}

@Composable
fun GlyphNowPlaying(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M12 3a9 9 0 0 0-9 9v6a3 3 0 0 0 3 3h1a2 2 0 0 0 2-2v-4a2 2 0 0 0-2-2H5v-1a7 7 0 0 1 14 0v1h-2a2 2 0 0 0-2 2v4a2 2 0 0 0 2 2h1a3 3 0 0 0 3-3v-6a9 9 0 0 0-9-9z",
        color = color,
        contentDescription = "Now Playing",
        modifier = modifier
    )
}

@Composable
fun GlyphSettings(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    EvaIconCanvas(
        svgPathData = "M19.86 13.57l-1.35-1a1 1 0 0 1 0-1.14l1.35-1a1.55 1.55 0 0 0 .42-2l-1.33-2.3a1.56 1.56 0 0 0-1.89-.68l-1.61.61a1 1 0 0 1-1-.19a1 1 0 0 1-.37-.78l-.23-1.71A1.56 1.56 0 0 0 12.3 2H9.7a1.56 1.56 0 0 0-1.55 1.38l-.23 1.71a1 1 0 0 1-.37.78a1 1 0 0 1-1 .19l-1.61-.61a1.56 1.56 0 0 0-1.89.68L1.72 8.43a1.55 1.55 0 0 0 .42 2l1.35 1a1 1 0 0 1 0 1.14l-1.35 1a1.55 1.55 0 0 0-.42 2l1.33 2.3a1.56 1.56 0 0 0 1.89.68l1.61-.61a1 1 0 0 1 1 .19a1 1 0 0 1 .37.78l.23 1.71A1.56 1.56 0 0 0 9.7 22h2.6a1.56 1.56 0 0 0 1.55-1.38l.23-1.71a1 1 0 0 1 .37-.78a1 1 0 0 1 1-.19l1.61.61a1.56 1.56 0 0 0 1.89-.68l1.33-2.3a1.55 1.55 0 0 0-.42-2zM11 15a3 3 0 1 1 3-3a3 3 0 0 1-3 3z",
        color = color,
        contentDescription = "Settings",
        modifier = modifier
    )
}

@Composable
fun GlyphDiagnostics(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M14.33 20h-.21a2 2 0 0 1-1.76-1.58L9.68 6l-2.76 6.4A1 1 0 0 1 6 13H3a1 1 0 0 1 0-2h2.34l2.51-5.79a2 2 0 0 1 3.79.38L14.32 18l2.76-6.38A1 1 0 0 1 18 11h3a1 1 0 0 1 0 2h-2.34l-2.51 5.79A2 2 0 0 1 14.33 20z",
        color = color,
        contentDescription = "Diagnostics",
        modifier = modifier
    )
}

@Composable
fun GlyphUpdate(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M20 16v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a1 1 0 0 0-2 0v2a4 4 0 0 0 4 4h12a4 4 0 0 0 4-4v-2a1 1 0 0 0-2 0zm-8.71-.29a1 1 0 0 0 .33.21a.94.94 0 0 0 .76 0a1 1 0 0 0 .33-.21l4-4a1 1 0 0 0-1.42-1.42L13 12.59V4a1 1 0 0 0-2 0v8.59l-2.29-2.3a1 1 0 1 0-1.42 1.42z",
        color = color,
        contentDescription = "Update",
        modifier = modifier
    )
}

@Composable
fun GlyphVolume(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M18.28 8.37a1 1 0 1 0-1.56 1.26a4 4 0 0 1 0 4.74A1 1 0 0 0 17.5 16a1 1 0 0 0 .78-.37a6 6 0 0 0 0-7.26zm1.68-2.74a1 1 0 1 0-1.42 1.42a8 8 0 0 1 0 9.9a1 1 0 0 0 1.42 1.42a10 10 0 0 0 0-12.74zM14.12 3.12a1 1 0 0 0-1.08.11L7.65 7.5H4a1 1 0 0 0-1 1v7a1 1 0 0 0 1 1h3.65l5.39 4.27a1 1 0 0 0 .62.23a1 1 0 0 0 .46-.11A1 1 0 0 0 14.66 20V4a1 1 0 0 0-.54-.88z",
        color = color,
        contentDescription = "Volume",
        modifier = modifier
    )
}

@Composable
fun GlyphBitPerfect(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M12 21.85a2 2 0 0 1-1-.25l-.3-.17A15.17 15.17 0 0 1 3 8.23V6.09a2 2 0 0 1 1.31-1.88l7-2.59a2 2 0 0 1 1.38 0l7 2.59A2 2 0 0 1 21 6.09v2.14a15.17 15.17 0 0 1-7.7 13.2l-.3.17a2 2 0 0 1-1 .25zm-2-9.55l-1.29-1.3a1 1 0 0 0-1.42 1.42l2 2a1 1 0 0 0 1.42 0l4-4a1 1 0 0 0-1.42-1.42z",
        color = color,
        contentDescription = "Bit Perfect",
        modifier = modifier
    )
}

@Composable
fun GlyphCheck(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M9.86 18a1 1 0 0 1-.73-.32l-4.86-5.17a1 1 0 1 1 1.46-1.37l4.12 4.39l8.41-9.2a1 1 0 1 1 1.48 1.34l-9.14 10a1 1 0 0 1-.73.33z",
        color = color,
        contentDescription = "Check",
        modifier = modifier
    )
}

@Composable
fun GlyphNotification(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M20.52 15.21l-1.8-1.81V8.94a6.86 6.86 0 0 0-5.82-6.88a6.74 6.74 0 0 0-7.62 6.67v4.67l-1.8 1.81A1.64 1.64 0 0 0 4.64 18H8v.34A3.84 3.84 0 0 0 12 22a3.84 3.84 0 0 0 4-3.66V18h3.36a1.64 1.64 0 0 0 1.16-2.79zM14 18.34A1.88 1.88 0 0 1 12 20a1.88 1.88 0 0 1-2-1.66V18h4z",
        color = color,
        contentDescription = "Notification",
        modifier = modifier
    )
}

@Composable
fun GlyphDoNotDisturb(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M4.71 3.29a1 1 0 0 0-1.42 1.42l2.14 2.14A6.71 6.71 0 0 0 5.28 8.73v4.67l-1.8 1.81A1.64 1.64 0 0 0 4.64 18H8v.34A3.84 3.84 0 0 0 12 22a3.84 3.84 0 0 0 4-3.66V18h1.59l1.7 1.71a1 1 0 0 0 1.42 0a1 1 0 0 0 0-1.42zM14 18.34A1.88 1.88 0 0 1 12 20a1.88 1.88 0 0 1-2-1.66V18h4zm4.72-4.94V8.94a6.86 6.86 0 0 0-5.82-6.88a6.71 6.71 0 0 0-4.76 1.37L19.86 15.15z",
        color = color,
        contentDescription = "Do Not Disturb",
        modifier = modifier
    )
}

@Composable
fun GlyphInfo(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8a8 8 0 0 1-8 8zm0-8.5a1 1 0 0 0-1 1v3a1 1 0 0 0 2 0v-3a1 1 0 0 0-1-1zm0-4a1.25 1.25 0 1 0 1.25 1.25A1.25 1.25 0 0 0 12 7.5z",
        color = color,
        contentDescription = "Info",
        modifier = modifier
    )
}

@Composable
fun GlyphLyrics(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M19.74 7.33l-4.44-5a1 1 0 0 0-.74-.33h-8A2.53 2.53 0 0 0 4 4.5v15A2.53 2.53 0 0 0 6.56 22h10.88A2.53 2.53 0 0 0 20 19.5V8a1 1 0 0 0-.26-.67zM9 12h6a1 1 0 0 1 0 2H9a1 1 0 0 1 0-2zm6 6H9a1 1 0 0 1 0-2h6a1 1 0 0 1 0 2zm-.29-10a.79.79 0 0 1-.71-.85V4l3.74 4z",
        color = color,
        contentDescription = "Lyrics",
        modifier = modifier
    )
}

@Composable
fun GlyphArrowBack(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M19 11H7.14l3.63-4.36a1 1 0 1 0-1.54-1.28l-5 6a1.19 1.19 0 0 0-.09.15c0 .05 0 .08-.07.13A1 1 0 0 0 4 12a1 1 0 0 0 .07.36c0 .05 0 .08.07.13a1.19 1.19 0 0 0 .09.15l5 6A1 1 0 0 0 10 19a1 1 0 0 0 .77-.36a1 1 0 0 0-.13-1.41L7.14 13H19a1 1 0 0 0 0-2z",
        color = color,
        contentDescription = "Back",
        modifier = modifier
    )
}

@Composable
fun GlyphFilter(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M13.9 22a1 1 0 0 1-.6-.2l-4-3.05a1 1 0 0 1-.39-.8v-3.27l-4.8-9.22A1 1 0 0 1 5 4h14a1 1 0 0 1 .86.49a1 1 0 0 1 0 .98l-4.8 9.21V21a1 1 0 0 1-.55.9a1 1 0 0 1-.61.1z",
        color = color,
        contentDescription = "Filter",
        modifier = modifier
    )
}

@Composable
fun GlyphPin(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    EvaIconCanvas(
        svgPathData = "M17.56 21a1 1 0 0 1-.46-.11L12 18.22l-5.1 2.67a1 1 0 0 1-1.45-1.06l1-5.63l-4.12-4a1 1 0 0 1-.25-1a1 1 0 0 1 .81-.68l5.7-.83l2.51-5.13a1 1 0 0 1 1.8 0l2.54 5.12l5.7.83a1 1 0 0 1 .81.68a1 1 0 0 1-.25 1l-4.12 4l1 5.63a1 1 0 0 1-.4 1a1 1 0 0 1-.62.18z",
        color = color,
        contentDescription = "Pin",
        modifier = modifier
    )
}

// ==========================================
// TRANSPORT BUTTONS & COMPONENTS
// ==========================================

/**
 * Premium Hero Play/Pause button with luminous ambient bloom and tactile scale.
 */
@Composable
fun HeroPlayButton(
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    TactileBox(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .background(
                color = accentColor,
                shape = CircleShape
            )
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = accentColor,
                spotColor = accentColor
            )
    ) {
        val iconColor = Color(0xFF070A12) // Deep navy on neon hero background
        if (isPlaying) {
            GlyphPause(color = iconColor, modifier = Modifier.size(24.dp))
        } else {
            Box(modifier = Modifier.padding(start = 3.dp)) {
                GlyphPlay(color = iconColor, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Secondary circular tactile transport button (Prev / Next).
 */
@Composable
fun SecondaryTransportButton(
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable () -> Unit
) {
    TactileBox(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .background(
                color = Color(0xFF101626),
                shape = CircleShape
            )
            .border(
                width = 0.8.dp,
                color = accentColor.copy(alpha = 0.25f),
                shape = CircleShape
            )
    ) {
        content()
    }
}

/**
 * Modern pill chip with micro-LED status dot for mode toggles.
 */
@Composable
fun ModePillChip(
    label: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    TactileBox(
        onClick = onClick,
        pressedScale = 0.94f,
        modifier = modifier
            .height(34.dp)
            .background(
                color = if (isActive) accentColor.copy(alpha = 0.12f) else Color(0xFF0D121F),
                shape = RoundedCornerShape(17.dp)
            )
            .border(
                width = 0.8.dp,
                color = if (isActive) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(17.dp)
            )
            .padding(horizontal = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Micro LED status dot
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        color = if (isActive) accentColor else Color(0xFF444444),
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = label,
                color = if (isActive) accentColor else TerminalWhite.copy(alpha = 0.75f),
                fontFamily = com.yuka.musicplayer.ui.theme.ModernSansFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
        }
    }
}
