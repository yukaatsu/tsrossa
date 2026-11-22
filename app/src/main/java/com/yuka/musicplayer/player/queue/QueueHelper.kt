package com.yuka.musicplayer.player.queue

import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import java.io.File

/**
 * Pure calculation logic for determining upcoming tracks, shuffle order, and playlist resolution.
 */
object QueueHelper {

    fun indexOfTrack(list: List<File>, track: File?): Int {
        if (track == null) return -1
        val targetPath = track.absolutePath
        return list.indexOfFirst { it.absolutePath == targetPath }
    }

    fun resolvePlaylistTracks(
        playlistTrackPaths: List<String>?,
        fallbackTrackPaths: List<String>,
        supportedFileMap: Map<String, File>
    ): List<File> {
        val paths = if (!playlistTrackPaths.isNullOrEmpty()) {
            playlistTrackPaths
        } else {
            fallbackTrackPaths
        }
        return paths.mapNotNull { path ->
            supportedFileMap[path] ?: File(path).takeIf { it.exists() && it.isFile }
        }
    }

    fun getNextTrackFile(
        isAutoAdvance: Boolean,
        consumeQueue: Boolean,
        repeatMode: Int,
        currentTrack: File?,
        upNextQueue: List<File>,
        activeList: List<File>,
        isShuffleEnabled: Boolean,
        shuffledList: List<File>,
        currentShuffleIndex: Int,
        sequenceAnchorFile: File? = null
    ): File? {
        if (upNextQueue.isNotEmpty()) {
            return upNextQueue.first()
        }

        if (isAutoAdvance && repeatMode == 1 && currentTrack != null) {
            return currentTrack
        }

        if (activeList.isEmpty()) {
            return null
        }

        val referenceTrack = sequenceAnchorFile ?: currentTrack

        if (isShuffleEnabled) {
            if (shuffledList.isNotEmpty()) {
                val effectiveShuffleIdx = if (
                    currentShuffleIndex in shuffledList.indices &&
                    referenceTrack != null &&
                    shuffledList[currentShuffleIndex].absolutePath == referenceTrack.absolutePath
                ) {
                    currentShuffleIndex
                } else {
                    indexOfTrack(shuffledList, referenceTrack)
                }
                if (effectiveShuffleIdx != -1) {
                    return if (effectiveShuffleIdx + 1 < shuffledList.size) {
                        shuffledList[effectiveShuffleIdx + 1]
                    } else if (repeatMode == 2) {
                        shuffledList.first()
                    } else {
                        null
                    }
                }
            }
        }

        val currentIndex = indexOfTrack(activeList, referenceTrack)
        return if (currentIndex != -1 && currentIndex + 1 < activeList.size) {
            activeList[currentIndex + 1]
        } else if (repeatMode == 2 && activeList.isNotEmpty()) {
            activeList.first()
        } else {
            null
        }
    }

