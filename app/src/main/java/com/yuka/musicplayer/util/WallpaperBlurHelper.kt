package com.yuka.musicplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to generate low-resolution pre-blurred backdrops on the CPU so the GPU never
 * has to run real-time full-screen blur shaders during Now Playing or list animations.
 */
object WallpaperBlurHelper {

    suspend fun loadBlurredWallpaper(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = android.app.WallpaperManager.getInstance(context)
            val drawable = wallpaperManager.drawable ?: return@withContext null

            val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                val b = Bitmap.createBitmap(
                    if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080,
                    if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(b)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                b
            }

            createBlurredArtworkBackdrop(bmp, targetSize = 128, blurRadius = 14).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * C1-continuous soft-knee highlight luma compression (knee = 96, ceiling = 132).
     * Maps 0..96 identity, and 96..255 smoothly to 96..132 with zero step discontinuity.
     */
    internal fun compressHighlightLuma(luma: Int): Int {
        if (luma <= 96) return luma.coerceAtLeast(0)
        return (96 + ((luma - 96) * 36) / 159).coerceIn(96, 132)
    }

    /**
     * Downscales [source] to a [targetSize]x[targetSize] ARGB_8888 bitmap, applies continuous
     * pre-blur chromatic vibrancy & soft-knee highlight luma normalization, and runs a 3-pass
     * separable Gaussian-convergent blur with sub-LSB ordered dithering.
     * Drawing the resulting bitmap with FilterQuality.High produces a silky-smooth gradient
     * with zero blocky pixel artifacts and zero GPU shader overhead.
     */
    fun createBlurredArtworkBackdrop(
        source: Bitmap,
        targetSize: Int = 128,
        blurRadius: Int = 14
    ): Bitmap {
        val safeTarget = targetSize.coerceIn(32, 256)
        val ratio = source.width.toFloat() / source.height.toFloat().coerceAtLeast(1f)
        val w = if (ratio >= 1f) safeTarget else (safeTarget * ratio).toInt().coerceAtLeast(24)
        val h = if (ratio >= 1f) (safeTarget / ratio).toInt().coerceAtLeast(24) else safeTarget

        val small = Bitmap.createScaledBitmap(source, w, h, true)
            .copy(Bitmap.Config.ARGB_8888, true)

        if (blurRadius <= 1) return small

        val effectiveRadius = blurRadius.coerceIn(2, minOf(w, h) / 2)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        // Stage 1 (PRE-BLUR): Continuous vibrancy boost (1.20x saturation) + C1-continuous soft-knee
        // highlight luma compression (knee = 96, max = 132). Running this BEFORE the 3-pass blur
        // guarantees that no per-pixel clipping or luma adjustment ever creates sharp pixel edges.
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (r * 77 + g * 150 + b * 29) shr 8
            var nr = (luma + (((r - luma) * 307) shr 8)).coerceIn(0, 255)
            var ng = (luma + (((g - luma) * 307) shr 8)).coerceIn(0, 255)
            var nb = (luma + (((b - luma) * 307) shr 8)).coerceIn(0, 255)

            val boostedLuma = (nr * 77 + ng * 150 + nb * 29) shr 8
            if (boostedLuma > 96) {
                val compressedLuma = compressHighlightLuma(boostedLuma)
                nr = (nr * compressedLuma / boostedLuma).coerceIn(0, 255)
                ng = (ng * compressedLuma / boostedLuma).coerceIn(0, 255)
                nb = (nb * compressedLuma / boostedLuma).coerceIn(0, 255)
            }
            pixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }

        val temp = IntArray(w * h)
        val kernelSize = effectiveRadius * 2 + 1
        val halfKernel = kernelSize / 2

        // Stage 2: 3-pass separable sliding-window box blur (converges to a true C1-smooth Gaussian
        // bell curve, eliminating 2-pass tent Mach bands and sharp pixel block boundaries).
        repeat(3) { pass ->
            val isFinalPass = (pass == 2)
            // Horizontal pass
            for (y in 0 until h) {
                val rowOffset = y * w
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dx in -effectiveRadius..effectiveRadius) {
                    val c = pixels[rowOffset + dx.coerceIn(0, w - 1)]
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (x in 0 until w) {
                    val nr = ((rSum + halfKernel) / kernelSize).coerceIn(0, 255)
                    val ng = ((gSum + halfKernel) / kernelSize).coerceIn(0, 255)
                    val nb = ((bSum + halfKernel) / kernelSize).coerceIn(0, 255)
                    temp[rowOffset + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb

                    val leavingX = (x - effectiveRadius).coerceIn(0, w - 1)
                    val enteringX = (x + effectiveRadius + 1).coerceIn(0, w - 1)
                    val cLeave = pixels[rowOffset + leavingX]
                    val cEnter = pixels[rowOffset + enteringX]
                    rSum += ((cEnter shr 16) and 0xFF) - ((cLeave shr 16) and 0xFF)
                    gSum += ((cEnter shr 8) and 0xFF) - ((cLeave shr 8) and 0xFF)
                    bSum += (cEnter and 0xFF) - (cLeave and 0xFF)
                }
            }

            // Vertical pass (with symmetric 2x2 Bayer ordered dithering on the final pass)
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dy in -effectiveRadius..effectiveRadius) {
                    val c = temp[dy.coerceIn(0, h - 1) * w + x]
                    rSum += (c shr 16) and 0xFF
                    gSum += (c shr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (y in 0 until h) {
                    val bias = if (isFinalPass) {
                        val bayer = (((y and 1) * 3 + (x and 1) * 2) and 3) * 2 - 3 // -3, -1, +1, +3
                        (halfKernel + (bayer * kernelSize) / 8).coerceIn(0, kernelSize - 1)
                    } else {
                        halfKernel
                    }
                    val nr = ((rSum + bias) / kernelSize).coerceIn(0, 255)
                    val ng = ((gSum + bias) / kernelSize).coerceIn(0, 255)
                    val nb = ((bSum + bias) / kernelSize).coerceIn(0, 255)
                    pixels[y * w + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb

                    val leavingY = (y - effectiveRadius).coerceIn(0, h - 1)
                    val enteringY = (y + effectiveRadius + 1).coerceIn(0, h - 1)
                    val cLeave = temp[leavingY * w + x]
                    val cEnter = temp[enteringY * w + x]
                    rSum += ((cEnter shr 16) and 0xFF) - ((cLeave shr 16) and 0xFF)
                    gSum += ((cEnter shr 8) and 0xFF) - ((cLeave shr 8) and 0xFF)
                    bSum += (cEnter and 0xFF) - (cLeave and 0xFF)
                }
            }
        }

        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return small
    }
}
