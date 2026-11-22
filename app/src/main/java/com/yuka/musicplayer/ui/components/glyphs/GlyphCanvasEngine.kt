package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.ui.theme.m3OnSurface
import java.util.concurrent.ConcurrentHashMap

/**
 * Reusable, high-performance Phosphor Icons vector canvas.
 * Built on the official Phosphor 256x256 viewBox grid.
 * https://github.com/phosphor-icons/homepage#phosphor-icons
 */
internal val phosphorPathCache = ConcurrentHashMap<String, Path>()

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
