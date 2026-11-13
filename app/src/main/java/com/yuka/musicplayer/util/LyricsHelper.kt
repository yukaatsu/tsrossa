package com.yuka.musicplayer.util

import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.charset.Charset

enum class VoiceType {
    PRIMARY,    // Lead / Vocalist 1 (v1: or default)
    SECONDARY,  // Duet / Vocalist 2 (v2:)
    ENSEMBLE,   // Together / All Vocalists (v3:)
    BACKGROUND  // Backing Vocal (bg: or [bg:...])
}

data class LyricWord(
    val startTimeSec: Double,
    val endTimeSec: Double,
    val text: String
)

data class LyricLine(
    val timeSec: Double,
    val text: String,
    val endTimeSec: Double = timeSec + 4.0,
    val words: List<LyricWord> = emptyList(),
    val voiceType: VoiceType = VoiceType.PRIMARY,
    val backgroundText: String? = null,
    val backgroundWords: List<LyricWord> = emptyList()
)

data class ParsedLyrics(
    val isSynced: Boolean,
    val isWordSynced: Boolean = false,
    val lines: List<LyricLine>,
    val plainText: String
)

object LyricsHelper {

    private val LINE_TIMESTAMP_REGEX = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val WORD_TIMESTAMP_REGEX = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private val HEADER_TAG_REGEX = Regex("^\\[[a-zA-Z]+:[^\\]]*\\]$")
    private val VOICE_PREFIX_REGEX = Regex("^\\s*(v1|v2|v3|bg)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val INLINE_BG_REGEX = Regex("\\[bg:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE)

    /**
     * Parses an LRC, Enhanced Word-Synced LRC, or plain text lyrics string.
     */
    fun parseLrc(content: String): ParsedLyrics {
        if (content.isBlank()) {
            return ParsedLyrics(isSynced = false, isWordSynced = false, lines = emptyList(), plainText = "")
        }

        val rawLines = content.lines()
        val parsedLines = mutableListOf<LyricLine>()

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Skip header metadata tags like [ar:Queen] or [ti:Song]
            if (HEADER_TAG_REGEX.matches(line)) continue

            // 1. Check for line timestamps [mm:ss.xxx]
            val lineMatches = LINE_TIMESTAMP_REGEX.findAll(line).toList()
            val hasExplicitLineTimestamp = lineMatches.isNotEmpty()

            // 2. Remove line timestamps to analyze content
            var contentWithoutLineTags = line
            for (lm in lineMatches) {
                contentWithoutLineTags = contentWithoutLineTags.replace(lm.value, "")
            }
            contentWithoutLineTags = contentWithoutLineTags.trim()

            // 3. Check for inline background vocals [bg: ...]
            var backgroundText: String? = null
            var backgroundWords: List<LyricWord> = emptyList()
            val bgMatch = INLINE_BG_REGEX.find(contentWithoutLineTags)
            if (bgMatch != null) {
                val bgRaw = bgMatch.groupValues[1]
                contentWithoutLineTags = contentWithoutLineTags.replace(bgMatch.value, "").trim()
                val bgParsed = parseWordsFromText(bgRaw)
                backgroundText = bgParsed.cleanText
                backgroundWords = bgParsed.words
            }

            // 4. Extract voice prefix (v1:, v2:, v3:, bg:)
            var voiceType = VoiceType.PRIMARY
            val voiceMatch = VOICE_PREFIX_REGEX.find(contentWithoutLineTags)
            if (voiceMatch != null) {
                val prefix = voiceMatch.groupValues[1].lowercase()
                voiceType = when (prefix) {
                    "v2" -> VoiceType.SECONDARY
                    "v3" -> VoiceType.ENSEMBLE
                    "bg" -> VoiceType.BACKGROUND
                    else -> VoiceType.PRIMARY
                }
                contentWithoutLineTags = contentWithoutLineTags.replace(voiceMatch.value, "").trim()
            }

            // 5. Parse word timestamps <mm:ss.xxx>
            val wordResult = parseWordsFromText(contentWithoutLineTags)
            val words = wordResult.words
            val cleanLineText = wordResult.cleanText

            // If the line had line timestamps [mm:ss.xx]:
            if (hasExplicitLineTimestamp) {
                for (lm in lineMatches) {
                    val lineTime = parseTimestamp(lm)
                    val endTime = if (words.isNotEmpty()) {
                        words.last().endTimeSec
                    } else {
                        lineTime + 4.0
                    }
                    parsedLines.add(
                        LyricLine(
                            timeSec = lineTime,
                            endTimeSec = endTime,
                            text = cleanLineText,
                            words = words,
                            voiceType = voiceType,
                            backgroundText = backgroundText,
                            backgroundWords = backgroundWords
                        )
                    )
                }
            } else if (words.isNotEmpty() && wordResult.firstTimestampSec != null) {
                // Line has word timestamps <mm:ss.xxx> without [mm:ss.xxx] tag.
                // Check if this line is a continuation (wrapped line) of the previous line!
                val lineTime = wordResult.firstTimestampSec
                val prev = parsedLines.lastOrNull()

                val shouldMergeIntoPrev = prev != null &&
                        prev.words.isNotEmpty() &&
                        voiceMatch == null &&
                        (lineTime - prev.words.last().startTimeSec) in 0.0..2.2

                if (shouldMergeIntoPrev && prev != null) {
                    // Merge continuation words into previous line
                    val updatedPrevWords = prev.words.toMutableList()
                    val lastPrevWord = updatedPrevWords.removeAt(updatedPrevWords.lastIndex)
                    // Connect last word of prev to first word of continuation
                    updatedPrevWords.add(lastPrevWord.copy(endTimeSec = lineTime))
                    updatedPrevWords.addAll(words)

                    val mergedText = if (prev.text.endsWith(" ") || cleanLineText.startsWith(" ")) {
                        prev.text + cleanLineText
                    } else {
                        "${prev.text} $cleanLineText"
                    }.trim()

                    parsedLines[parsedLines.lastIndex] = prev.copy(
                        endTimeSec = words.last().endTimeSec,
                        text = mergedText,
                        words = updatedPrevWords
                    )
                } else {
                    // New line starting with word timestamp
                    parsedLines.add(
                        LyricLine(
                            timeSec = lineTime,
                            endTimeSec = words.last().endTimeSec,
                            text = cleanLineText,
                            words = words,
                            voiceType = voiceType,
                            backgroundText = backgroundText,
                            backgroundWords = backgroundWords
                        )
                    )
                }
            }
        }

        return if (parsedLines.isNotEmpty()) {
            parsedLines.sortBy { it.timeSec }

            // Refine end times for lines with no explicit word end timestamps
            for (i in 0 until parsedLines.size) {
                val curr = parsedLines[i]
                if (curr.words.isEmpty()) {
                    val nextTime = if (i + 1 < parsedLines.size) parsedLines[i + 1].timeSec else (curr.timeSec + 4.0)
                    val estimatedEnd = curr.timeSec + (nextTime - curr.timeSec).coerceIn(1.2, 5.5)
                    parsedLines[i] = curr.copy(endTimeSec = estimatedEnd)
                }
            }

            val hasWords = parsedLines.any { it.words.isNotEmpty() }
            val cleanPlainText = parsedLines.joinToString("\n") { it.text }

            ParsedLyrics(
                isSynced = true,
                isWordSynced = hasWords,
                lines = parsedLines,
                plainText = cleanPlainText
            )
        } else {
            // Plain text without any timestamps
            val cleaned = content.lines()
                .filter { !HEADER_TAG_REGEX.matches(it.trim()) }
                .joinToString("\n") { raw ->
                    raw.replace(LINE_TIMESTAMP_REGEX, "")
                        .replace(WORD_TIMESTAMP_REGEX, "")
                        .replace(VOICE_PREFIX_REGEX, "")
                        .replace(INLINE_BG_REGEX, "")
                        .trim()
                }.trim()

            ParsedLyrics(
                isSynced = false,
                isWordSynced = false,
                lines = emptyList(),
                plainText = cleaned
            )
        }
    }

