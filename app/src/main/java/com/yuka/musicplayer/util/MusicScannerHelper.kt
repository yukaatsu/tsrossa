package com.yuka.musicplayer.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.yuka.musicplayer.metadata.AudioTagReader
import com.yuka.musicplayer.model.FolderBrowserSlice
import com.yuka.musicplayer.model.FolderSortField
import com.yuka.musicplayer.model.MusicFolderNode
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortOrder
import com.yuka.musicplayer.utils.AppLogger
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    data class ScanDiagnosticsSummary(
        val timestampMs: Long,
        val rootPath: String,
        val allFilesAccessGranted: Boolean,
        val isForceRescan: Boolean,
        val layer1WalkCount: Int,
        val layer2ReindexTriggered: Boolean,
        val layer2AfterReindexCount: Int,
        val layer3MediaStoreAdded: Int,
        val layer4SafBridgeAdded: Int,
        val totalDiscovered: Int,
        val blockingNomediaPaths: List<String>,
        val elapsedMs: Long
    )

    @Volatile
    var lastScanDiagnostics: ScanDiagnosticsSummary? = null

    fun canonicalizeFile(file: File): File =
        runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)

    internal fun escapeSqlLike(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    fun findBlockingNomediaFiles(rootDir: File): List<String> {
        val found = ArrayList<String>()
        val seen = HashSet<String>()
        var curr: File? = canonicalizeFile(rootDir)
        var hops = 0
        while (curr != null && hops < 16) {
            val nomedia = File(curr, ".nomedia")
            val key = nomedia.absolutePath
            if (seen.add(key) && runCatching { nomedia.exists() }.getOrDefault(false)) {
                found.add(key)
            }
            val p = curr.absolutePath
            if (p == "/" || p == "/storage" || p == "/storage/emulated" || p == "/storage/emulated/0") {
                break
            }
            if (curr.parentFile?.absolutePath == "/storage") {
                break
            }
            curr = curr.parentFile
            hops++
        }
        return found
    }

    internal fun resolveSafDocumentIdToFile(
        docId: String,
        primaryExternalRoot: String = "/storage/emulated/0"
    ): File? {
        val colonIdx = docId.indexOf(':')
        if (colonIdx <= 0) {
            return if (docId.startsWith("/")) File(docId) else null
        }
        val volumeId = docId.substring(0, colonIdx)
        val relativePath = docId.substring(colonIdx + 1).trimStart('/', '\\')
        val baseDir = when {
            volumeId.equals("primary", ignoreCase = true) ||
                volumeId.equals("home", ignoreCase = true) -> primaryExternalRoot.trimEnd('/', '\\')
            else -> {
                val direct = "/storage/$volumeId"
                val mnt = "/mnt/media_rw/$volumeId"
                if (File(direct).exists()) direct else if (File(mnt).exists()) mnt else direct
            }
        }
        return if (relativePath.isEmpty()) File(baseDir) else File(baseDir, relativePath)
    }

    fun shouldEnterDirectory(dir: File, rootDir: File): Boolean {
        if (dir == rootDir) return true
        // Skip hidden system/cache directories like .thumbnails, .Trash, etc.
        // Inside user-selected rootDir, .nomedia files must NOT block scanning
        // because audiophiles often use .nomedia to isolate album art from standard galleries.
        return !dir.name.startsWith(".")
    }

    /**
     * Performs a 4-layer resilient discovery of audio files:
     * Layer 1: Direct File.walkTopDown() traversal.
     * Layer 2: Active MediaScannerConnection re-indexing (clears stale .nomedia tombstones on Android 11+).
     * Layer 3: MediaStore.Audio.Media & MediaStore.Files prefix query fallback.
     * Layer 4: SAF DocumentsContract treeUri bridge fallback.
     */
    fun discoverAudioFilesResilient(
        rootDir: File,
        context: Context? = null,
        isForceRescan: Boolean = false
    ): List<File> {
        val canonRoot = canonicalizeFile(rootDir)
        if (!canonRoot.exists() || !canonRoot.isDirectory) return emptyList()

        val startTime = SystemClock.elapsedRealtime()
        val foundFiles = LinkedHashMap<String, File>()

        fun collectFromWalk() {
            canonRoot.walkTopDown()
                .onEnter { dir -> shouldEnterDirectory(dir, canonRoot) }
                .forEach { file ->
                    if (file.isFile && !file.name.startsWith(".")) {
                        val ext = file.extension.lowercase()
                        if (ext in ALL_AUDIO_EXTENSIONS) {
                            val canonF = canonicalizeFile(file)
                            foundFiles[canonF.absolutePath] = canonF
                        }
                    }
                }
        }

        // Layer 1: Direct filesystem walk
        collectFromWalk()
        val l1Count = foundFiles.size

        // If no Context is provided (e.g. standalone JVM unit tests), return direct walk results
        if (context == null) {
            val elapsedMs = SystemClock.elapsedRealtime() - startTime
            val blocking = findBlockingNomediaFiles(canonRoot)
            lastScanDiagnostics = ScanDiagnosticsSummary(
                timestampMs = System.currentTimeMillis(),
                rootPath = canonRoot.absolutePath,
                allFilesAccessGranted = false,
                isForceRescan = isForceRescan,
                layer1WalkCount = l1Count,
                layer2ReindexTriggered = false,
                layer2AfterReindexCount = l1Count,
                layer3MediaStoreAdded = 0,
                layer4SafBridgeAdded = 0,
                totalDiscovered = foundFiles.size,
                blockingNomediaPaths = blocking,
                elapsedMs = elapsedMs
            )
            return foundFiles.values.toList()
        }

        val hasAllFilesAccess = isAllFilesAccessGranted(context)
        var l2Triggered = false
        var l2AfterCount = l1Count

        // Layer 2: Active MediaScannerConnection Re-Indexer
        // If it's a manual force rescan OR Layer 1 returned 0 files, trigger active re-indexing
        // to clear any stale .nomedia tombstones (crucial on Samsung One UI Android 11).
        if (isForceRescan || foundFiles.isEmpty()) {
            l2Triggered = true
            triggerMediaScannerReindexSync(context, canonRoot)
            collectFromWalk()
            l2AfterCount = foundFiles.size
        }

        // Layer 3: MediaStore.Audio Fallback Query
        // If still empty or if user does not have All Files Access on Android 10+,
        // query MediaStore to see if audio files are registered under canonRoot
        val preL3 = foundFiles.size
        if (foundFiles.isEmpty() || !hasAllFilesAccess) {
            val msFiles = queryMediaStoreForAudioFiles(context, canonRoot)
            for (f in msFiles) {
                val canonF = canonicalizeFile(f)
                if (canonF.absolutePath !in foundFiles) {
                    foundFiles[canonF.absolutePath] = canonF
                }
            }
        }
        val l3Added = foundFiles.size - preL3

        // Layer 4: SAF DocumentTree Bridge Fallback
        // If still empty, check if user selected a treeUri via + System Picker
        val preL4 = foundFiles.size
        if (foundFiles.isEmpty()) {
            val safFiles = querySafDocumentTreeBridge(context, canonRoot)
            for (f in safFiles) {
                val canonF = canonicalizeFile(f)
                if (canonF.absolutePath !in foundFiles) {
                    foundFiles[canonF.absolutePath] = canonF
                }
            }
        }
        val l4Added = foundFiles.size - preL4

        val elapsedMs = SystemClock.elapsedRealtime() - startTime
        val blockingNomedia = findBlockingNomediaFiles(canonRoot)

        val summary = ScanDiagnosticsSummary(
            timestampMs = System.currentTimeMillis(),
            rootPath = canonRoot.absolutePath,
            allFilesAccessGranted = hasAllFilesAccess,
            isForceRescan = isForceRescan,
            layer1WalkCount = l1Count,
            layer2ReindexTriggered = l2Triggered,
            layer2AfterReindexCount = l2AfterCount,
            layer3MediaStoreAdded = l3Added,
            layer4SafBridgeAdded = l4Added,
            totalDiscovered = foundFiles.size,
            blockingNomediaPaths = blockingNomedia,
            elapsedMs = elapsedMs
        )
        lastScanDiagnostics = summary

        runCatching {
            AppLogger.i(
                "STORAGE",
                "Scan root='${canonRoot.absolutePath}' (exists=${canonRoot.exists()}, canRead=${canonRoot.canRead()}, " +
                    "allFilesAccess=$hasAllFilesAccess) -> ${foundFiles.size} tracks in ${elapsedMs}ms " +
                    "[L1=$l1Count, L2=${if (l2Triggered) l2AfterCount else "skip"}, L3=+$l3Added, L4=+$l4Added, " +
                    "nomediaAncestors=${blockingNomedia.size}]"
            )
        }

        return foundFiles.values.toList()
    }

    /**
     * Actively requests MediaScanner to re-index the root directory and child folders.
     * This flushes stale media_type=0 tombstones left after deleting .nomedia in Android 11+.
     */
    fun triggerMediaScannerReindexSync(
        context: Context,
        rootDir: File,
        timeoutMs: Long = 3500L
    ) {
        runCatching {
            val canonRoot = canonicalizeFile(rootDir)
            val pathsToScan = LinkedHashSet<String>()
            pathsToScan.add(canonRoot.absolutePath)
            canonRoot.walkTopDown()
                .maxDepth(8)
                .onEnter { shouldEnterDirectory(it, canonRoot) }
                .forEach { dir ->
                    if (dir.isDirectory && !dir.name.startsWith(".")) {
                        pathsToScan.add(dir.absolutePath)
                    }
                }

            if (pathsToScan.isNotEmpty()) {
                val batchList = pathsToScan.take(256)
                val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs
                batchList.chunked(32).forEach { chunk ->
                    val remaining = deadlineMs - SystemClock.elapsedRealtime()
                    if (remaining <= 50L) return@forEach
                    val paths = chunk.toTypedArray()
                    val latch = CountDownLatch(paths.size)
                    MediaScannerConnection.scanFile(
                        context,
                        paths,
                        null
                    ) { _, _ ->
                        latch.countDown()
                    }
                    latch.await(remaining.coerceAtMost(1500L), TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    /**
     * Fallback query against MediaStore.Audio.Media & MediaStore.Files for any files residing under [rootDir].
     */
    fun queryMediaStoreForAudioFiles(context: Context, rootDir: File): List<File> {
        val results = ArrayList<File>()
        runCatching {
            val cr = context.contentResolver ?: return emptyList()
            val canonRoot = canonicalizeFile(rootDir)
            val rootPath = canonRoot.absolutePath.trimEnd('/', '\\')
            val escapedPath = escapeSqlLike(rootPath)

            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.DATA} LIKE ? ESCAPE '\\'"
            val selectionArgs = arrayOf("$escapedPath/%")

            fun parseCursor(uri: Uri, isFilesUri: Boolean = false) {
                cr.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataCol >= 0) {
                        val filesToReindex = ArrayList<String>()
                        while (cursor.moveToNext()) {
                            val path = cursor.getString(dataCol)
                            if (!path.isNullOrBlank()) {
                                val canonF = canonicalizeFile(File(path))
                                if (canonF.extension.lowercase() in ALL_AUDIO_EXTENSIONS &&
                                    canonF.absolutePath.startsWith("$rootPath/")
                                ) {
                                    if (canonF.exists() && canonF.canRead()) {
                                        results.add(canonF)
                                    } else if (isFilesUri) {
                                        filesToReindex.add(canonF.absolutePath)
                                    }
                                }
                            }
                        }
                        if (filesToReindex.isNotEmpty()) {
                            val latch = CountDownLatch(filesToReindex.size)
                            MediaScannerConnection.scanFile(
                                context,
                                filesToReindex.toTypedArray(),
                                null
                            ) { _, _ -> latch.countDown() }
                            latch.await(1500L, TimeUnit.MILLISECONDS)
                            for (p in filesToReindex) {
                                val f = File(p)
                                if (f.exists() && f.canRead()) results.add(f)
                            }
                        }
                    }
                }
            }

            parseCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

            // Secondary fallback via MediaStore.Files for files stuck in media_type=0
            if (results.isEmpty()) {
                parseCursor(MediaStore.Files.getContentUri("external"), isFilesUri = true)
            }
        }
        return results
    }

    /**
     * Fallback bridge querying SAF DocumentTree when chosen via + System Picker.
     * Resolves matching audio files and notifies MediaScanner to make them visible to FUSE & C++.
     */
    fun querySafDocumentTreeBridge(context: Context, rootDir: File): List<File> {
        val results = ArrayList<File>()
        runCatching {
            val sp = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
            val treeUriStr = sp.getString("music_root_tree_uri", null) ?: return emptyList()
            val treeUri = Uri.parse(treeUriStr)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri) ?: return emptyList()

            val primaryRoot = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
            val treeRootFile = resolveSafDocumentIdToFile(treeDocId, primaryRoot) ?: return emptyList()
            val canonTarget = canonicalizeFile(rootDir).path.trimEnd('/')
            val canonTree = canonicalizeFile(treeRootFile).path.trimEnd('/')

            val isTreeMatch = canonTarget == canonTree ||
                canonTarget.startsWith("$canonTree/") ||
                canonTree.startsWith("$canonTarget/")
            if (!isTreeMatch) return emptyList()

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            val queue = ArrayDeque<String>()
            queue.add(treeDocId)
            val visitedDirs = HashSet<String>()
            val candidateFiles = ArrayList<File>()
            val maxDirsToVisit = 500

            while (queue.isNotEmpty() && visitedDirs.size < maxDirsToVisit) {
                val currentDocId = queue.removeFirst()
                if (!visitedDirs.add(currentDocId)) continue

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    if (idCol < 0 || nameCol < 0) return@use

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol) ?: continue
                        val name = cursor.getString(nameCol) ?: continue
                        if (name.startsWith(".")) continue
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.addLast(docId)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in ALL_AUDIO_EXTENSIONS) {
                                val candidate = resolveSafDocumentIdToFile(docId, primaryRoot) ?: continue
                                val candCanon = canonicalizeFile(candidate).path
                                if (candCanon.startsWith("$canonTarget/") || candCanon == canonTarget) {
                                    candidateFiles.add(candidate)
                                }
                            }
                        }
                    }
                }
            }

            if (candidateFiles.isEmpty()) return emptyList()

            val needScan = candidateFiles.filter { !it.exists() || !it.canRead() }
            if (needScan.isNotEmpty()) {
                val deadlineMs = SystemClock.elapsedRealtime() + 4000L
                needScan.chunked(64).forEach { chunk ->
                    val remaining = deadlineMs - SystemClock.elapsedRealtime()
                    if (remaining <= 100L) return@forEach
                    val paths = chunk.map { it.absolutePath }.toTypedArray()
                    val latch = CountDownLatch(paths.size)
                    MediaScannerConnection.scanFile(context, paths, null) { _, _ ->
                        latch.countDown()
                    }
                    latch.await(remaining.coerceAtMost(2500L), TimeUnit.MILLISECONDS)
                }
            }

            for (candidate in candidateFiles) {
                val canonF = canonicalizeFile(candidate)
                if (canonF.exists() && canonF.canRead()) {
                    results.add(canonF)
                }
            }
        }
        return results
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
        val currentFiles = discoverAudioFilesResilient(rootDir, context, isForceRescan = false)

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

    fun scanMusicDirectory(
        rootDir: File,
        context: Context? = null,
        isForceRescan: Boolean = false
    ): List<ScannedMusicItem> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
        val rootPath = rootDir.absolutePath
        val currentFiles = discoverAudioFilesResilient(rootDir, context, isForceRescan)
        return currentFiles
            .map { file ->
                buildScannedMusicItem(file, rootPath, file.lastModified(), file.length())
            }
            .sortedWith(
                compareByDescending<ScannedMusicItem> { it.isSupported }
                    .thenBy { it.file.name.lowercase() }
            )
    }

    fun getRelativeBreadcrumb(file: File, rootDir: File): String {
        val parent = file.parentFile ?: return ""
        val rootPath = canonicalizeFile(rootDir).path.trimEnd('/', '\\')
        val parentPath = canonicalizeFile(parent).path.trimEnd('/', '\\')

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
        val canonFile = canonicalizeFile(file)
        val ext = canonFile.extension.lowercase()
        val isSupported = ext in SUPPORTED_EXTENSIONS
        val breadcrumb = getRelativeBreadcrumb(canonFile, File(rootPath))
        val tags = if (isSupported) AudioTagReader.readTags(canonFile) else null
        return ScannedMusicItem.createWithPrecomputedDisplay(
            file = canonFile,
            isSupported = isSupported,
            formatLabel = ext.uppercase(),
            breadcrumb = breadcrumb,
            title = tags?.title?.takeIf { it.isNotBlank() } ?: canonFile.nameWithoutExtension.ifEmpty { canonFile.name },
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

    /**
     * Computes a fast CRC/XOR fingerprint of directory timestamps, names, and sizes.
     * Useful for detecting background folder/file structure changes without full tag parsing.
     */
    fun computeDirectoryStructureFingerprint(rootDir: File): Long {
        if (!rootDir.exists() || !rootDir.isDirectory) return 0L
        var hash = 1125899906842597L
        try {
            rootDir.walkTopDown()
                .onEnter { dir -> shouldEnterDirectory(dir, rootDir) }
                .forEach { file ->
                    if (!file.name.startsWith(".")) {
                        hash = 31L * hash + file.name.hashCode()
                        hash = 31L * hash + (file.lastModified() xor (file.length() shl 16))
                    }
                }
        } catch (_: Exception) {
            // Safe traversal fallback
        }
        return hash
    }

    /**
     * Pure-RAM path normalizer (ZERO disk/FUSE syscalls).
     * Harmonizes trailing slashes, Windows separators, and the Android /sdcard -> /storage/emulated/0 alias.
     */
    fun normalizeFolderPath(rawPath: String): String {
        var p = rawPath.replace('\\', '/').trimEnd('/')
        if (p == "/sdcard") {
            p = "/storage/emulated/0"
        } else if (p.startsWith("/sdcard/")) {
            p = "/storage/emulated/0" + p.substring("/sdcard".length)
        }
        return if (p.isEmpty()) "/" else p
    }

    private class SubfolderAccumulator(
        val name: String,
        val directoryPath: String,
        val relativePath: String
    ) {
        val directFiles = ArrayList<File>()
        val recursivePlayableFiles = ArrayList<File>()
        var recursiveCount: Int = 0
        val childSubfolderNames = HashSet<String>()
        var lastModified: Long = 0L
    }

    /**
     * Pure-RAM O(K log K) folder sorter.
     * Decoupled from folder structure indexing so changing sort criteria in the UI never rebuilds
     * the folder tree or touches storage.
     */
    fun sortFolderNodes(
        nodes: List<MusicFolderNode>,
        folderSortField: FolderSortField = FolderSortField.NAME,
        folderSortOrder: SortOrder = SortOrder.ASCENDING
    ): List<MusicFolderNode> {
        if (nodes.size <= 1) return nodes
        val comparator: Comparator<MusicFolderNode> = when (folderSortField) {
            FolderSortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            FolderSortField.TRACK_COUNT -> compareBy { it.recursiveMusicCount }
            FolderSortField.DATE_MODIFIED -> compareBy { it.lastModified }
        }
        return if (folderSortOrder == SortOrder.ASCENDING) {
            nodes.sortedWith(comparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            nodes.sortedWith(comparator.reversed().thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    /**
     * Pure-RAM Single-Pass Folder Indexer (O(N), ZERO disk/FUSE syscalls).
     * Constructs subfolders, directTracks, recursive playable track lists, child folder counts,
     * and lastModified timestamps directly from in-memory [scannedItems].
     */
    fun buildFolderBrowserSliceUnordered(
        rootDir: File,
        currentDir: File,
        scannedItems: List<ScannedMusicItem>
    ): FolderBrowserSlice {
        var rootPath = normalizeFolderPath(rootDir.path)
        var reqPath = normalizeFolderPath(currentDir.path)

        // Pure-RAM macOS /var -> /private/var symlink alignment for JVM unit tests without syscalls
        if (rootPath.startsWith("/var/")) {
            val firstParent = scannedItems.firstOrNull()?.let {
                normalizeFolderPath(it.parentPath.ifEmpty { it.file.parent ?: "" })
            }
            if (firstParent != null && firstParent.startsWith("/private/var/")) {
                rootPath = "/private$rootPath"
                if (reqPath.startsWith("/var/")) {
                    reqPath = "/private$reqPath"
                }
            }
        }

        val rootPrefix = if (rootPath == "/") "/" else "$rootPath/"
        val targetPath = if (reqPath == rootPath || reqPath.startsWith(rootPrefix)) {
            reqPath
        } else {
            rootPath
        }

        val targetDir = File(targetPath)
        val isRoot = (targetPath == rootPath)
        val parentDir = if (!isRoot) targetDir.parentFile else null
        val targetPrefix = if (targetPath == "/") "/" else "$targetPath/"

        val directTracks = ArrayList<ScannedMusicItem>()
        val allRecursivePlayableFiles = ArrayList<File>()
        val subfolderMap = LinkedHashMap<String, SubfolderAccumulator>()
        var totalRecursive = 0

        for (item in scannedItems) {
            val rawParent = item.parentPath.ifEmpty { item.file.parent ?: "" }
            if (rawParent.isEmpty()) continue
            val itemParent = normalizeFolderPath(rawParent)

            if (itemParent == targetPath) {
                directTracks.add(item)
                totalRecursive++
                if (item.isSupported) {
                    allRecursivePlayableFiles.add(item.file)
                }
            } else if (itemParent.startsWith(targetPrefix)) {
                totalRecursive++
                if (item.isSupported) {
                    allRecursivePlayableFiles.add(item.file)
                }

                val rel = itemParent.substring(targetPrefix.length)
                if (rel.isEmpty()) continue

                val slashIdx = rel.indexOf('/')
                val childName = if (slashIdx == -1) rel else rel.substring(0, slashIdx)
                if (childName.isEmpty() || childName.startsWith(".")) continue

                val acc = subfolderMap.getOrPut(childName) {
                    val dirPath = "$targetPrefix$childName"
                    val relToRoot = if (dirPath.startsWith(rootPrefix)) {
                        dirPath.substring(rootPrefix.length)
                    } else {
                        childName
                    }
                    SubfolderAccumulator(
                        name = childName,
                        directoryPath = dirPath,
                        relativePath = relToRoot
                    )
                }

                acc.recursiveCount++
                if (item.isSupported) {
                    acc.recursivePlayableFiles.add(item.file)
                }
                if (item.dateModified > acc.lastModified) {
                    acc.lastModified = item.dateModified
                }

                if (slashIdx == -1) {
                    if (item.isSupported) {
                        acc.directFiles.add(item.file)
                    }
                } else {
                    val rest = rel.substring(slashIdx + 1)
                    val nextSlash = rest.indexOf('/')
                    val grandChildName = if (nextSlash == -1) rest else rest.substring(0, nextSlash)
                    if (grandChildName.isNotEmpty() && !grandChildName.startsWith(".")) {
                        acc.childSubfolderNames.add(grandChildName)
                    }
                }
            }
        }

        val subfolderNodes = subfolderMap.values.map { acc ->
            MusicFolderNode(
                directory = File(acc.directoryPath),
                directoryPath = acc.directoryPath,
                relativePath = acc.relativePath,
                name = acc.name,
                directMusicFiles = acc.directFiles,
                recursivePlayableFiles = acc.recursivePlayableFiles,
                directMusicCount = acc.directFiles.size,
                recursiveMusicCount = acc.recursiveCount,
                subfoldersCount = acc.childSubfolderNames.size,
                lastModified = acc.lastModified
            )
        }

        return FolderBrowserSlice(
            currentDirectory = targetDir,
            isRoot = isRoot,
            parentDirectory = parentDir,
            subfolders = subfolderNodes,
            directTracks = directTracks,
            allRecursivePlayableFiles = allRecursivePlayableFiles,
            totalDirectMusicCount = directTracks.size,
            totalRecursiveMusicCount = totalRecursive
        )
    }

    /**
     * Builds a sorted real-time slice for Folder-Based Music Library navigation in pure RAM (ZERO FUSE syscalls).
     */
    fun buildFolderBrowserSlice(
        rootDir: File,
        currentDir: File,
        scannedItems: List<ScannedMusicItem>,
        folderSortField: FolderSortField = FolderSortField.NAME,
        folderSortOrder: SortOrder = SortOrder.ASCENDING
    ): FolderBrowserSlice {
        val rawSlice = buildFolderBrowserSliceUnordered(
            rootDir = rootDir,
            currentDir = currentDir,
            scannedItems = scannedItems
        )
        return rawSlice.copy(
            subfolders = sortFolderNodes(rawSlice.subfolders, folderSortField, folderSortOrder)
        )
    }
}
