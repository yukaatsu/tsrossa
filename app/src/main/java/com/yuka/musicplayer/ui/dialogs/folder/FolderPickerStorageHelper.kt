package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.yuka.musicplayer.util.StorageVolumeRoot
import java.io.File

/**
 * Data model for detected folders on device that already contain audio tracks.
 */
data class DetectedAudioFolder(
    val directory: File,
    val displayName: String,
    val displayPath: String,
    val audioCount: Int
)

data class BreadcrumbSegment(
    val title: String,
    val directory: File
)

internal val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "alac", "aif", "aiff", "dsd", "dff", "dsf"
)

/**
 * Rapidly queries MediaStore to find all folders containing audio files and their counts.
 */
internal fun discoverFoldersWithAudio(context: Context): List<DetectedAudioFolder> {
    val folderCounts = linkedMapOf<String, Int>()
    runCatching {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        cursor?.use { c ->
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol != -1) {
                while (c.moveToNext()) {
                    val filePath = c.getString(dataCol) ?: continue
                    val parent = File(filePath).parent ?: continue
                    folderCounts[parent] = (folderCounts[parent] ?: 0) + 1
                }
            }
        }
    }
    val internalStorage = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    return folderCounts.entries.mapNotNull { (path, count) ->
        val dir = File(path)
        if (dir.exists() && dir.isDirectory && dir.canRead()) {
            val shortPath = if (path.startsWith(internalStorage)) {
                "Internal" + path.removePrefix(internalStorage)
            } else path
            DetectedAudioFolder(
                directory = dir,
                displayName = dir.name.ifBlank { shortPath },
                displayPath = shortPath,
                audioCount = count
            )
        } else null
    }.sortedByDescending { it.audioCount }
}

internal fun buildBreadcrumbSegments(currentDir: File, roots: List<StorageVolumeRoot>): List<BreadcrumbSegment> {
    val segments = mutableListOf<BreadcrumbSegment>()
    var curr: File? = currentDir
    while (curr != null) {
        val matchingRoot = roots.firstOrNull { runCatching { it.directory.canonicalPath == curr?.canonicalPath }.getOrDefault(false) }
        val name = when {
            matchingRoot != null -> matchingRoot.label
            curr.parent == null || curr.name.isEmpty() -> "/"
            else -> curr.name
        }
        segments.add(0, BreadcrumbSegment(title = name, directory = curr))
        if (matchingRoot != null) {
            break
        }
        curr = curr.parentFile
    }
    return segments
}

/**
 * Resolves a Storage Access Framework DocumentTree Uri into a physical File path if possible.
 */
internal fun resolvePhysicalPathFromUri(treeUri: Uri): String? {
    return runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri) ?: return null
        val parts = docId.split(":", limit = 2)
        val volumeId = parts.getOrNull(0) ?: return null
        val subPath = parts.getOrNull(1)?.trimStart('/', '\\') ?: ""

        if (volumeId.equals("primary", ignoreCase = true) || volumeId.equals("home", ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
            if (subPath.isNotEmpty()) "$base/$subPath" else base
        } else {
            // Removable volume (e.g. 1A2B-3C4D or USB drive)
            val directPath = if (subPath.isNotEmpty()) "/storage/$volumeId/$subPath" else "/storage/$volumeId"
            if (File(directPath).exists()) {
                directPath
            } else {
                val mntPath = if (subPath.isNotEmpty()) "/mnt/media_rw/$volumeId/$subPath" else "/mnt/media_rw/$volumeId"
                if (File(mntPath).exists()) mntPath else directPath
            }
        }
    }.getOrNull()
}