    private data class RawParsedWords(
        val words: List<LyricWord>,
        val cleanText: String,
        val firstTimestampSec: Double?,
        val lastTimestampSec: Double?
    )

    /**
     * Parses inline word timestamps like: <00:23.146>Touch <00:23.831>my <00:24.114>body <00:24.762>tender<00:25.552>
     */
    private fun parseWordsFromText(text: String): RawParsedWords {
        val matches = WORD_TIMESTAMP_REGEX.findAll(text).toList()
        if (matches.isEmpty()) {
            val clean = text.replace(Regex("<[^>]*>"), "").trim()
            return RawParsedWords(emptyList(), clean, null, null)
        }

        val parsedWords = mutableListOf<LyricWord>()
        var firstTimestamp: Double? = null
        var lastTimestamp: Double? = null

        for (i in matches.indices) {
            val currMatch = matches[i]
            val currTime = parseTimestamp(currMatch)
            if (firstTimestamp == null) firstTimestamp = currTime
            lastTimestamp = currTime

            val textStart = currMatch.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val rawWord = if (textStart <= textEnd) text.substring(textStart, textEnd) else ""

            // If this is the last timestamp and following text is empty, it acts as the end timestamp
            if (rawWord.trim().isEmpty() && i == matches.lastIndex && parsedWords.isNotEmpty()) {
                val lastIdx = parsedWords.lastIndex
                val last = parsedWords.removeAt(lastIdx)
                parsedWords.add(last.copy(endTimeSec = currTime))
                continue
            }

            val nextTime = if (i + 1 < matches.size) {
                parseTimestamp(matches[i + 1])
            } else {
                currTime + 0.6
            }

            if (rawWord.isNotEmpty()) {
                parsedWords.add(
                    LyricWord(
                        startTimeSec = currTime,
                        endTimeSec = maxOf(currTime + 0.05, nextTime),
                        text = rawWord
                    )
                )
            }
        }

        val cleanText = if (parsedWords.isNotEmpty()) {
            parsedWords.joinToString("") { it.text }.trim()
        } else {
            text.replace(WORD_TIMESTAMP_REGEX, "").trim()
        }

        return RawParsedWords(parsedWords, cleanText, firstTimestamp, lastTimestamp)
    }

    private fun parseTimestamp(match: MatchResult): Double {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fractionStr = match.groupValues[3]
        val fractionSec = when {
            fractionStr.isEmpty() -> 0.0
            fractionStr.length == 1 -> fractionStr.toDouble() / 10.0
            fractionStr.length == 2 -> fractionStr.toDouble() / 100.0
            else -> fractionStr.toDouble() / 1000.0
        }
        return minutes * 60.0 + seconds + fractionSec
    }

    /**
     * Finds the index of the currently active line for a given playback time in seconds.
     * Returns -1 if playback is before the first lyric line.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, currentPositionSec: Double): Int {
        if (lines.isEmpty() || currentPositionSec < lines.first().timeSec) {
            return -1
        }
        var activeIdx = -1
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
