package com.yuka.musicplayer.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.yuka.musicplayer.core.prefs.AppPreferences
import com.yuka.musicplayer.metadata.AudioTagReader
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.util.MusicScannerHelper.IncrementalScanResult
import com.yuka.musicplayer.util.MusicScannerHelper.ScanDiagnosticsSummary
import com.yuka.musicplayer.utils.AppLogger
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AudioDiscoveryEngine {
    val SUPPORTED_EXTENSIONS = setOf("flac", "wav")

    val RECOGNIZED_UNSUPPORTED_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "opus", "wma", "alac", "aiff", "dsf", "dff", "ape", "wv"
    )

    val ALL_AUDIO_EXTENSIONS = SUPPORTED_EXTENSIONS + RECOGNIZED_UNSUPPORTED_EXTENSIONS

    @Volatile
    var lastScanDiagnostics: MusicScannerHelper.ScanDiagnosticsSummary? = null

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
        return !dir.name.startsWith(".")
    }

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

        collectFromWalk()
        val l1Count = foundFiles.size

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

        if (isForceRescan || foundFiles.isEmpty()) {
            l2Triggered = true
            triggerMediaScannerReindexSync(context, canonRoot)
            collectFromWalk()
            l2AfterCount = foundFiles.size
        }

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

            if (results.isEmpty()) {
                parseCursor(MediaStore.Files.getContentUri("external"), isFilesUri = true)
            }
        }
        return results
    }

    fun querySafDocumentTreeBridge(context: Context, rootDir: File): List<File> {
        val results = ArrayList<File>()
        runCatching {
            val sp = AppPreferences.get(context)
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

    fun buildScannedMusicItem(
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

    fun buildFolderPlaylists(rootDir: File, scannedItems: List<ScannedMusicItem>): List<Playlist> {
        val rootPath = rootDir.absolutePath
        val grouped = LinkedHashMap<String, MutableList<String>>()
        val folderNames = LinkedHashMap<String, String>()

        for (item in scannedItems) {
            if (!item.isSupported) continue
            val parentFile = item.file.parentFile ?: continue
            val parentPath = parentFile.absolutePath
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
        }
        return hash
    }
}