    fun getNextTrackFile(
        isAutoAdvance: Boolean,
        consumeQueue: Boolean,
        repeatMode: RepeatMode,
        currentTrack: File?,
        upNextQueue: List<File>,
        activeList: List<File>,
        isShuffleEnabled: Boolean,
        shuffledList: List<File>,
        currentShuffleIndex: Int,
        sequenceAnchorFile: File? = null
    ): File? = getNextTrackFile(
        isAutoAdvance = isAutoAdvance,
        consumeQueue = consumeQueue,
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> 0
            RepeatMode.ONE -> 1
            RepeatMode.ALL -> 2
        },
        currentTrack = currentTrack,
        upNextQueue = upNextQueue,
        activeList = activeList,
        isShuffleEnabled = isShuffleEnabled,
        shuffledList = shuffledList,
        currentShuffleIndex = currentShuffleIndex,
        sequenceAnchorFile = sequenceAnchorFile
    )

    fun calculateUpcomingTracks(
        currentTrack: TrackInfo?,
        activeList: List<File>,
        isShuffleEnabled: Boolean,
        shuffledList: List<File>,
        currentShuffleIndex: Int,
        repeatMode: RepeatMode,
        sequenceAnchorFile: File? = null
    ): List<File> {
        val referenceFile = sequenceAnchorFile ?: currentTrack?.file
        if (isShuffleEnabled && shuffledList.isNotEmpty()) {
            val startIndex = if (referenceFile != null) {
                val idx = indexOfTrack(shuffledList, referenceFile)
                if (idx != -1) idx else currentShuffleIndex
            } else {
                currentShuffleIndex
            }
            val afterCurrent = if (startIndex in shuffledList.indices && startIndex + 1 < shuffledList.size) {
                shuffledList.subList(startIndex + 1, shuffledList.size)
            } else emptyList()

            return if (repeatMode == RepeatMode.ALL && startIndex > 0) {
                afterCurrent + shuffledList.subList(0, startIndex)
            } else {
                afterCurrent
            }
        } else if (activeList.isNotEmpty()) {
            val currentIndex = indexOfTrack(activeList, referenceFile)
            if (currentIndex != -1) {
                val afterCurrent = if (currentIndex + 1 < activeList.size) {
                    activeList.subList(currentIndex + 1, activeList.size)
                } else emptyList()

                return if (repeatMode == RepeatMode.ALL && currentIndex > 0) {
                    afterCurrent + activeList.subList(0, currentIndex)
                } else {
                    afterCurrent
                }
            } else {
                return activeList
            }
        } else {
            return emptyList()
        }
    }

    fun ensureShuffleDeck(
        activeList: List<File>,
        currentFile: File?,
        currentShuffledList: List<File>,
        currentShuffleIndex: Int
    ): Pair<List<File>, Int> {
        if (activeList.isEmpty()) {
            return Pair(emptyList(), -1)
        }
        val activePaths = activeList.map { it.absolutePath }.toSet()
        val deckPaths = currentShuffledList.map { it.absolutePath }.toSet()

        if (currentShuffledList.isNotEmpty() && deckPaths == activePaths) {
            if (currentFile != null) {
                val idx = indexOfTrack(currentShuffledList, currentFile)
                if (idx != -1) {
                    return Pair(currentShuffledList, idx)
                }
            } else {
                return Pair(currentShuffledList, currentShuffleIndex)
            }
        }

        return if (currentFile != null && indexOfTrack(activeList, currentFile) != -1) {
            val remaining = activeList.filter { it.absolutePath != currentFile.absolutePath }.shuffled()
            Pair(listOf(currentFile) + remaining, 0)
        } else {
            Pair(activeList.shuffled(), 0)
        }
    }

    data class NextTrackResult(
        val nextFile: File?,
        val updatedQueue: List<File>,
        val updatedShuffledList: List<File>,
        val updatedShuffleIndex: Int,
        val fromPriorityQueue: Boolean = false
    )

    fun resolveNextTrack(
        isAutoAdvance: Boolean,
        consumeQueue: Boolean,
        priorityQueue: List<File>,
        currentTrack: TrackInfo?,
        repeatMode: RepeatMode,
        activeList: List<File>,
        isShuffleEnabled: Boolean,
        shuffledList: List<File>,
        currentShuffleIndex: Int,
        sequenceAnchorFile: File? = null
    ): NextTrackResult {
        if (priorityQueue.isNotEmpty()) {
            val nextFile = priorityQueue.first()
            val newQueue = if (consumeQueue) priorityQueue.drop(1) else priorityQueue
            return NextTrackResult(nextFile, newQueue, shuffledList, currentShuffleIndex, fromPriorityQueue = true)
        }

        if (isAutoAdvance && repeatMode == RepeatMode.ONE && currentTrack?.file != null) {
            return NextTrackResult(currentTrack.file, priorityQueue, shuffledList, currentShuffleIndex, fromPriorityQueue = false)
        }

        if (activeList.isEmpty()) {
            return NextTrackResult(null, priorityQueue, shuffledList, currentShuffleIndex, fromPriorityQueue = false)
        }

        val referenceTrackFile = sequenceAnchorFile ?: currentTrack?.file

        if (isShuffleEnabled) {
            if (activeList.size == 1) {
                val next = if (repeatMode != RepeatMode.OFF || !isAutoAdvance) activeList.first() else null
                return NextTrackResult(next, priorityQueue, shuffledList, currentShuffleIndex, fromPriorityQueue = false)
            }

            var deck = shuffledList
            var deckIndex = currentShuffleIndex
            val activePaths = activeList.map { it.absolutePath }.toSet()
            val deckPaths = deck.map { it.absolutePath }.toSet()
            if (deck.isEmpty() || deckPaths != activePaths) {
                val ensured = ensureShuffleDeck(activeList, referenceTrackFile, deck, deckIndex)
                deck = ensured.first
                deckIndex = ensured.second
            } else {
                val effectiveShuffleIdx = if (
                    deckIndex in deck.indices &&
                    referenceTrackFile != null &&
                    deck[deckIndex].absolutePath == referenceTrackFile.absolutePath
                ) {
                    deckIndex
                } else {
                    indexOfTrack(deck, referenceTrackFile)
                }
                if (effectiveShuffleIdx != -1) {
                    deckIndex = effectiveShuffleIdx
                }
            }

            val nextIndex = deckIndex + 1
            if (nextIndex < deck.size) {
                val nextFile = deck[nextIndex]
                val newIndex = if (consumeQueue) nextIndex else deckIndex
                return NextTrackResult(nextFile, priorityQueue, deck, newIndex, fromPriorityQueue = false)
            } else if (repeatMode == RepeatMode.ALL || (!isAutoAdvance && repeatMode == RepeatMode.OFF)) {
                if (consumeQueue) {
                    val currentPlaying = referenceTrackFile
                    val candidates = if (activeList.size > 1 && currentPlaying != null) {
                        val withoutCurrent = activeList.filter { it.absolutePath != currentPlaying.absolutePath }
                        withoutCurrent.shuffled() + listOf(currentPlaying)
                    } else {
                        activeList.shuffled()
                    }
                    return NextTrackResult(candidates.firstOrNull(), priorityQueue, candidates, 0, fromPriorityQueue = false)
                } else {
                    return NextTrackResult(deck.firstOrNull(), priorityQueue, deck, deckIndex, fromPriorityQueue = false)
                }
            } else {
                return NextTrackResult(null, priorityQueue, deck, deckIndex, fromPriorityQueue = false)
            }
        }

        val currentIndex = indexOfTrack(activeList, referenceTrackFile)
        val nextFile = if (currentIndex != -1 && currentIndex + 1 < activeList.size) {
            activeList[currentIndex + 1]
        } else if (currentIndex == -1 && activeList.isNotEmpty()) {
            activeList[0]
        } else if (repeatMode == RepeatMode.ALL && activeList.isNotEmpty()) {
            activeList[0]
        } else {
            null
        }

        return NextTrackResult(nextFile, priorityQueue, shuffledList, currentShuffleIndex, fromPriorityQueue = false)
    }

    data class PrevTrackResult(
        val prevFile: File?,
        val updatedShuffleIndex: Int
    )

    fun resolvePrevTrack(
        playbackHistory: MutableList<File>,
        activeList: List<File>,
        currentTrackFile: File?,
        isShuffleEnabled: Boolean,
        shuffledList: List<File>,
        currentShuffleIndex: Int,
        repeatMode: RepeatMode
    ): PrevTrackResult {
        if (activeList.isEmpty()) return PrevTrackResult(null, currentShuffleIndex)

        // 1. History stack
        while (playbackHistory.isNotEmpty()) {
            val prev = playbackHistory.removeAt(playbackHistory.size - 1)
            if (indexOfTrack(activeList, prev) != -1) {
                var newShuffleIdx = currentShuffleIndex
                if (isShuffleEnabled && shuffledList.isNotEmpty()) {
                    val idx = indexOfTrack(shuffledList, prev)
                    if (idx != -1) newShuffleIdx = idx
                }
                return PrevTrackResult(prev, newShuffleIdx)
            }
        }

        // 2. Shuffle mode fallback
        if (isShuffleEnabled && shuffledList.isNotEmpty()) {
            val effectiveShuffleIdx = if (
                currentShuffleIndex in shuffledList.indices &&
                currentTrackFile != null &&
                shuffledList[currentShuffleIndex].absolutePath == currentTrackFile.absolutePath
            ) {
                currentShuffleIndex
            } else {
                indexOfTrack(shuffledList, currentTrackFile)
            }
            return if (effectiveShuffleIdx > 0) {
                PrevTrackResult(shuffledList[effectiveShuffleIdx - 1], effectiveShuffleIdx - 1)
            } else if (repeatMode == RepeatMode.ALL && shuffledList.isNotEmpty()) {
                PrevTrackResult(shuffledList[shuffledList.lastIndex], shuffledList.lastIndex)
            } else {
                PrevTrackResult(currentTrackFile, effectiveShuffleIdx.coerceAtLeast(0))
            }
        }

        // 3. Normal sequential fallback
        val currentIndex = indexOfTrack(activeList, currentTrackFile)
        val prevFile = if (currentIndex > 0) {
            activeList[currentIndex - 1]
        } else if (repeatMode == RepeatMode.ALL && activeList.isNotEmpty()) {
            activeList.last()
        } else {
            currentTrackFile
        }

        return PrevTrackResult(prevFile, currentShuffleIndex)
    }
}
