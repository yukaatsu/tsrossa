package com.yuka.musicplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object AlbumArtThumbnailCache {
    private val decodeSemaphore = Semaphore(3)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Allocate up to 48MB for in-RAM thumbnails (each 96x96 RGB_565 bitmap is only ~18KB, holding ~2,500+ tracks in RAM)
    private val cacheSize = (maxMemory / 8).coerceIn(16 * 1024, 48 * 1024)

    // Direct lock-free ImageBitmap RAM map for zero-latency LazyColumn scrolling
    private val ramImageBitmapMap = ConcurrentHashMap<String, ImageBitmap>(512)
    private val negativeCache = ConcurrentHashMap<String, Boolean>(1024)
    private val fileKeyCache = ConcurrentHashMap<String, String>(1024)

    private class BitmapLruCache(private val maxKb: Int) {
        private var currentKb = 0
        private val map = LinkedHashMap<String, Bitmap>(128, 0.75f, true)

        @Synchronized
        fun get(key: String): Bitmap? = map[key]

        @Synchronized
        fun put(key: String, value: Bitmap) {
            val sizeKb = try { value.byteCount / 1024 } catch (_: Throwable) { 18 }
            map.remove(key)?.let { old ->
                currentKb -= try { old.byteCount / 1024 } catch (_: Throwable) { 18 }
            }
            map[key] = value
            currentKb += sizeKb
            val it = map.entries.iterator()
            while (currentKb > maxKb && it.hasNext()) {
                val entry = it.next()
                currentKb -= try { entry.value.byteCount / 1024 } catch (_: Throwable) { 18 }
                ramImageBitmapMap.remove(entry.key)
                it.remove()
            }
        }

        @Synchronized
        fun evictAll() {
            map.clear()
            ramImageBitmapMap.clear()
            currentKb = 0
        }
    }

    private class SimpleLruCache<K, V>(private val maxEntries: Int) {
        private val map = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxEntries
            }
        }
        @Synchronized fun get(key: K): V? = map[key]
        @Synchronized fun put(key: K, value: V) { map[key] = value }
        @Synchronized fun evictAll() { map.clear() }
    }

    private val lruCache = BitmapLruCache(cacheSize)
    private val colorCache = SimpleLruCache<String, Color>(1024)

    @Volatile
    private var diskCacheDir: File? = null

    fun init(context: Context) {
        if (diskCacheDir == null) {
            synchronized(this) {
                if (diskCacheDir == null) {
                    val dir = File(context.cacheDir, "album_art_cache")
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }
                    diskCacheDir = dir
                }
            }
        }
    }

    fun get(filePath: String): Bitmap? = lruCache.get(filePath)

    fun getImageBitmap(filePath: String): ImageBitmap? {
        if (filePath.isEmpty()) return null
        ramImageBitmapMap[filePath]?.let { return it }
        val bmp = lruCache.get(filePath) ?: return null
        val img = bmp.asImageBitmap()
        ramImageBitmapMap[filePath] = img
        return img
    }

    fun isKnownMissingArt(filePath: String): Boolean = negativeCache[filePath] == true

    fun put(filePath: String, bitmap: Bitmap) {
        lruCache.put(filePath, bitmap)
        ramImageBitmapMap[filePath] = bitmap.asImageBitmap()
    }

    fun getCachedDominantColor(filePath: String, lastModified: Long): Color? {
        val key = cacheKey(filePath, lastModified)
        synchronized(colorCache) {
            val ramColor = colorCache.get(key)
            if (ramColor != null && ramColor.alpha > 0.5f) return ramColor
        }
        val dir = diskCacheDir ?: return null
        val colorFile = File(dir, "${key}_color_v4.txt")
        if (colorFile.exists()) {
            try {
                val hex = colorFile.readText().trim()
                if (hex.equals("#00000000", ignoreCase = true) || hex.equals("#000000", ignoreCase = true)) {
                    colorFile.delete()
                    return null
                }
                val parsed = Color(android.graphics.Color.parseColor(hex))
                if (parsed.alpha < 0.5f) {
                    colorFile.delete()
                    return null
                }
                synchronized(colorCache) {
                    colorCache.put(key, parsed)
                }
                return parsed
            } catch (_: Throwable) {
                colorFile.delete()
            }
        }
        return null
    }

    fun putCachedDominantColor(filePath: String, lastModified: Long, color: Color) {
        if (color.alpha < 0.5f) return
        val key = cacheKey(filePath, lastModified)
        synchronized(colorCache) {
            colorCache.put(key, color)
        }
        val dir = diskCacheDir ?: return
        try {
            val colorFile = File(dir, "${key}_color_v4.txt")
            val hex = String.format("#%08X", color.toArgb())
            colorFile.writeText(hex)
        } catch (_: Throwable) {}
    }

    fun cacheKey(filePath: String, lastModified: Long): String {
        val memoryKey = "$filePath:$lastModified"
        fileKeyCache[memoryKey]?.let { return it }
        val raw = "$filePath:$lastModified"
        val computed = try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            (raw.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        }
        if (lastModified > 0L) {
            fileKeyCache[memoryKey] = computed
        }
        return computed
    }

    /**
     * Pre-warms thumbnails into RAM in the background so scrolling through the Library
     * reads directly from [ramImageBitmapMap] with zero disk I/O or recomposition stutter.
     */
    suspend fun prewarmLibraryThumbnails(items: List<ScannedMusicItem>) = withContext(Dispatchers.IO) {
        for (item in items) {
            if (!item.isSupported) continue
            val path = item.file.absolutePath
            if (ramImageBitmapMap.containsKey(path) || negativeCache.containsKey(path)) continue
            if (item.dateModified > 0L) {
                cacheKey(path, item.dateModified)
            }
            loadThumbnail(path, targetSizePx = 96, knownLastModified = item.dateModified)
        }
    }

    suspend fun loadThumbnail(
        filePath: String,
        targetSizePx: Int = 96,
        knownLastModified: Long = 0L
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isEmpty() || negativeCache[filePath] == true) return@withContext null

        lruCache.get(filePath)?.let { bmp ->
            if (!ramImageBitmapMap.containsKey(filePath)) {
                ramImageBitmapMap[filePath] = bmp.asImageBitmap()
            }
            return@withContext bmp
        }

        val dir = diskCacheDir
        val lastMod = if (knownLastModified > 0L) knownLastModified else {
            val f = File(filePath)
            if (!f.exists()) {
                negativeCache[filePath] = true
                return@withContext null
            }
            f.lastModified()
        }
        val key = cacheKey(filePath, lastMod)

        // 1. Check persistent disk cache first (very fast, no MediaMetadataRetriever needed)
        if (dir != null) {
            val noArtMarker = File(dir, "${key}.noart")
            if (noArtMarker.exists()) {
                negativeCache[filePath] = true
                return@withContext null
            }

            val diskThumb = File(dir, "${key}_thumb_96.jpg")
            if (diskThumb.exists() && diskThumb.length() > 0) {
                try {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val decoded = BitmapFactory.decodeFile(diskThumb.absolutePath, opts)
                    if (decoded != null) {
                        put(filePath, decoded)
                        return@withContext decoded
                    }
                } catch (_: Throwable) {}
            }
        }

        // 2. Cache miss: extract embedded picture with bounded concurrency
        decodeSemaphore.withPermit {
            lruCache.get(filePath)?.let { return@withContext it }

            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                val picture = retriever.embeddedPicture
                if (picture == null || picture.isEmpty() || picture.size > 16 * 1024 * 1024) {
                    negativeCache[filePath] = true
                    if (dir != null) {
                        try { File(dir, "${key}.noart").createNewFile() } catch (_: Throwable) {}
                    }
                    return@withContext null
                }

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, options)

                var sampleSize = 1
                val (width, height) = options.outWidth to options.outHeight
                if (width > targetSizePx || height > targetSizePx) {
                    val halfWidth = width / 2
                    val halfHeight = height / 2
                    while ((halfWidth / sampleSize) >= targetSizePx && (halfHeight / sampleSize) >= targetSizePx) {
                        sampleSize *= 2
                    }
                }

                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize.coerceAtLeast(1)
                options.inPreferredConfig = Bitmap.Config.RGB_565

                val decoded = BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                if (decoded != null) {
                    put(filePath, decoded)
                    if (dir != null) {
                        try {
                            val tmp = File(dir, "${key}_thumb_96.tmp")
                            FileOutputStream(tmp).use { fos ->
                                decoded.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                            }
                            tmp.renameTo(File(dir, "${key}_thumb_96.jpg"))
                        } catch (_: Throwable) {}
                    }
                    return@withContext decoded
                } else {
                    negativeCache[filePath] = true
                    return@withContext null
                }
            } catch (_: Throwable) {
                negativeCache[filePath] = true
                null
            } finally {
                try {
                    retriever?.release()
                } catch (_: Throwable) {}
            }
        }
    }

    fun clearCache() {
        lruCache.evictAll()
        negativeCache.clear()
        fileKeyCache.clear()
        synchronized(colorCache) { colorCache.evictAll() }
        diskCacheDir?.let { dir ->
            try {
                dir.listFiles()?.forEach { it.delete() }
            } catch (_: Throwable) {}
        }
    }
}

@Composable
fun TrackAlbumThumbnail(
    filePath: String,
    isCurrentTrack: Boolean,
    @Suppress("UNUSED_PARAMETER") currentTrackArt: Bitmap? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    fallback: @Composable () -> Unit
) {
    val initialImage = remember(filePath) { AlbumArtThumbnailCache.getImageBitmap(filePath) }
    var imageBitmap by remember(filePath) { mutableStateOf(initialImage) }

    if (imageBitmap == null && filePath.isNotEmpty() && !AlbumArtThumbnailCache.isKnownMissingArt(filePath)) {
        LaunchedEffect(filePath) {
            val bmp = AlbumArtThumbnailCache.loadThumbnail(filePath, targetSizePx = 96)
            if (bmp != null) {
                imageBitmap = AlbumArtThumbnailCache.getImageBitmap(filePath) ?: bmp.asImageBitmap()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(m3SurfaceContainerLow()),
        contentAlignment = Alignment.Center
    ) {
        val activeImg = imageBitmap
        if (activeImg != null) {
            Image(
                bitmap = activeImg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.40f)),
                    contentAlignment = Alignment.Center
                ) {
                    fallback()
                }
            }
        } else {
            fallback()
        }
    }
}
