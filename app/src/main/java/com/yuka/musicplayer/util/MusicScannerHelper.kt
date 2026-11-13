package com.yuka.musicplayer.util

import android.content.Context
import com.yuka.musicplayer.metadata.AudioTagReader
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.ScannedMusicItem
import java.io.File

object MusicScannerHelper {
    val SUPPORTED_EXTENSIONS = setOf("flac", "wav")

    val RECOGNIZED_UNSUPPORTED_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "opus", "wma", "alac", "aiff", "dsf", "dff", "ape", "wv"
    )

    val ALL_AUDIO_EXTENSIONS = SUPPORTED_EXTENSIONS + RECOGNIZED_UNSUPPORTED_EXTENSIONS

    data class IncrementalScanResult(
        val items: List<ScannedMusicItem>,
        val hasChanges: Boolean
    )

    fun shouldEnterDirectory(dir: File, rootDir: File): Boolean {
        if (dir == rootDir) return true
        // Skip hidden system/cache directories like .thumbnails, .Trash, etc.
        // Inside user-selected rootDir, .nomedia files must NOT block scanning
        // because audiophiles often use .nomedia to isolate album art from standard galleries.
        return !dir.name.startsWith(".")
    }

    /**
     * Performs an incremental scan of [rootDir] backed by [MusicLibraryDatabaseHelper].
     * Unchanged files reuse their SQLite metadata without re-reading disk headers.
     * Only new or modified files invoke [AudioTagReader.readTags].
     */
    fun scanMusicDirectoryIncremental(
        context: Context,
        rootDir: File,
        cachedItems: List<ScannedMusicItem>? = null
    ): IncrementalScanResult {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return IncrementalScanResult(emptyList(), cachedItems?.isNotEmpty() == true)
        }
        val db = MusicLibraryDatabaseHelper.getInstance(context)
        val existingList = cachedItems ?: db.getCachedTracksForRoot(rootDir)
        val existingByPath = HashMap<String, ScannedMusicItem>(existingList.size * 2)
        for (item in existingList) {
            existingByPath[item.file.absolutePath] = item
        }

        val rootPath = rootDir.absolutePath
        val currentFiles = ArrayList<File>(existingList.size.coerceAtLeast(128))

        rootDir.walkTopDown()
            .onEnter { dir ->
                shouldEnterDirectory(dir, rootDir)
            }
            .forEach { file ->
                if (file.isFile && !file.name.startsWith(".")) {
                    val ext = file.extension.lowercase()
                    if (ext in ALL_AUDIO_EXTENSIONS) {
                        currentFiles.add(file)
                    }
                }
            }

        val finalItems = ArrayList<ScannedMusicItem>(currentFiles.size)
        val itemsToUpsert = ArrayList<ScannedMusicItem>()
        val seenPaths = HashSet<String>(currentFiles.size * 2)

        for (file in currentFiles) {
            val absPath = file.absolutePath
            seenPaths.add(absPath)
            val lastMod = file.lastModified()
            val length = file.length()

            val cached = existingByPath[absPath]
            if (cached != null && cached.dateModified == lastMod && cached.fileSize == length) {
                finalItems.add(cached)
            } else {
                val scanned = buildScannedMusicItem(file, rootPath, lastMod, length)
                finalItems.add(scanned)
                itemsToUpsert.add(scanned)
            }
        }

        val deletedPaths = existingByPath.keys.filter { it !in seenPaths }
        val hasChanges = itemsToUpsert.isNotEmpty() || deletedPaths.isNotEmpty() || finalItems.size != existingList.size

        if (hasChanges) {
            db.saveTracksForRoot(rootDir, itemsToUpsert, deletedPaths)
        }

        val sortedDefault = finalItems.sortedWith(
            compareByDescending<ScannedMusicItem> { it.isSupported }
                .thenBy { it.title.lowercase() }
        )
        return IncrementalScanResult(sortedDefault, hasChanges)
    }

    fun scanMusicDirectory(rootDir: File): List<ScannedMusicItem> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
        val rootPath = rootDir.absolutePath
        return rootDir.walkTopDown()
            .onEnter { dir ->
                shouldEnterDirectory(dir, rootDir)
            }
            .filter { file ->
                file.isFile && !file.name.startsWith(".") &&
                    file.extension.lowercase() in ALL_AUDIO_EXTENSIONS
            }
            .map { file ->
                buildScannedMusicItem(file, rootPath, file.lastModified(), file.length())
            }
            .sortedWith(
                compareByDescending<ScannedMusicItem> { it.isSupported }
                    .thenBy { it.file.name.lowercase() }
            )
            .toList()
    }

    fun getRelativeBreadcrumb(file: File, rootDir: File): String {
        val parent = file.parentFile ?: return ""
        val rootPath = rootDir.absolutePath.trimEnd('/', '\\')
        val parentPath = parent.absolutePath.trimEnd('/', '\\')

        if (parentPath == rootPath) {
            return ""
        }
        if (!parentPath.startsWith("$rootPath/") && !parentPath.startsWith("$rootPath\\")) {
            return ""
        }

        val relative = parentPath.removePrefix(rootPath).trim('/', '\\')
        return relative.replace("/", " / ").replace("\\", " / ")
    }

    private fun buildScannedMusicItem(
        file: File,
        rootPath: String,
        lastMod: Long,
        fileSize: Long
    ): ScannedMusicItem {
        val ext = file.extension.lowercase()
        val isSupported = ext in SUPPORTED_EXTENSIONS
        val breadcrumb = getRelativeBreadcrumb(file, File(rootPath))
        val tags = if (isSupported) AudioTagReader.readTags(file) else null
        return ScannedMusicItem.createWithPrecomputedDisplay(
            file = file,
            isSupported = isSupported,
            formatLabel = ext.uppercase(),
            breadcrumb = breadcrumb,
            title = tags?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension.ifEmpty { file.name },
            artist = tags?.artist ?: "",
            album = tags?.album ?: "",
            albumArtist = tags?.albumArtist ?: "",
            genre = tags?.genre ?: "",
            trackNumber = tags?.trackNumber ?: 0,
            durationMs = tags?.durationMs ?: 0L,
            year = tags?.year ?: 0,
            composer = tags?.composer ?: "",
            fileSize = fileSize,
            sampleRateHz = tags?.sampleRate ?: 0,
            bitDepth = tags?.bitsPerSample ?: 0,
            bitrateKbps = tags?.bitrateKbps ?: 0,
            dateAdded = lastMod,
            dateModified = lastMod
        )
    }

    fun filterPlayableFiles(items: List<ScannedMusicItem>): List<File> {
        return items.asSequence()
            .filter { it.isSupported }
            .map { it.file }
            .toList()
    }

    fun isSupportedAudioFile(file: File): Boolean {
        return file.extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    /**
     * Generates virtual playlists from subfolders inside [rootDir] when "Make Folder as Playlist" is enabled.
     */
    fun buildFolderPlaylists(rootDir: File, scannedItems: List<ScannedMusicItem>): List<Playlist> {
        val rootPath = rootDir.absolutePath
        val grouped = LinkedHashMap<String, MutableList<String>>()
        val folderNames = LinkedHashMap<String, String>()

        for (item in scannedItems) {
            if (!item.isSupported) continue
            val parentFile = item.file.parentFile ?: continue
            val parentPath = parentFile.absolutePath
            // Only subfolders inside the selected root directory (or the root directory itself if tracks sit directly inside it)
            if (parentPath == rootPath) continue
            val list = grouped.getOrPut(parentPath) { ArrayList() }
            list.add(item.file.absolutePath)
            if (parentPath !in folderNames) {
                val rel = if (parentPath.startsWith(rootPath)) {
                    parentPath.removePrefix(rootPath).trimStart('/', '\\')
                } else {
                    parentFile.name
                }
                folderNames[parentPath] = rel.ifEmpty { parentFile.name }
            }
        }

        return grouped.entries.map { (folderPath, trackPaths) ->
            Playlist(
                id = "folder:$folderPath",
                name = folderNames[folderPath] ?: folderPath.substringAfterLast('/'),
                trackPaths = trackPaths,
                createdAt = 0L,
                isPinned = false,
                isFolderPlaylist = true,
                folderPath = folderPath
            )
        }.sortedBy { it.name.lowercase() }
    }
}
