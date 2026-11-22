package com.yuka.musicplayer.util

import com.yuka.musicplayer.model.FolderBrowserSlice
import com.yuka.musicplayer.model.FolderSortField
import com.yuka.musicplayer.model.MusicFolderNode
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.SortOrder
import java.io.File
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashMap

object FolderBrowserSliceBuilder {

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
