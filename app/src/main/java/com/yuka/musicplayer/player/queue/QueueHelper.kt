package com.yuka.musicplayer.player.queue

import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import java.io.File

/**
 * Pure calculation logic for determining upcoming tracks in the Queue panel.
 */
object QueueHelper {

    fun calculateUpcomingTracks(
        currentTrack: TrackInfo?,
        activeList: List<File>,
        isShuffleEnabled: Boolean,
        shuffledList: List<File>,
        currentShuffleIndex: Int,
        repeatMode: RepeatMode
    ): List<File> {
        if (isShuffleEnabled && shuffledList.isNotEmpty()) {
            val startIndex = if (currentTrack?.file != null) {
                val idx = shuffledList.indexOfFirst { it.absolutePath == currentTrack.file.absolutePath }
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
            val currentIndex = activeList.indexOfFirst { it.absolutePath == currentTrack?.file?.absolutePath }
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
                val idx = currentShuffledList.indexOfFirst { it.absolutePath == currentFile.absolutePath }
                if (idx != -1) {
                    return Pair(currentShuffledList, idx)
                }
            } else {
                return Pair(currentShuffledList, currentShuffleIndex)
            }
        }

        return if (currentFile != null && activeList.any { it.absolutePath == currentFile.absolutePath }) {
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
        val updatedShuffleIndex: Int
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
        currentShuffleIndex: Int
    ): NextTrackResult {
        if (priorityQueue.isNotEmpty()) {
            val nextFile = priorityQueue.first()
            val newQueue = if (consumeQueue) priorityQueue.drop(1) else priorityQueue
            return NextTrackResult(nextFile, newQueue, shuffledList, currentShuffleIndex)
        }

        if (isAutoAdvance && repeatMode == RepeatMode.ONE && currentTrack?.file != null) {
            return NextTrackResult(currentTrack.file, priorityQueue, shuffledList, currentShuffleIndex)
        }

        if (activeList.isEmpty()) {
            return NextTrackResult(null, priorityQueue, shuffledList, currentShuffleIndex)
        }

        if (isShuffleEnabled) {
            if (activeList.size == 1) {
                val next = if (repeatMode != RepeatMode.OFF || !isAutoAdvance) activeList.first() else null
                return NextTrackResult(next, priorityQueue, shuffledList, currentShuffleIndex)
            }

            var deck = shuffledList
            var deckIndex = currentShuffleIndex
            val activePaths = activeList.map { it.absolutePath }.toSet()
            val deckPaths = deck.map { it.absolutePath }.toSet()
            if (deck.isEmpty() || deckPaths != activePaths) {
                val ensured = ensureShuffleDeck(activeList, currentTrack?.file, deck, deckIndex)
                deck = ensured.first
                deckIndex = ensured.second
            }

            val nextIndex = deckIndex + 1
            if (nextIndex < deck.size) {
                val nextFile = deck[nextIndex]
                val newIndex = if (consumeQueue) nextIndex else deckIndex
                return NextTrackResult(nextFile, priorityQueue, deck, newIndex)
            } else if (repeatMode == RepeatMode.ALL || (!isAutoAdvance && repeatMode == RepeatMode.OFF)) {
                if (consumeQueue) {
                    val currentPlaying = currentTrack?.file
                    val candidates = if (activeList.size > 1 && currentPlaying != null) {
                        val withoutCurrent = activeList.filter { it.absolutePath != currentPlaying.absolutePath }
                        withoutCurrent.shuffled() + listOf(currentPlaying)
                    } else {
                        activeList.shuffled()
                    }
                    return NextTrackResult(candidates.firstOrNull(), priorityQueue, candidates, 0)
                } else {
                    return NextTrackResult(deck.firstOrNull(), priorityQueue, deck, deckIndex)
                }
            } else {
                return NextTrackResult(null, priorityQueue, deck, deckIndex)
            }
        }

        val currentTrackFile = currentTrack?.file
        val currentIndex = activeList.indexOfFirst {
            it.absolutePath == currentTrackFile?.absolutePath ||
            it.canonicalPath == currentTrackFile?.canonicalPath
        }
        val nextFile = if (currentIndex != -1 && currentIndex + 1 < activeList.size) {
            activeList[currentIndex + 1]
        } else if (currentIndex == -1 && activeList.isNotEmpty()) {
            activeList[0]
        } else if (repeatMode == RepeatMode.ALL && activeList.isNotEmpty()) {
            activeList[0]
        } else {
            null
        }

        return NextTrackResult(nextFile, priorityQueue, shuffledList, currentShuffleIndex)
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
            if (activeList.any { it.absolutePath == prev.absolutePath || it.canonicalPath == prev.canonicalPath }) {
                var newShuffleIdx = currentShuffleIndex
                if (isShuffleEnabled && shuffledList.isNotEmpty()) {
                    val idx = shuffledList.indexOfFirst { it.absolutePath == prev.absolutePath || it.canonicalPath == prev.canonicalPath }
                    if (idx != -1) newShuffleIdx = idx
                }
                return PrevTrackResult(prev, newShuffleIdx)
            }
        }

        // 2. Shuffle mode fallback
        if (isShuffleEnabled && shuffledList.isNotEmpty()) {
            return if (currentShuffleIndex > 0) {
                PrevTrackResult(shuffledList[currentShuffleIndex - 1], currentShuffleIndex - 1)
            } else if (repeatMode == RepeatMode.ALL && shuffledList.isNotEmpty()) {
                PrevTrackResult(shuffledList[shuffledList.lastIndex], shuffledList.lastIndex)
            } else {
                PrevTrackResult(currentTrackFile, currentShuffleIndex)
            }
        }

        // 3. Normal sequential fallback
        val currentIndex = activeList.indexOfFirst {
            it.absolutePath == currentTrackFile?.absolutePath || it.canonicalPath == currentTrackFile?.canonicalPath
        }
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
