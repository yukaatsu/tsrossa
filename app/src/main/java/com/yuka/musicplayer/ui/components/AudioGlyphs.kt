package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.yuka.musicplayer.ui.theme.m3OnSurface
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
 * Reusable, high-performance Phosphor Icons vector canvas.
 * Built on the official Phosphor 256x256 viewBox grid.
 * https://github.com/phosphor-icons/homepage#phosphor-icons
 */
private val phosphorPathCache = ConcurrentHashMap<String, Path>()

@Composable
fun PhosphorIconCanvas(
    vararg svgPathsData: String,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier.size(20.dp),
    filled: Boolean = true,
    strokeWidth: Float = 0f
) {
    val basePaths = remember(svgPathsData) {
        svgPathsData.map { data ->
            phosphorPathCache.getOrPut(data) {
                PathParser().parsePathString(data).toPath()
            }
        }
    }
    val scaledPath = remember { Path() }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val minDim = size.minDimension
        val strokeStyle = if (strokeWidth > 0f) {
            Stroke(
                width = (strokeWidth / 256f) * minDim,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        } else null

        for (basePath in basePaths) {
            scaledPath.reset()
            scaledPath.addPath(basePath)
            matrix.reset()
            matrix.scale(size.width / 256f, size.height / 256f)
            scaledPath.transform(matrix)
            if (filled) {
                drawPath(path = scaledPath, color = color, style = Fill)
            }
            if (strokeStyle != null) {
                drawPath(path = scaledPath, color = color, style = strokeStyle)
            }
        }
    }
}

/**
 * Backward compatibility alias for any legacy 24x24 callers.
 */
@Composable
fun LucideIconCanvas(
    vararg svgPathsData: String,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier.size(20.dp),
    strokeWidth: Float = 2.0f,
    filled: Boolean = false
) {
    val basePaths = remember(svgPathsData) {
        svgPathsData.map { data ->
            phosphorPathCache.getOrPut("legacy_24_" + data) {
                PathParser().parsePathString(data).toPath()
            }
        }
    }
    val scaledPath = remember { Path() }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val minDim = size.minDimension
        val strokeStyle = Stroke(
            width = (strokeWidth / 24f) * minDim,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        for (basePath in basePaths) {
            scaledPath.reset()
            scaledPath.addPath(basePath)
            matrix.reset()
            matrix.scale(size.width / 24f, size.height / 24f)
            scaledPath.transform(matrix)
            if (filled) {
                drawPath(path = scaledPath, color = color, style = Fill)
            }
            drawPath(path = scaledPath, color = color, style = strokeStyle)
        }
    }
}

/**
 * Backward compatibility alias for Eva icon callers.
 */
@Composable
fun EvaIconCanvas(
    svgPathData: String,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier.size(20.dp)
) {
    PhosphorIconCanvas(
        svgPathData,
        color = color,
        contentDescription = contentDescription,
        modifier = modifier
    )
}

// ==========================================
// OFFICIAL PHOSPHOR ICONS (256x256 Vector Paths)
// https://github.com/phosphor-icons/homepage#phosphor-icons
// ==========================================

