package com.yuka.musicplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to extract and blur current system wallpaper for app background.
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

            val scale = 0.25f
            val scaledBmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try {
                    @Suppress("DEPRECATION")
                    val rs = RenderScript.create(context)
                    @Suppress("DEPRECATION")
                    val input = Allocation.createFromBitmap(rs, scaledBmp)
                    @Suppress("DEPRECATION")
                    val output = Allocation.createTyped(rs, input.type)
                    @Suppress("DEPRECATION")
                    val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
                    script.setRadius(25f)
                    script.setInput(input)
                    script.forEach(output)
                    output.copyTo(scaledBmp)
                    rs.destroy()
                } catch (_: Exception) {}
            }

            scaledBmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
