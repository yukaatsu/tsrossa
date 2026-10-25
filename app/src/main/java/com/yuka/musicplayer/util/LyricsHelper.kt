package com.yuka.musicplayer.util

import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.charset.Charset

data class LyricLine(
    val timeSec: Double,
    val text: String
)

data class ParsedLyrics(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val plainText: String
)

object LyricsHelper {

    private val TIMESTAMP_REGEX = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val HEADER_TAG_REGEX = Regex("\\[[a-zA-Z]+:[^\\]]*\\]")

    /**
     * Parses an LRC or plain text lyrics string.
     */
    fun parseLrc(content: String): ParsedLyrics {
        if (content.isBlank()) {
            return ParsedLyrics(isSynced = false, lines = emptyList(), plainText = "")
        }

        val rawLines = content.lines()
        val parsedLines = mutableListOf<LyricLine>()

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Skip header metadata tags like [ar:Queen]
            if (HEADER_TAG_REGEX.matches(line)) continue

            // Find all timestamps on this line
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                // Remove timestamps from the text
                var lyricText = line
                for (match in matches) {
                    lyricText = lyricText.replace(match.value, "")
                }
                lyricText = lyricText.trim()

                for (match in matches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3]
                    val fractionSec = when {
                        fractionStr.isEmpty() -> 0.0
                        fractionStr.length == 1 -> fractionStr.toDouble() / 10.0
                        fractionStr.length == 2 -> fractionStr.toDouble() / 100.0
                        else -> fractionStr.toDouble() / 1000.0
                    }
                    val totalSec = minutes * 60.0 + seconds + fractionSec
                    parsedLines.add(LyricLine(timeSec = totalSec, text = lyricText))
                }
            }
        }

        return if (parsedLines.isNotEmpty()) {
            parsedLines.sortBy { it.timeSec }
            ParsedLyrics(
                isSynced = true,
                lines = parsedLines,
                plainText = parsedLines.joinToString("\n") { it.text }
            )
        } else {
            ParsedLyrics(
                isSynced = false,
                lines = emptyList(),
                plainText = content.trim()
            )
        }
    }

    /**
     * Finds the index of the currently active line for a given playback time in seconds.
     * Returns -1 if playback is before the first lyric line.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, currentPositionSec: Double): Int {
        if (lines.isEmpty() || currentPositionSec < lines.first().timeSec) {
            return -1
        }
        var activeIdx = 0
        for (i in lines.indices) {
            if (lines[i].timeSec <= currentPositionSec) {
                activeIdx = i
            } else {
                break
            }
        }
        return activeIdx
    }

    /**
     * Looks for companion .lrc or .txt files or embedded tags for a given audio file.
     */
    fun findLyricsForTrack(trackFile: File?): ParsedLyrics? {
        if (trackFile == null || !trackFile.exists()) return null

        val parentDir = trackFile.parentFile
        val baseName = trackFile.nameWithoutExtension

        // 1. Check companion files in the same directory ($baseName.lrc / $baseName.txt)
        val candidateExtensions = listOf("lrc", "txt", "LRC", "TXT")
        if (parentDir != null && parentDir.exists()) {
            for (ext in candidateExtensions) {
                val companion = File(parentDir, "$baseName.$ext")
                if (companion.exists() && companion.isFile && companion.length() > 0) {
                    val text = runCatching { companion.readText(Charset.forName("UTF-8")) }.getOrNull()
                    if (!text.isNullOrBlank()) {
                        return parseLrc(text)
                    }
                }
            }
        }

        // 2. Check embedded audio tags (FLAC Vorbis Comments, WAV/MP3 ID3 USLT / SYLT / LYRICS)
        try {
            val embedded = com.yuka.musicplayer.metadata.AudioTagReader.extractEmbeddedLyrics(trackFile)
            if (!embedded.isNullOrBlank()) {
                return parseLrc(embedded)
            }
        } catch (_: Exception) {}

        // 3. Check generic lyrics.lrc / lyrics.txt in the album folder
        if (parentDir != null && parentDir.exists()) {
            for (ext in candidateExtensions) {
                val generic = File(parentDir, "lyrics.$ext")
                if (generic.exists() && generic.isFile && generic.length() > 0) {
                    val text = runCatching { generic.readText(Charset.forName("UTF-8")) }.getOrNull()
                    if (!text.isNullOrBlank()) {
                        return parseLrc(text)
                    }
                }
            }
        }

        // 4. Fallback check via MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(trackFile.absolutePath)
            retriever.release()
        } catch (_: Exception) {}

        return null
    }
}
