package com.yuka.musicplayer.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.util.WallpaperBlurHelper
import java.io.File

/**
 * Extracts comprehensive track metadata, dominant artwork colors, and pre-blurred low-res backdrop.
 */
fun extractMetadata(file: File): TrackInfo {
    val retriever = MediaMetadataRetriever()
    var title = file.nameWithoutExtension
    var artist = "Unknown Artist"
    var album = "Unknown Album"
    var year = "----"
    var durationSec = 0.0
    var bitmap: Bitmap? = null
    var dominantColor = Color(0xFF00FF00) // Default Terminal Green
    val cachedColor = com.yuka.musicplayer.util.AlbumArtThumbnailCache.getCachedDominantColor(file.absolutePath, file.lastModified())
    if (cachedColor != null) {
        dominantColor = cachedColor
    }

    try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { year = it }
        
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { ms ->
            durationSec = ms / 1000.0
        }

        val artBytes = retriever.embeddedPicture
        if (artBytes != null && artBytes.isNotEmpty() && artBytes.size <= 16 * 1024 * 1024) {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight

            if (origWidth > 0 && origHeight > 0) {
                var sampleSize = 1
                while ((origWidth / sampleSize) > 1024 || (origHeight / sampleSize) > 1024) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOptions)
            }

            if (bitmap != null) {
                if (cachedColor != null) {
                    dominantColor = cachedColor
                } else {
                    try {
                        val paletteBitmap = if (bitmap.width > 112 || bitmap.height > 112) {
                            Bitmap.createScaledBitmap(bitmap, 112, 112, true)
                        } else {
                            bitmap
                        }
                        val palette = Palette.from(paletteBitmap)
                            .maximumColorCount(32)
                            .clearFilters()
                            .generate()
                        val swatches = palette.swatches.map {
                            com.yuka.musicplayer.util.ColorContrastHelper.SwatchData(it.rgb, it.population)
                        }

                        if (swatches.isNotEmpty()) {
                            dominantColor = com.yuka.musicplayer.util.ColorContrastHelper.selectDominantSwatchColor(swatches)
                            com.yuka.musicplayer.util.AlbumArtThumbnailCache.putCachedDominantColor(
                                file.absolutePath,
                                file.lastModified(),
                                dominantColor
                            )
                        }
                        if (paletteBitmap != bitmap) {
                            paletteBitmap.recycle()
                        }
                    } catch (_: Exception) {
                        // Fallback
                    }
                }
            }
        }
    } catch (_: Exception) {
        // Ignore if metadata extraction fails
    } finally {
        retriever.release()
    }

    // MQA Detection: check filename conventions and Vorbis Comment / ID3 header
    val isMqa = detectMqa(file)
    val blurredCoverArt = if (bitmap != null) {
        WallpaperBlurHelper.createBlurredArtworkBackdrop(bitmap, targetSize = 72, blurRadius = 5)
    } else null
    val audioTags = AudioTagReader.readTags(file)
    val codec = when (file.extension.lowercase()) {
        "wav", "wave" -> "WAV"
        "flac" -> "FLAC"
        else -> file.extension.uppercase().ifEmpty { "AUDIO" }
    }

    return TrackInfo(
        file = file,
        title = title,
        artist = artist,
        album = album,
        year = year,
        durationSeconds = if (durationSec > 0.0) durationSec else (audioTags.durationMs / 1000.0),
        coverArt = bitmap,
        dominantColor = dominantColor,
        isMqa = isMqa,
        blurredCoverArt = blurredCoverArt,
        sampleRateHz = audioTags.sampleRate,
        bitDepth = audioTags.bitsPerSample,
        channels = audioTags.channels,
        fileSizeBytes = file.length(),
        explicitCodec = codec,
        explicitBitrateKbps = audioTags.bitrateKbps
    )
}

/**
 * Checks for MQA indicators in filename or raw file header bytes.
 */
fun detectMqa(file: File): Boolean {
    val nameLower = file.name.lowercase()
    if (nameLower.contains("[mqa]") || nameLower.contains(".mqa.") || nameLower.contains("mqa.flac")) {
        return true
    }
    // Inspect first 8KB of FLAC header for Vorbis Comment "MQAENCODER" or "MQA" tag
    if (file.extension.equals("flac", ignoreCase = true) && file.exists() && file.length() > 64) {
        try {
            file.inputStream().use { input ->
                val header = ByteArray(8192)
                val read = input.read(header)
                if (read > 0) {
                    val headerStr = String(header, 0, read, Charsets.ISO_8859_1)
                    if (headerStr.contains("MQAENCODER", ignoreCase = true) ||
                        headerStr.contains("MQA", ignoreCase = false) && headerStr.contains("ORIGINALSAMPLERATE", ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {
            // Non-critical check failure fallback
        }
    }
    return false
}
