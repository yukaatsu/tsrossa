package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.audio.PeqBand
import com.yuka.musicplayer.audio.PeqEvaluator
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.util.ColorContrastHelper
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * 60 FPS Logarithmic Frequency Response Graph with Light/Dark adaptive grid & interactive band dots.
 */
@Composable
internal fun PeqFrequencyResponseGraph(
    bands: List<PeqBand>,
    preampDb: Float,
    accentColor: Color,
    isEnabled: Boolean,
    selectedBandIndex: Int,
    onSelectBand: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val zeroGridColor = onSurfaceColor.copy(
        alpha = if (isEnabled) (if (isDark) 0.24f else 0.30f) else 0.12f
    )
    val minorGridColor = onSurfaceColor.copy(
        alpha = if (isEnabled) (if (isDark) 0.08f else 0.14f) else 0.05f
    )

    val samplePoints = remember {
        val numPoints = 96
        val logMin = log10(20.0)
        val logMax = log10(20000.0)
        FloatArray(numPoints) { i ->
            val frac = i.toDouble() / (numPoints - 1)
            10.0.pow(logMin + frac * (logMax - logMin)).toFloat()
        }
    }

    val curveGainValues = remember(bands, preampDb) {
        PeqEvaluator.evaluateCurve(bands, samplePoints, preampDb)
    }

    val currentOnSelectBand by rememberUpdatedState(onSelectBand)

    Canvas(
        modifier = modifier.pointerInput(bands, isEnabled) {
            if (!isEnabled) return@pointerInput
            detectTapGestures { offset ->
                val w = size.width.toFloat()
                val logMin = log10(20.0)
                val logSpan = log10(20000.0) - logMin
                var nearestIdx = 0
                var minDiff = Float.MAX_VALUE
                bands.forEachIndexed { i, b ->
                    val bx = ((log10(b.freqHz.toDouble().coerceIn(20.0, 20000.0)) - logMin) / logSpan * w).toFloat()
                    val diff = abs(bx - offset.x)
                    if (diff < minDiff) {
                        minDiff = diff
                        nearestIdx = i
                    }
                }
                if (minDiff < 48.dp.toPx()) {
                    currentOnSelectBand(nearestIdx)
                }
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val logMin = log10(20.0)
        val logMax = log10(20000.0)
        val logSpan = logMax - logMin

        val minDb = -15.0f
        val maxDb = 15.0f
        val dbSpan = maxDb - minDb

        fun freqToX(freq: Double): Float {
            val clamped = freq.coerceIn(20.0, 20000.0)
            val frac = (log10(clamped) - logMin) / logSpan
            return (frac * w).toFloat()
        }

        fun dbToY(db: Float): Float {
            val clamped = db.coerceIn(minDb, maxDb)
            val frac = (maxDb - clamped) / dbSpan
            return frac * h
        }

        // Horizontal dB grid lines (+12, +6, 0, -6, -12 dB) — Light/Dark adaptive
        val gridDbs = floatArrayOf(12f, 6f, 0f, -6f, -12f)
        for (gDb in gridDbs) {
            val y = dbToY(gDb)
            val isZero = gDb == 0f
            drawLine(
                color = if (isZero) zeroGridColor else minorGridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (isZero) 1.2f else 0.8f
            )
        }

        // Vertical octave frequency grid lines — Light/Dark adaptive
        val gridFreqs = doubleArrayOf(31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        for (gFreq in gridFreqs) {
            val x = freqToX(gFreq)
            drawLine(
                color = minorGridColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 0.8f
            )
        }

        val curveColor = if (isEnabled) accentColor else Color.Gray.copy(alpha = 0.55f)

        val curvePath = Path()
        val fillPath = Path()
        for (i in samplePoints.indices) {
            val x = freqToX(samplePoints[i].toDouble())
            val y = dbToY(curveGainValues[i])
            if (i == 0) {
                curvePath.moveTo(x, y)
                fillPath.moveTo(x, dbToY(0f))
                fillPath.lineTo(x, y)
            } else {
                curvePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val lastX = freqToX(samplePoints.last().toDouble())
        fillPath.lineTo(lastX, dbToY(0f))
        fillPath.close()

        // Gradient fill under curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    curveColor.copy(alpha = if (isEnabled) 0.26f else 0.08f),
                    curveColor.copy(alpha = 0.01f)
                ),
                startY = 0f,
                endY = h
            ),
            style = Fill
        )

        // Curve outline
        drawPath(
            path = curvePath,
            color = curveColor,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Interactive dots for each band (high-contrast on both Light & Dark themes)
        val nodeColor = if (isEnabled) accentColor else Color.Gray.copy(alpha = 0.60f)
        bands.forEachIndexed { idx, band ->
            val bx = freqToX(band.freqHz.toDouble())
            val by = dbToY(band.gainDb + preampDb)
            val isSelected = selectedBandIndex == idx

            if (isSelected) {
                drawCircle(
                    color = nodeColor.copy(alpha = 0.32f),
                    radius = 9.dp.toPx(),
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = nodeColor,
                    radius = 5.2.dp.toPx(),
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = ColorContrastHelper.getOnAccentColor(nodeColor),
                    radius = 2.2.dp.toPx(),
                    center = Offset(bx, by)
                )
            } else {
                drawCircle(
                    color = nodeColor,
                    radius = 3.2.dp.toPx(),
                    center = Offset(bx, by)
                )
            }
        }
    }
}
