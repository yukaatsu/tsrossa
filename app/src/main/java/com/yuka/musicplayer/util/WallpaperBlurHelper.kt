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

            createBlurredArtworkBackdrop(bmp, targetSize = 96, blurRadius = 4).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downscales [source] to a tiny [targetSize]x[targetSize] bitmap and applies a fast 2-pass
     * box/stack blur on the CPU. Drawing the resulting tiny bitmap with ContentScale.Crop
     * produces a silky blur with zero GPU shader cost.
     */
    fun createBlurredArtworkBackdrop(
        source: Bitmap,
        targetSize: Int = 72,
        blurRadius: Int = 5
    ): Bitmap {
        val ratio = source.width.toFloat() / source.height.toFloat().coerceAtLeast(1f)
        val w = if (ratio >= 1f) targetSize else (targetSize * ratio).toInt().coerceAtLeast(16)
        val h = if (ratio >= 1f) (targetSize / ratio).toInt().coerceAtLeast(16) else targetSize

        val small = Bitmap.createScaledBitmap(source, w, h, true)
            .copy(Bitmap.Config.ARGB_8888, true)

        if (blurRadius <= 1) return small

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        val temp = IntArray(w * h)

        // 2-pass separable box blur on tiny 72x72 array (~5,184 pixels -> < 0.3ms on CPU)
        repeat(2) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    var count = 0
                    for (dx in -blurRadius..blurRadius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val c = pixels[rowOffset + nx]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                        count++
                    }
                    temp[rowOffset + x] = (0xFF shl 24) or
                        ((rSum / count) shl 16) or
                        ((gSum / count) shl 8) or
                        (bSum / count)
                }
            }
            // Vertical pass
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    var count = 0
                    for (dy in -blurRadius..blurRadius) {
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val c = temp[ny * w + x]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                        count++
                    }
                    pixels[y * w + x] = (0xFF shl 24) or
                        ((rSum / count) shl 16) or
                        ((gSum / count) shl 8) or
                        (bSum / count)
                }
            }
        }

        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return small
    }
}
