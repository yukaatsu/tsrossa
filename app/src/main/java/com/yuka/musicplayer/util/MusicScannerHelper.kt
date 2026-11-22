package com.yuka.musicplayer.util

import android.content.Context
import com.yuka.musicplayer.model.FolderBrowserSlice
import com.yuka.musicplayer.model.FolderSortField
import com.yuka.musicplayer.model.MusicFolderNode
import com.yuka.musicplayer.model.Playlist
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortOrder
import java.io.File

/**
 * Facade for Audio Discovery and Folder Browser Slice operations.
 * Preserves 100% backward-compatibility across all existing callers and unit tests
 * while delegating implementation to [AudioDiscoveryEngine] and [FolderBrowserSliceBuilder].
 */
object MusicScannerHelper {

    val SUPPORTED_EXTENSIONS = AudioDiscoveryEngine.SUPPORTED_EXTENSIONS
    val RECOGNIZED_UNSUPPORTED_EXTENSIONS = AudioDiscoveryEngine.RECOGNIZED_UNSUPPORTED_EXTENSIONS
    val ALL_AUDIO_EXTENSIONS = AudioDiscoveryEngine.ALL_AUDIO_EXTENSIONS

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

    val lastScanDiagnostics: ScanDiagnosticsSummary?
        get() = AudioDiscoveryEngine.lastScanDiagnostics

    fun canonicalizeFile(file: File): File =
        AudioDiscoveryEngine.canonicalizeFile(file)

    internal fun escapeSqlLike(input: String): String =
        AudioDiscoveryEngine.escapeSqlLike(input)

    fun findBlockingNomediaFiles(rootDir: File): List<String> =
        AudioDiscoveryEngine.findBlockingNomediaFiles(rootDir)

    internal fun resolveSafDocumentIdToFile(
        docId: String,
        primaryExternalRoot: String = "/storage/emulated/0"
    ): File? = AudioDiscoveryEngine.resolveSafDocumentIdToFile(docId, primaryExternalRoot)

    fun shouldEnterDirectory(dir: File, rootDir: File): Boolean =
        AudioDiscoveryEngine.shouldEnterDirectory(dir, rootDir)

    fun discoverAudioFilesResilient(
        rootDir: File,
        context: Context? = null,
        isForceRescan: Boolean = false
    ): List<File> = AudioDiscoveryEngine.discoverAudioFilesResilient(rootDir, context, isForceRescan)

    fun triggerMediaScannerReindexSync(
        context: Context,
        rootDir: File,
        timeoutMs: Long = 3500L
    ) = AudioDiscoveryEngine.triggerMediaScannerReindexSync(context, rootDir, timeoutMs)

    fun queryMediaStoreForAudioFiles(context: Context, rootDir: File): List<File> =
        AudioDiscoveryEngine.queryMediaStoreForAudioFiles(context, rootDir)

    fun querySafDocumentTreeBridge(context: Context, rootDir: File): List<File> =
        AudioDiscoveryEngine.querySafDocumentTreeBridge(context, rootDir)

    fun scanMusicDirectoryIncremental(
        context: Context,
        rootDir: File,
        cachedItems: List<ScannedMusicItem>? = null
    ): IncrementalScanResult = AudioDiscoveryEngine.scanMusicDirectoryIncremental(context, rootDir, cachedItems)

    fun scanMusicDirectory(
        rootDir: File,
        context: Context? = null,
        isForceRescan: Boolean = false
    ): List<ScannedMusicItem> = AudioDiscoveryEngine.scanMusicDirectory(rootDir, context, isForceRescan)

    fun getRelativeBreadcrumb(file: File, rootDir: File): String =
        AudioDiscoveryEngine.getRelativeBreadcrumb(file, rootDir)

    fun filterPlayableFiles(items: List<ScannedMusicItem>): List<File> =
        AudioDiscoveryEngine.filterPlayableFiles(items)

    fun isSupportedAudioFile(file: File): Boolean =
        AudioDiscoveryEngine.isSupportedAudioFile(file)

    fun buildFolderPlaylists(rootDir: File, scannedItems: List<ScannedMusicItem>): List<Playlist> =
        AudioDiscoveryEngine.buildFolderPlaylists(rootDir, scannedItems)

    fun computeDirectoryStructureFingerprint(rootDir: File): Long =
        AudioDiscoveryEngine.computeDirectoryStructureFingerprint(rootDir)

    fun normalizeFolderPath(rawPath: String): String =
        FolderBrowserSliceBuilder.normalizeFolderPath(rawPath)

    fun sortFolderNodes(
        nodes: List<MusicFolderNode>,
        folderSortField: FolderSortField = FolderSortField.NAME,
        folderSortOrder: SortOrder = SortOrder.ASCENDING
    ): List<MusicFolderNode> = FolderBrowserSliceBuilder.sortFolderNodes(nodes, folderSortField, folderSortOrder)

    fun buildFolderBrowserSliceUnordered(
        rootDir: File,
        currentDir: File,
        scannedItems: List<ScannedMusicItem>
    ): FolderBrowserSlice = FolderBrowserSliceBuilder.buildFolderBrowserSliceUnordered(rootDir, currentDir, scannedItems)

    fun buildFolderBrowserSlice(
        rootDir: File,
        currentDir: File,
        scannedItems: List<ScannedMusicItem>,
        folderSortField: FolderSortField = FolderSortField.NAME,
        folderSortOrder: SortOrder = SortOrder.ASCENDING
    ): FolderBrowserSlice = FolderBrowserSliceBuilder.buildFolderBrowserSlice(rootDir, currentDir, scannedItems, folderSortField, folderSortOrder)
}