@Composable
fun GlyphPlay(color: Color, modifier: Modifier = Modifier.size(24.dp), filled: Boolean = false) {
    val path = if (filled) {
        // phosphor: play-fill
        "M240,128a15.74,15.74,0,0,1-7.6,13.51L88.32,229.65a16,16,0,0,1-16.2.3A15.86,15.86,0,0,1,64,216.13V39.87a15.86,15.86,0,0,1,8.12-13.82,16,16,0,0,1,16.2.3L232.4,114.49A15.74,15.74,0,0,1,240,128Z"
    } else {
        // phosphor: play (regular)
        "M232.4,114.49,88.32,26.35a16,16,0,0,0-16.2-.3A15.86,15.86,0,0,0,64,39.87V216.13A15.94,15.94,0,0,0,80,232a16.07,16.07,0,0,0,8.36-2.35L232.4,141.51a15.81,15.81,0,0,0,0-27ZM80,215.94V40l143.83,88Z"
    }
    PhosphorIconCanvas(
        path,
        color = color,
        contentDescription = "Play",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPause(color: Color, modifier: Modifier = Modifier.size(24.dp), filled: Boolean = false) {
    val path = if (filled) {
        // phosphor: pause-fill
        "M216,48V208a16,16,0,0,1-16,16H160a16,16,0,0,1-16-16V48a16,16,0,0,1,16-16h40A16,16,0,0,1,216,48ZM96,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16H96a16,16,0,0,0,16-16V48A16,16,0,0,0,96,32Z"
    } else {
        // phosphor: pause (regular)
        "M200,32H160a16,16,0,0,0-16,16V208a16,16,0,0,0,16,16h40a16,16,0,0,0,16-16V48A16,16,0,0,0,200,32Zm0,176H160V48h40ZM96,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16H96a16,16,0,0,0,16-16V48A16,16,0,0,0,96,32Zm0,176H56V48H96Z"
    }
    PhosphorIconCanvas(
        path,
        color = color,
        contentDescription = "Pause",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPrev(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    // phosphor: skip-back
    PhosphorIconCanvas(
        "M199.81,34a16,16,0,0,0-16.24.43L64,109.23V40a8,8,0,0,0-16,0V216a8,8,0,0,0,16,0V146.77l119.57,74.78A15.95,15.95,0,0,0,208,208.12V47.88A15.86,15.86,0,0,0,199.81,34ZM192,208,64.16,128,192,48.07Z",
        color = color,
        contentDescription = "Previous Track",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphNext(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    // phosphor: skip-forward
    PhosphorIconCanvas(
        "M200,32a8,8,0,0,0-8,8v69.23L72.43,34.45A15.95,15.95,0,0,0,48,47.88V208.12a16,16,0,0,0,24.43,13.43L192,146.77V216a8,8,0,0,0,16,0V40A8,8,0,0,0,200,32ZM64,207.93V48.07L191.84,128Z",
        color = color,
        contentDescription = "Next Track",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphShuffle(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: shuffle
    PhosphorIconCanvas(
        "M237.66,178.34a8,8,0,0,1,0,11.32l-24,24a8,8,0,0,1-11.32-11.32L212.69,192H200.94a72.12,72.12,0,0,1-58.59-30.15l-41.72-58.4A56.1,56.1,0,0,0,55.06,80H32a8,8,0,0,1,0-16H55.06a72.12,72.12,0,0,1,58.59,30.15l41.72,58.4A56.1,56.1,0,0,0,200.94,176h11.75l-10.35-10.34a8,8,0,0,1,11.32-11.32ZM143,107a8,8,0,0,0,11.16-1.86l1.2-1.67A56.1,56.1,0,0,1,200.94,80h11.75L202.34,90.34a8,8,0,0,0,11.32,11.32l24-24a8,8,0,0,0,0-11.32l-24-24a8,8,0,0,0-11.32,11.32L212.69,64H200.94a72.12,72.12,0,0,0-58.59,30.15l-1.2,1.67A8,8,0,0,0,143,107Zm-30,42a8,8,0,0,0-11.16,1.86l-1.2,1.67A56.1,56.1,0,0,1,55.06,176H32a8,8,0,0,0,0,16H55.06a72.12,72.12,0,0,0,58.59-30.15l1.2-1.67A8,8,0,0,0,113,149Z",
        color = color,
        contentDescription = "Shuffle",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphRepeat(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: repeat
    PhosphorIconCanvas(
        "M24,128A72.08,72.08,0,0,1,96,56H204.69L194.34,45.66a8,8,0,0,1,11.32-11.32l24,24a8,8,0,0,1,0,11.32l-24,24a8,8,0,0,1-11.32-11.32L204.69,72H96a56.06,56.06,0,0,0-56,56,8,8,0,0,1-16,0Zm200-8a8,8,0,0,0-8,8,56.06,56.06,0,0,1-56,56H51.31l10.35-10.34a8,8,0,0,0-11.32-11.32l-24,24a8,8,0,0,0,0,11.32l24,24a8,8,0,0,0,11.32-11.32L51.31,200H160a72.08,72.08,0,0,0,72-72A8,8,0,0,0,224,120Z",
        color = color,
        contentDescription = "Toggle Repeat",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphRepeatOne(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: repeat-once
    PhosphorIconCanvas(
        "M24,128A72.08,72.08,0,0,1,96,56H204.69L194.34,45.66a8,8,0,0,1,11.32-11.32l24,24a8,8,0,0,1,0,11.32l-24,24a8,8,0,0,1-11.32-11.32L204.69,72H96a56.06,56.06,0,0,0-56,56,8,8,0,0,1-16,0Zm200-8a8,8,0,0,0-8,8,56.06,56.06,0,0,1-56,56H51.31l10.35-10.34a8,8,0,0,0-11.32-11.32l-24,24a8,8,0,0,0,0,11.32l24,24a8,8,0,0,0,11.32-11.32L51.31,200H160a72.08,72.08,0,0,0,72-72A8,8,0,0,0,224,120Zm-80,32V104a8,8,0,0,0-11.58-7.16l-16,8a8,8,0,0,0,7.16,14.32L128,116.94V152a8,8,0,0,0,16,0Z",
        color = color,
        contentDescription = "Repeat One",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphBolt(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    // phosphor: lightning
    PhosphorIconCanvas(
        "M215.79,118.17a8,8,0,0,0-5-5.66L153.18,90.9l14.66-73.33a8,8,0,0,0-13.69-7l-112,120a8,8,0,0,0,3,13l57.63,21.61L88.16,238.43a8,8,0,0,0,13.69,7l112-120A8,8,0,0,0,215.79,118.17ZM109.37,214l10.47-52.38a8,8,0,0,0-5-9.06L62,132.71l84.62-90.66L136.16,94.43a8,8,0,0,0,5,9.06l52.8,19.8Z",
        color = color,
        contentDescription = "Bolt",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphLock(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    // phosphor: lock
    PhosphorIconCanvas(
        "M208,80H176V56a48,48,0,0,0-96,0V80H48A16,16,0,0,0,32,96V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V96A16,16,0,0,0,208,80ZM96,56a32,32,0,0,1,64,0V80H96ZM208,208H48V96H208V208Zm-68-56a12,12,0,1,1-12-12A12,12,0,0,1,140,152Z",
        color = color,
        contentDescription = "Hardware Volume Locked",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphUnlock(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    // phosphor: lock-open
    PhosphorIconCanvas(
        "M208,80H96V56a32,32,0,0,1,32-32c15.37,0,29.2,11,32.16,25.59a8,8,0,0,0,15.68-3.18C171.32,24.15,151.2,8,128,8A48.05,48.05,0,0,0,80,56V80H48A16,16,0,0,0,32,96V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V96A16,16,0,0,0,208,80Zm0,128H48V96H208V208Zm-68-56a12,12,0,1,1-12-12A12,12,0,0,1,140,152Z",
        color = color,
        contentDescription = "Hardware Volume Unlocked",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphClipboard(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: clipboard-text
    PhosphorIconCanvas(
        "M200,32H163.74a47.92,47.92,0,0,0-71.48,0H56A16,16,0,0,0,40,48V216a16,16,0,0,0,16,16H200a16,16,0,0,0,16-16V48A16,16,0,0,0,200,32Zm-72,0a32,32,0,0,1,32,32H96A32,32,0,0,1,128,32Zm72,184H56V48H82.75A47.93,47.93,0,0,0,80,64v8a8,8,0,0,0,8,8h80a8,8,0,0,0,8-8V64a47.93,47.93,0,0,0-2.75-16H200ZM160,152a8,8,0,0,1-8,8H104a8,8,0,0,1,0-16h48A8,8,0,0,1,160,152Zm0-32a8,8,0,0,1-8,8H104a8,8,0,0,1,0-16h48A8,8,0,0,1,160,120Z",
        color = color,
        contentDescription = "Clipboard",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphWarning(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: warning
    PhosphorIconCanvas(
        "M236.8,188.09,149.35,36.22h0a24.76,24.76,0,0,0-42.7,0L19.2,188.09a23.51,23.51,0,0,0,0,23.72A24.35,24.35,0,0,0,40.55,224h174.9a24.35,24.35,0,0,0,21.33-12.19A23.51,23.51,0,0,0,236.8,188.09ZM222.93,203.8a8.5,8.5,0,0,1-7.48,4.2H40.55a8.5,8.5,0,0,1-7.48-4.2,7.59,7.59,0,0,1,0-7.72L120.52,44.21a8.75,8.75,0,0,1,15,0l87.45,151.87A7.59,7.59,0,0,1,222.93,203.8ZM120,144V104a8,8,0,0,1,16,0v40a8,8,0,0,1-16,0Zm20,36a12,12,0,1,1-12-12A12,12,0,0,1,140,180Z",
        color = color,
        contentDescription = "Warning",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSearch(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: magnifying-glass
    PhosphorIconCanvas(
        "M229.66,218.34l-50.07-50.06a88.11,88.11,0,1,0-11.31,11.31l50.06,50.07a8,8,0,0,0,11.32-11.32ZM40,112a72,72,0,1,1,72,72A72.08,72.08,0,0,1,40,112Z",
        color = color,
        contentDescription = "Search",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphMoreVert(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: dots-three-vertical
    PhosphorIconCanvas(
        "M112,60a16,16,0,1,1,16,16A16,16,0,0,1,112,60Zm16,52a16,16,0,1,0,16,16A16,16,0,0,0,128,112Zm0,68a16,16,0,1,0,16,16A16,16,0,0,0,128,180Z",
        color = color,
        contentDescription = "Options",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphFolder(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: folder
    PhosphorIconCanvas(
        "M216,72H131.31L104,44.69A15.86,15.86,0,0,0,92.69,40H40A16,16,0,0,0,24,56V200.62A15.4,15.4,0,0,0,39.38,216H216.89A15.13,15.13,0,0,0,232,200.89V88A16,16,0,0,0,216,72ZM40,56H92.69l16,16H40ZM216,200H40V88H216Z",
        color = color,
        contentDescription = "Folder",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphFolderOpen(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: folder-open
    PhosphorIconCanvas(
        "M245,110.64A16,16,0,0,0,232,104H216V88a16,16,0,0,0-16-16H130.67L102.94,51.2a16.14,16.14,0,0,0-9.6-3.2H40A16,16,0,0,0,24,64V208h0a8,8,0,0,0,8,8H211.1a8,8,0,0,0,7.59-5.47l28.49-85.47A16.05,16.05,0,0,0,245,110.64ZM93.34,64,123.2,86.4A8,8,0,0,0,128,88h72v16H69.77a16,16,0,0,0-15.18,10.94L40,158.7V64Zm112,136H43.1l26.67-80H232Z",
        color = color,
        contentDescription = "Open Folder",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphRefresh(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: arrows-clockwise
    PhosphorIconCanvas(
        "M224,48V96a8,8,0,0,1-8,8H168a8,8,0,0,1,0-16h28.69L182.06,73.37a79.56,79.56,0,0,0-56.13-22.43h-.45A79.52,79.52,0,0,0,69.59,72.71,8,8,0,0,1,58.41,61.27a96,96,0,0,1,135,.79L208,76.69V48a8,8,0,0,1,16,0ZM186.41,183.29a80,80,0,0,1-112.47-.66L59.31,168H88a8,8,0,0,0,0-16H40a8,8,0,0,0-8,8v48a8,8,0,0,0,16,0V179.31l14.63,14.63A95.43,95.43,0,0,0,130,222.06h.53a95.36,95.36,0,0,0,67.07-27.33,8,8,0,0,0-11.18-11.44Z",
        color = color,
        contentDescription = "Refresh",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSort(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: arrows-down-up
    PhosphorIconCanvas(
        "M117.66,170.34a8,8,0,0,1,0,11.32l-32,32a8,8,0,0,1-11.32,0l-32-32a8,8,0,0,1,11.32-11.32L72,188.69V48a8,8,0,0,1,16,0V188.69l18.34-18.35A8,8,0,0,1,117.66,170.34Zm96-96-32-32a8,8,0,0,0-11.32,0l-32,32a8,8,0,0,0,11.32,11.32L168,67.31V208a8,8,0,0,0,16,0V67.31l18.34,18.35a8,8,0,0,0,11.32-11.32Z",
        color = color,
        contentDescription = "Sort",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphQueue(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: queue
    PhosphorIconCanvas(
        "M32,64a8,8,0,0,1,8-8H216a8,8,0,0,1,0,16H40A8,8,0,0,1,32,64ZM136,120H40a8,8,0,0,0,0,16h96a8,8,0,0,0,0-16Zm0,64H40a8,8,0,0,0,0,16h96a8,8,0,0,0,0-16Zm107.66-29.34-48-32A8,8,0,0,0,184,128v64a8,8,0,0,0,11.66,7.11l48-32a8,8,0,0,0,0-14.22ZM200,177.23V142.77L225.85,160Z",
        color = color,
        contentDescription = "Queue",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphAdd(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: plus
    PhosphorIconCanvas(
        "M224,128a8,8,0,0,1-8,8H136v80a8,8,0,0,1-16,0V136H40a8,8,0,0,1,0-16h80V40a8,8,0,0,1,16,0v80h80A8,8,0,0,1,224,128Z",
        color = color,
        contentDescription = "Add",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphMinus(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: minus
    PhosphorIconCanvas(
        "M224,128a8,8,0,0,1-8,8H40a8,8,0,0,1,0-16H216A8,8,0,0,1,224,128Z",
        color = color,
        contentDescription = "Minus",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphChevronDown(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: caret-down
    PhosphorIconCanvas(
        "M213.66,101.66l-80,80a8,8,0,0,1-11.32,0l-80-80A8,8,0,0,1,53.66,90.34L128,164.69l74.34-74.35a8,8,0,0,1,11.32,11.32Z",
        color = color,
        contentDescription = "Collapse",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphChevronRight(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: caret-right
    PhosphorIconCanvas(
        "M181.66,133.66l-80,80a8,8,0,0,1-11.32-11.32L164.69,128,90.34,53.66a8,8,0,0,1,11.32-11.32l80,80A8,8,0,0,1,181.66,133.66Z",
        color = color,
        contentDescription = "Next",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphDelete(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: trash
    PhosphorIconCanvas(
        "M216,48H176V40a24,24,0,0,0-24-24H104A24,24,0,0,0,80,40v8H40a8,8,0,0,0,0,16h8V208a16,16,0,0,0,16,16H192a16,16,0,0,0,16-16V64h8a8,8,0,0,0,0-16ZM96,40a8,8,0,0,1,8-8h48a8,8,0,0,1,8,8v8H96Zm96,168H64V64H192ZM112,104v64a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Zm48,0v64a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Z",
        color = color,
        contentDescription = "Delete",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphClose(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: x
    PhosphorIconCanvas(
        "M205.66,194.34a8,8,0,0,1-11.32,11.32L128,139.31,61.66,205.66a8,8,0,0,1-11.32-11.32L116.69,128,50.34,61.66A8,8,0,0,1,61.66,50.34L128,116.69l66.34-66.35a8,8,0,0,1,11.32,11.32L139.31,128Z",
        color = color,
        contentDescription = "Close",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphMusicNote(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: music-note
    PhosphorIconCanvas(
        "M210.3,56.34l-80-24A8,8,0,0,0,120,40V148.26A47.92,47.92,0,1,0,136,184V98.75l69.7,20.91A8,8,0,0,0,216,112V64A8,8,0,0,0,210.3,56.34ZM88,216a32,32,0,1,1,32-32A32,32,0,0,1,88,216ZM200,101.25l-64-19.2V50.75l64,19.2Z",
        color = color,
        contentDescription = "Music",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphAlbum(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: vinyl-record
    PhosphorIconCanvas(
        "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm0-152a64.07,64.07,0,0,0-64,64,8,8,0,0,0,16,0,48.05,48.05,0,0,1,48-48,8,8,0,0,0,0-16Zm64,64a8,8,0,0,0-16,0,48.05,48.05,0,0,1-48,48,8,8,0,0,0,0,16A64.07,64.07,0,0,0,192,128Zm-64-24a24,24,0,1,0,24,24A24,24,0,0,0,128,104Zm0,32a8,8,0,1,1,8-8A8,8,0,0,1,128,136Z",
        color = color,
        contentDescription = "Album",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphLibrary(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: books
    PhosphorIconCanvas(
        "M231.65,194.55,198.46,36.75a16,16,0,0,0-19-12.39L132.65,34.42a16.08,16.08,0,0,0-12.3,19l33.19,157.8A16,16,0,0,0,169.16,224a16.25,16.25,0,0,0,3.38-.36l46.81-10.06A16.09,16.09,0,0,0,231.65,194.55ZM136,50.15l46.82-10.06,3.34,15.9L139.33,66Zm10,47.69,46.81-10.06,13.38,63.58L159.35,161.42Zm23.2,110.22-6.51-30.94,46.81-10.06,6.51,30.94ZM104,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V48A16,16,0,0,0,104,32ZM56,48h48V64H56Zm0,32h48v96H56Zm48,128H56V192h48v16Z",
        color = color,
        contentDescription = "Library",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPlaylists(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: playlist
    PhosphorIconCanvas(
        "M32,64a8,8,0,0,1,8-8H216a8,8,0,0,1,0,16H40A8,8,0,0,1,32,64Zm128,56H40a8,8,0,0,0,0,16H160a8,8,0,0,0,0-16Zm-48,64H40a8,8,0,0,0,0,16h72a8,8,0,0,0,0-16Zm130.3-55.66-40-12A8,8,0,0,0,192,124v53.71A32,32,0,1,0,208,200V134.81l29.7,8.91a8,8,0,1,0,4.6-15.32ZM176,216a16,16,0,1,1,16-16A16,16,0,0,1,176,216Z",
        color = color,
        contentDescription = "Playlists",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphNowPlaying(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: waveform
    PhosphorIconCanvas(
        "M56,96v64a8,8,0,0,1-16,0V96a8,8,0,0,1,16,0ZM88,24a8,8,0,0,0-8,8V224a8,8,0,0,0,16,0V32A8,8,0,0,0,88,24Zm40,32a8,8,0,0,0-8,8V192a8,8,0,0,0,16,0V64A8,8,0,0,0,128,56Zm40,32a8,8,0,0,0-8,8v64a8,8,0,0,0,16,0V96A8,8,0,0,0,168,88Zm40-16a8,8,0,0,0-8,8v96a8,8,0,0,0,16,0V80A8,8,0,0,0,208,72Z",
        color = color,
        contentDescription = "Now Playing",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSettings(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    // phosphor: gear-six
    PhosphorIconCanvas(
        "M128,80a48,48,0,1,0,48,48A48.05,48.05,0,0,0,128,80Zm0,80a32,32,0,1,1,32-32A32,32,0,0,1,128,160Zm109.94-52.79a8,8,0,0,0-3.89-5.4l-29.83-17-.12-33.62a8,8,0,0,0-2.83-6.08,111.91,111.91,0,0,0-36.72-20.67,8,8,0,0,0-6.46.59L128,41.85,97.88,25a8,8,0,0,0-6.47-.6A111.92,111.92,0,0,0,54.73,45.15a8,8,0,0,0-2.83,6.07l-.15,33.65-29.83,17a8,8,0,0,0-3.89,5.4,106.47,106.47,0,0,0,0,41.56,8,8,0,0,0,3.89,5.4l29.83,17,.12,33.62a8,8,0,0,0,2.83,6.08,111.91,111.91,0,0,0,36.72,20.67,8,8,0,0,0,6.46-.59L128,214.15,158.12,231a7.91,7.91,0,0,0,3.9,1,8.09,8.09,0,0,0,2.57-.42,112.1,112.1,0,0,0,36.68-20.73,8,8,0,0,0,2.83-6.07l.15-33.65,29.83-17a8,8,0,0,0,3.89-5.4A106.47,106.47,0,0,0,237.94,107.21Zm-15,34.91-28.57,16.25a8,8,0,0,0-3,3c-.58,1-1.19,2.06-1.81,3.06a7.94,7.94,0,0,0-1.22,4.21l-.15,32.25a95.89,95.89,0,0,1-25.37,14.3L134,199.36a8,8,0,0,0-3.91-1h-.19c-1.21,0-2.43,0-3.64,0a8.1,8.1,0,0,0-4.1,1l-28.84,16.1A96,96,0,0,1,67.88,201l-.11-32.2a8,8,0,0,0-1.22-4.22c-.62-1-1.23-2-1.8-3.06a8.09,8.09,0,0,0-3-3.06l-28.6-16.29a90.49,90.49,0,0,1,0-28.26L61.72,97.63a8,8,0,0,0,3-3c.58-1,1.19-2.06,1.81-3.06a7.94,7.94,0,0,0,1.22-4.21l.15-32.25a95.89,95.89,0,0,1,25.37-14.3L122,56.64a8,8,0,0,0,4.1,1c1.21,0,2.43,0,3.64,0a8,8,0,0,0,4.1-1l28.84-16.1A96,96,0,0,1,188.12,55l.11,32.2a8,8,0,0,0,1.22,4.22c.62,1,1.23,2,1.8,3.06a8.09,8.09,0,0,0,3,3.06l28.6,16.29A90.49,90.49,0,0,1,222.9,142.12Z",
        color = color,
        contentDescription = "Settings",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphDiagnostics(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: pulse
    PhosphorIconCanvas(
        "M240,128a8,8,0,0,1-8,8H204.94l-37.78,75.58A8,8,0,0,1,160,216h-.4a8,8,0,0,1-7.08-5.14L95.35,60.76,63.28,131.31A8,8,0,0,1,56,136H24a8,8,0,0,1,0-16H50.85L88.72,36.69a8,8,0,0,1,14.76.46l57.51,151,31.85-63.71A8,8,0,0,1,200,120h32A8,8,0,0,1,240,128Z",
        color = color,
        contentDescription = "Diagnostics",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphUpdate(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: download-simple
    PhosphorIconCanvas(
        "M224,144v64a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V144a8,8,0,0,1,16,0v56H208V144a8,8,0,0,1,16,0Zm-101.66,5.66a8,8,0,0,0,11.32,0l40-40a8,8,0,0,0-11.32-11.32L136,124.69V32a8,8,0,0,0-16,0v92.69L93.66,98.34a8,8,0,0,0-11.32,11.32Z",
        color = color,
        contentDescription = "Update",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphVolume(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: speaker-high
    PhosphorIconCanvas(
        "M155.51,24.81a8,8,0,0,0-8.42.88L77.25,80H32A16,16,0,0,0,16,96v64a16,16,0,0,0,16,16H77.25l69.84,54.31A8,8,0,0,0,160,224V32A8,8,0,0,0,155.51,24.81ZM32,96H72v64H32ZM144,207.64,88,164.09V91.91l56-43.55Zm54-106.08a40,40,0,0,1,0,52.88,8,8,0,0,1-12-10.58,24,24,0,0,0,0-31.72,8,8,0,0,1,12-10.58ZM248,128a79.9,79.9,0,0,1-20.37,53.34,8,8,0,0,1-11.92-10.67,64,64,0,0,0,0-85.33,8,8,0,1,1,11.92-10.67A79.83,79.83,0,0,1,248,128Z",
        color = color,
        contentDescription = "Volume",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphVolumeMute(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: speaker-slash
    PhosphorIconCanvas(
        "M53.92,34.62A8,8,0,1,0,42.08,45.38L73.55,80H32A16,16,0,0,0,16,96v64a16,16,0,0,0,16,16H77.25l69.84,54.31A8,8,0,0,0,160,224V175.09l42.08,46.29a8,8,0,1,0,11.84-10.76ZM32,96H72v64H32ZM144,207.64,88,164.09V95.9l56,61.6Zm-30.34-135a8,8,0,0,1,1.65-11.21l31.78-24.72A8,8,0,0,1,160,32v74.59a8,8,0,0,1-16,0V48.36L124.87,63.24A8,8,0,0,1,113.66,72.64Zm84.33,28.92a40,40,0,0,1,0,52.88,8,8,0,0,1-12-10.58,24,24,0,0,0,0-31.72,8,8,0,0,1,12-10.58ZM248,128a79.9,79.9,0,0,1-20.37,53.34,8,8,0,0,1-11.92-10.67,64,64,0,0,0,0-85.33,8,8,0,1,1,11.92-10.67A79.83,79.83,0,0,1,248,128Z",
        color = color,
        contentDescription = "Volume Muted",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphBitPerfect(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: shield-check
    PhosphorIconCanvas(
        "M208,40H48A16,16,0,0,0,32,56v58.78c0,89.61,75.82,119.34,91,124.39a15.53,15.53,0,0,0,10,0c15.2-5.05,91-34.78,91-124.39V56A16,16,0,0,0,208,40Zm0,74.79c0,78.42-66.35,104.62-80,109.18-13.53-4.51-80-30.69-80-109.18V56H208ZM82.34,141.66a8,8,0,0,1,11.32-11.32L112,148.68l50.34-50.34a8,8,0,0,1,11.32,11.32l-56,56a8,8,0,0,1-11.32,0Z",
        color = color,
        contentDescription = "Bit Perfect",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphCheck(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: check
    PhosphorIconCanvas(
        "M229.66,77.66l-128,128a8,8,0,0,1-11.32,0l-56-56a8,8,0,0,1,11.32-11.32L96,188.69,218.34,66.34a8,8,0,0,1,11.32,11.32Z",
        color = color,
        contentDescription = "Check",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphNotification(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: bell
    PhosphorIconCanvas(
        "M221.8,175.94C216.25,166.38,208,139.33,208,104a80,80,0,1,0-160,0c0,35.34-8.26,62.38-13.81,71.94A16,16,0,0,0,48,200H88.81a40,40,0,0,0,78.38,0H208a16,16,0,0,0,13.8-24.06ZM128,216a24,24,0,0,1-22.62-16h45.24A24,24,0,0,1,128,216ZM48,184c7.7-13.24,16-43.92,16-80a64,64,0,1,1,128,0c0,36.05,8.28,66.73,16,80Z",
        color = color,
        contentDescription = "Notification",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphDoNotDisturb(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: moon
    PhosphorIconCanvas(
        "M233.54,142.23a8,8,0,0,0-8-2,88.08,88.08,0,0,1-109.8-109.8,8,8,0,0,0-10-10,104.84,104.84,0,0,0-52.91,37A104,104,0,0,0,136,224a103.09,103.09,0,0,0,62.52-20.88,104.84,104.84,0,0,0,37-52.91A8,8,0,0,0,233.54,142.23ZM188.9,190.34A88,88,0,0,1,65.66,67.11a89,89,0,0,1,31.4-26A106,106,0,0,0,96,56,104.11,104.11,0,0,0,200,160a106,106,0,0,0,14.92-1.06A89,89,0,0,1,188.9,190.34Z",
        color = color,
        contentDescription = "Do Not Disturb",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphInfo(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: info
    PhosphorIconCanvas(
        "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm16-40a8,8,0,0,1-8,8,16,16,0,0,1-16-16V128a8,8,0,0,1,0-16,16,16,0,0,1,16,16v40A8,8,0,0,1,144,176ZM112,84a12,12,0,1,1,12,12A12,12,0,0,1,112,84Z",
        color = color,
        contentDescription = "Info",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphLyrics(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: microphone-stage
    PhosphorIconCanvas(
        "M168,16a63.84,63.84,0,0,0-60.92,44.76l-.15.46L30.22,191.91a16,16,0,0,0,2.47,19.38l12,12a16,16,0,0,0,19.38,2.47L194.78,149l.46-.15A64,64,0,1,0,168,16Zm48,64a47.82,47.82,0,0,1-9.22,28.31L139.69,41.22A48,48,0,0,1,216,80ZM56,212,44,200l73.57-73.57,12,12Zm100.34-44.34-12-12,51.09-29.93a48.29,48.29,0,0,1-9.16,9.16ZM105.66,150.34a8,8,0,0,1-11.32,11.32l-16-16a8,8,0,0,1,11.32-11.32Z",
        color = color,
        contentDescription = "Lyrics",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphArrowBack(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: arrow-left
    PhosphorIconCanvas(
        "M224,128a8,8,0,0,1-8,8H59.31l58.35,58.34a8,8,0,0,1-11.32,11.32l-72-72a8,8,0,0,1,0-11.32l72-72a8,8,0,0,1,11.32,11.32L59.31,120H216A8,8,0,0,1,224,128Z",
        color = color,
        contentDescription = "Back",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphFilter(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: funnel
    PhosphorIconCanvas(
        "M230.6,49.53A15.81,15.81,0,0,0,216,40H40A16,16,0,0,0,28.19,66.76l.08.09L96,139.17V216a16,16,0,0,0,24.87,13.32l32-21.34A16,16,0,0,0,160,194.66V139.17l67.74-72.32.08-.09A15.8,15.8,0,0,0,230.6,49.53ZM40,56h0Zm106.18,74.58A8,8,0,0,0,144,136v58.66L112,216V136a8,8,0,0,0-2.18-5.47L40,56H216Z",
        color = color,
        contentDescription = "Filter",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPin(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    // phosphor: push-pin
    PhosphorIconCanvas(
        "M235.33,104,152,20.69a16,16,0,0,0-22.63,0L98.37,51.72c-10.66-3.34-35-7.37-60.4,13.14a16,16,0,0,0-1.29,23.78L85,136.71,42.34,179.34a8,8,0,0,0,11.32,11.32L96.29,148l48.07,48.07A16,16,0,0,0,155.66,200c.38,0,.75,0,1.13,0a15.93,15.93,0,0,0,11.64-6.33c19.64-26.1,17.75-47.32,13.19-60L212.7,102.6,224,113.94a16,16,0,0,0,11.33-9.94ZM156,184,48,76c19.47-14.51,38.49-9.72,46.63-6.11a8,8,0,0,0,8.87-1.69L140.69,31,225,115.31,187.8,152.5a8,8,0,0,0-1.69,8.87C189.65,169.39,192.09,180.52,156,184Z",
        color = color,
        contentDescription = "Pin",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphGitHub(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: github-logo
    PhosphorIconCanvas(
        "M208.31,75.68A59.78,59.78,0,0,0,202.93,28,8,8,0,0,0,196,24a59.75,59.75,0,0,0-48,24H124A59.75,59.75,0,0,0,76,24a8,8,0,0,0-6.93,4,59.78,59.78,0,0,0-5.38,47.68A58.14,58.14,0,0,0,56,104v8a56.06,56.06,0,0,0,48.44,55.47A39.8,39.8,0,0,0,96,192v8H72a24,24,0,0,1-24-24A40,40,0,0,0,8,136a8,8,0,0,0,0,16,24,24,0,0,1,24,24,40,40,0,0,0,40,40H96v16a8,8,0,0,0,16,0V192a24,24,0,0,1,48,0v40a8,8,0,0,0,16,0V192a39.8,39.8,0,0,0-8.44-24.53A56.06,56.06,0,0,0,216,112v-8A58.14,58.14,0,0,0,208.31,75.68ZM200,112a40,40,0,0,1-40,40H112a40,40,0,0,1-40-40v-8a41.74,41.74,0,0,1,6.9-22.48A8,8,0,0,0,80,73.83a43.81,43.81,0,0,1,.79-33.58,43.88,43.88,0,0,1,32.32,20.06A8,8,0,0,0,119.82,64h32.35a8,8,0,0,0,6.74-3.69,43.87,43.87,0,0,1,32.32-20.06A43.81,43.81,0,0,1,192,73.83a8.09,8.09,0,0,0,1,7.65A41.72,41.72,0,0,1,200,104Z",
        color = color,
        contentDescription = "GitHub",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphDiscord(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: discord-logo (filled 256x256)
    PhosphorIconCanvas(
        "M247.51,174.93,216.83,55.59A16.08,16.08,0,0,0,201.7,43.82l-36.5-5.22a8.1,8.1,0,0,0-9,6.22l-4.29,17.18a118.69,118.69,0,0,0-47.82,0L99.8,44.82a8.1,8.1,0,0,0-9-6.22L54.3,43.82A16.08,16.08,0,0,0,39.17,55.59L8.49,174.93a15.94,15.94,0,0,0,9.56,18.71l67.3,26.92a8,8,0,0,0,10.17-4.51l8.63-21.58a116.51,116.51,0,0,0,47.7,0l8.63,21.58a8,8,0,0,0,10.17,4.51l67.3-26.92A15.94,15.94,0,0,0,247.51,174.93ZM92,160a16,16,0,1,1,16-16A16,16,0,0,1,92,160Zm72,0a16,16,0,1,1,16-16A16,16,0,0,1,164,160Z",
        color = color,
        contentDescription = "Discord Server",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphBagiBagi(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: hand-heart
    PhosphorIconCanvas(
        "M240,104a32.06,32.06,0,0,0-31,24H176a32,32,0,0,0-29.88-20.47L117,97.81A32,32,0,0,0,96,90.15H48a16,16,0,0,0-16,16v64a16,16,0,0,0,16,16H188.69a31.82,31.82,0,0,0,22.62-9.37l32-32A16,16,0,0,0,240,104ZM199.31,165.31a15.89,15.89,0,0,1-11.31,4.69H48v-64H96a16,16,0,0,1,10.49,3.91l29.11,9.7a16,16,0,0,1,10.94,15.18H112a8,8,0,0,0,0,16h56a8,8,0,0,0,8-8,16,16,0,0,1,32,0,8,8,0,0,0,8,8h1.37ZM125.13,86.17a8,8,0,0,0,5.74,0C136.4,84,184,64.42,184,36a28,28,0,0,0-52-14.34A28,28,0,0,0,80,36C80,64.42,127.6,84,125.13,86.17ZM108,24a12,12,0,0,1,12,12,8,8,0,0,0,16,0,12,12,0,0,1,24,0c0,14.12-21.57,28.72-32,34.43C117.57,64.72,96,50.12,96,36A12,12,0,0,1,108,24Z",
        color = color,
        contentDescription = "Bagi-Bagi Donation",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphEdit(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    // phosphor: pencil-simple
    PhosphorIconCanvas(
        "M227.31,73.37,182.63,28.68a16,16,0,0,0-22.63,0L36.69,152A15.86,15.86,0,0,0,32,163.31V208a16,16,0,0,0,16,16H92.69A15.86,15.86,0,0,0,104,219.31L227.31,96a16,16,0,0,0,0-22.63ZM92.69,208H48V163.31l88-88L180.69,120ZM192,108.68,147.31,64l24-24L216,84.68Z",
        color = color,
        contentDescription = "Edit",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSparkle(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: sparkle
    PhosphorIconCanvas(
        "M208,144a15.78,15.78,0,0,1-10.42,14.94l-51.65,19-19,51.61a15.92,15.92,0,0,1-29.88,0L78,178l-51.62-19a15.92,15.92,0,0,1,0-29.88l51.65-19,19-51.61a15.92,15.92,0,0,1,29.88,0l19,51.65,51.61,19A15.78,15.78,0,0,1,208,144ZM152,48h16V64a8,8,0,0,0,16,0V48h16a8,8,0,0,0,0-16H184V16a8,8,0,0,0-16,0V32H152a8,8,0,0,0,0,16Zm88,32h-8V72a8,8,0,0,0-16,0v8h-8a8,8,0,0,0,0,16h8v8a8,8,0,0,0,16,0V96h8a8,8,0,0,0,0-16Z",
        color = color,
        contentDescription = "Highlight",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphChatTeardropText(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: chat-teardrop-text
    PhosphorIconCanvas(
        "M132,24A100.11,100.11,0,0,0,32,124v84a16,16,0,0,0,16,16h84a100,100,0,0,0,0-200Zm0,184H48V124a84,84,0,1,1,84,84Zm40-100a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h68A8,8,0,0,1,172,108Zm0,32a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h68A8,8,0,0,1,172,140Z",
        color = color,
        contentDescription = "Message",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphEqualizer(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    // phosphor: sliders-horizontal (Parametric EQ)
    PhosphorIconCanvas(
        "M120,40v16H40a8,8,0,0,0,0,16h80V88a8,8,0,0,0,16,0V40a8,8,0,0,0-16,0Zm96,24H152a8,8,0,0,0,0,16h64a8,8,0,0,0,0-16ZM40,136h64a8,8,0,0,0,0-16H40a8,8,0,0,0,0,16Zm176-16H120v-16a8,8,0,0,0-16,0v48a8,8,0,0,0,16,0V136h96a8,8,0,0,0,0-16ZM80,168v-16a8,8,0,0,0-16,0v16H40a8,8,0,0,0,0,16H64v16a8,8,0,0,0,16,0V168Zm136,8H96a8,8,0,0,0,0,16H216a8,8,0,0,0,0-16Z",
        color = color,
        contentDescription = "Parametric Equalizer",
        modifier = modifier,
        filled = true
    )
}

// ==========================================
// TSROSSA LOGO SILHOUETTE (Transparent Vector)
// Exact 10-layer vector geometry from tsrossa_app_icons/icon-1024.png
// (5 Alabaster/White Note & Outer Petals + 5 Crimson Rose Petals with EvenOdd Cutout)
// ==========================================

private data class TsrossaLogoLayer(
    val pathData: String,
    val isCrimsonPetal: Boolean,
    val isEvenOdd: Boolean = false
)

private val tsrossaIcon1024Layers = arrayOf(
    // 1. Windswept Upper-Left Rose Petal (Crimson)
    TsrossaLogoLayer(
        pathData = "M 35.8,34.8 A 14.0,14.0 0 0,1 50.2,24.6 A 14.0,14.0 0 0,1 35.8,34.8 Z",
        isCrimsonPetal = true
    ),
    // 2. Outer Asymmetric Left Rose Petal (Alabaster)
    TsrossaLogoLayer(
        pathData = "M 29.65,64.88 A 21.00,21.00 0 0,1 24.85,54.68 A 21.00,21.00 0 0,1 35.60,49.37 A 13.20,13.20 0 0,0 29.65,64.88 Z",
        isCrimsonPetal = false
    ),
    // 3. Outer Upper-Middle Rose Petal (Alabaster)
    TsrossaLogoLayer(
        pathData = "M 37.26,48.56 A 20.50,20.50 0 0,1 45.67,43.14 A 20.50,20.50 0 0,1 52.31,52.32 A 13.20,13.20 0 0,0 37.26,48.56 Z",
        isCrimsonPetal = false
    ),
    // 4. Inner Upper Rose Petal (Crimson)
    TsrossaLogoLayer(
        pathData = "M 35.37,57.47 A 13.50,13.50 0 0,1 39.95,50.24 A 13.50,13.50 0 0,1 47.09,54.98 A 7.60,7.60 0 0,0 35.37,57.47 Z",
        isCrimsonPetal = true
    ),
    // 5. Inner Lower-Left Rose Petal (Alabaster)
    TsrossaLogoLayer(
        pathData = "M 41.67,68.38 A 13.50,13.50 0 0,1 33.35,66.99 A 13.50,13.50 0 0,1 34.89,58.71 A 7.60,7.60 0 0,0 41.67,68.38 Z",
        isCrimsonPetal = false
    ),
    // 6. Inner Right Rose Petal (Crimson)
    TsrossaLogoLayer(
        pathData = "M 48.03,56.24 A 12.00,12.00 0 0,1 51.10,62.68 A 12.00,12.00 0 0,1 45.21,67.56 A 7.40,7.40 0 0,0 48.03,56.24 Z",
        isCrimsonPetal = true
    ),
    // 7. Central Rosebud Heart with EvenOdd Crescent Cutout (Crimson)
    TsrossaLogoLayer(
        pathData = "M 37.3,58.1 C 38.8,54.9 43.1,53.8 46.3,55.7 C 49.1,57.4 49.5,61.3 47.2,64.1 C 44.8,67.1 40.3,67.5 37.5,65.0 C 35.4,63.2 35.6,60.2 37.3,58.1 Z M 39.6,58.3 C 41.6,56.8 44.6,57.4 45.5,59.4 C 46.3,61.1 45.3,63.4 43.3,64.3 C 44.2,62.0 43.5,59.6 41.4,58.7 C 40.7,58.3 40.1,58.2 39.6,58.3 Z",
        isCrimsonPetal = true,
        isEvenOdd = true
    ),
    // 8. Secondary Crimson Streak Flag (Crimson)
    TsrossaLogoLayer(
        pathData = "M 62.0,37.4 C 69.0,40.4 73.8,47.2 73.4,54.8 C 73.0,62.4 67.8,69.2 59.8,72.8 C 64.2,67.4 66.6,60.6 66.2,53.8 C 65.8,47.4 64.2,42.2 61.6,39.4 Z",
        isCrimsonPetal = true
    ),
    // 9. Bottom-Right Sonic/Rose Slash Accent (Alabaster)
    TsrossaLogoLayer(
        pathData = "M 47.8,81.8 C 58.0,83.4 68.4,79.4 75.0,71.4 C 67.4,77.0 57.8,79.8 47.8,81.8 Z",
        isCrimsonPetal = false
    ),
    // 10. Main Unified Rose-Petal Base + Thorny Stem + Sweeping Alabaster Petal-Flag (Alabaster)
    TsrossaLogoLayer(
        pathData = "M 29.2,67.8 A 21.5,21.5 0 0,0 42.6,80.2 C 51.6,78.0 57.0,71.0 57.8,61.2 L 60.4,32.8 C 67.0,35.2 73.6,40.6 76.2,48.0 C 78.0,53.0 77.2,58.6 74.4,63.6 C 81.8,58.0 85.0,48.6 82.2,39.5 C 79.4,30.4 70.2,26.4 62.8,24.0 L 57.6,22.2 L 55.8,26.0 L 54.6,39.6 L 48.4,36.8 L 54.1,43.8 L 52.8,60.2 C 52.2,68.6 45.4,74.6 37.2,73.6 C 33.8,73.2 31.0,71.0 29.2,67.8 Z",
        isCrimsonPetal = false
    )
)

@Composable
fun GlyphTsrossaLogoSilhouette(
    color: Color = m3OnSurface(),
    crimsonColor: Color = Color(0xFFFF2E55),
    modifier: Modifier = Modifier.size(22.dp)
) {
    val parsedLayers = remember {
        tsrossaIcon1024Layers.map { layer ->
            val path = phosphorPathCache.getOrPut("tsrossa_1024_" + layer.pathData) {
                PathParser().parsePathString(layer.pathData).toPath()
            }
            if (layer.isEvenOdd) {
                path.fillType = PathFillType.EvenOdd
            }
            Pair(path, layer.isCrimsonPetal)
        }
    }
    val transformedPath = remember { Path() }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier.semantics { this.contentDescription = "tsrossa Logo" }) {
        // Tight bounding box of icon-1024.png geometry in 108x108 space:
        // X in [24.5..83.0] -> width ~ 58.5, Y in [22.0..82.5] -> height ~ 60.5
        val contentWidth = 59.0f
        val contentHeight = 61.0f
        val scale = minOf(size.width / contentWidth, size.height / contentHeight)
        val left = (size.width - contentWidth * scale) / 2f
        val top = (size.height - contentHeight * scale) / 2f

        for ((basePath, isCrimson) in parsedLayers) {
            transformedPath.reset()
            transformedPath.fillType = basePath.fillType
            transformedPath.addPath(basePath)
            matrix.reset()
            matrix.translate(left - 24.5f * scale, top - 22.0f * scale)
            matrix.scale(scale, scale)
            transformedPath.transform(matrix)
            val fillCol = if (isCrimson) crimsonColor else color
            drawPath(path = transformedPath, color = fillCol, style = Fill)
        }
    }
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
            GlyphPause(color = iconColor, modifier = Modifier.size(24.dp), filled = true)
        } else {
            Box(modifier = Modifier.padding(start = 3.dp)) {
                GlyphPlay(color = iconColor, modifier = Modifier.size(24.dp), filled = true)
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
