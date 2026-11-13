package com.yuka.musicplayer.metadata

import com.yuka.musicplayer.util.TrackTitleParser
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

data class AudioFileTags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "",
    val trackNumber: Int = 0,
    val year: Int = 0,
    val composer: String = "",
    val durationMs: Long = 0L,
    val lyrics: String? = null,
    val sampleRate: Int = 0,
    val bitsPerSample: Int = 0,
    val channels: Int = 2,
    val bitrateKbps: Int = 0
)

object AudioTagReader {

    fun readTags(file: File): AudioFileTags {
        if (!file.exists() || !file.isFile || file.length() < 12) {
            return fallbackTags(file)
        }

        return try {
            val ext = file.extension.lowercase()
            when (ext) {
                "flac" -> readFlacTags(file)
                "wav", "wave" -> readWavTags(file)
                "mp3" -> readId3Tags(file)
                else -> fallbackTags(file)
            }
        } catch (_: Exception) {
            fallbackTags(file)
        }
    }

    fun extractEmbeddedLyrics(file: File): String? {
        val tags = readTags(file)
        return tags.lyrics?.trim()?.ifEmpty { null }
    }

    private fun fallbackTags(file: File): AudioFileTags {
        val (parsedTitle, parsedArtist) = TrackTitleParser.parse(file.nameWithoutExtension)
        val title = parsedTitle.ifBlank { file.nameWithoutExtension }
        val artist = parsedArtist.ifBlank { "Unknown Artist" }
        val parent = file.parentFile
        val album = if (parent != null && parent.name.isNotBlank() && !parent.name.equals("Music", ignoreCase = true)) {
            parent.name
        } else {
            "Unknown Album"
        }
        return AudioFileTags(
            title = title,
            artist = artist,
            album = album
        )
    }

    // ==========================================
    // FLAC PARSER (fLaC)
    // ==========================================
    private fun readFlacTags(file: File): AudioFileTags {
        file.inputStream().buffered().use { stream ->
            val magic = ByteArray(4)
            if (stream.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "fLaC") {
                return fallbackTags(file)
            }

            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var albumArtist: String? = null
            var genre: String? = null
            var trackNumber = 0
            var year = 0
            var composer: String? = null
            var durationMs = 0L
            var lyrics: String? = null
            var flacSampleRate = 0
            var flacBitsPerSample = 0
            var flacChannels = 2

            var isLast = false
            while (!isLast) {
                val headerByte = stream.read()
                if (headerByte == -1) break
                isLast = (headerByte and 0x80) != 0
                val blockType = headerByte and 0x7F

                val length = (stream.read() shl 16) or (stream.read() shl 8) or stream.read()
                if (length < 0) break

                when (blockType) {
                    0 -> {
                        // STREAMINFO block (34 bytes)
                        if (length >= 34) {
                            val data = ByteArray(length)
                            stream.readFully(data)
                            val b10 = data[10].toInt() and 0xFF
                            val b11 = data[11].toInt() and 0xFF
                            val b12 = data[12].toInt() and 0xFF
                            val b13 = data[13].toInt() and 0xFF
                            flacSampleRate = (b10 shl 12) or (b11 shl 4) or ((b12 ushr 4) and 0x0F)
                            flacChannels = ((b12 ushr 1) and 0x07) + 1
                            flacBitsPerSample = (((b12 and 1) shl 4) or ((b13 ushr 4) and 0x0F)) + 1

                            val s13 = data[13].toLong() and 0x0F
                            val s14 = data[14].toLong() and 0xFF
                            val s15 = data[15].toLong() and 0xFF
                            val s16 = data[16].toLong() and 0xFF
                            val s17 = data[17].toLong() and 0xFF
                            val totalSamples = (s13 shl 32) or (s14 shl 24) or (s15 shl 16) or (s16 shl 8) or s17

                            if (flacSampleRate > 0) {
                                durationMs = (totalSamples * 1000L) / flacSampleRate
                            }
                        } else {
                            stream.skipFully(length.toLong())
                        }
                    }
                    4 -> {
                        // VORBIS_COMMENT block
                        val data = ByteArray(length)
                        stream.readFully(data)
                        var offset = 0

                        if (offset + 4 <= length) {
                            val vendorLen = readLeInt(data, offset)
                            offset += 4 + vendorLen

                            if (offset + 4 <= length) {
                                val commentCount = readLeInt(data, offset)
                                offset += 4

                                val maxComments = minOf(commentCount, 1000)
                                for (i in 0 until maxComments) {
                                    if (offset + 4 > length) break
                                    val commentLen = readLeInt(data, offset)
                                    offset += 4
                                    if (offset + commentLen > length || commentLen < 0) break
                                    val comment = String(data, offset, commentLen, Charsets.UTF_8)
                                    offset += commentLen

                                    val eqIdx = comment.indexOf('=')
                                    if (eqIdx != -1) {
                                        val key = comment.substring(0, eqIdx).trim().uppercase()
                                        val value = comment.substring(eqIdx + 1).trim()
                                        when (key) {
                                            "TITLE" -> if (title == null) title = value
                                            "ARTIST" -> if (artist == null) artist = value
                                            "ALBUM" -> if (album == null) album = value
                                            "ALBUMARTIST", "ALBUM_ARTIST" -> if (albumArtist == null) albumArtist = value
                                            "GENRE" -> if (genre == null) genre = value
                                            "DATE", "YEAR" -> {
                                                if (year == 0) {
                                                    year = extractYear(value)
                                                }
                                            }
                                            "TRACKNUMBER", "TRACK" -> {
                                                if (trackNumber == 0) {
                                                    trackNumber = value.split("/")[0].trim().toIntOrNull() ?: 0
                                                }
                                            }
                                            "COMPOSER" -> if (composer == null) composer = value
                                            "LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS" -> {
                                                if (lyrics == null && value.isNotBlank()) lyrics = value
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        stream.skipFully(length.toLong())
                    }
                }
            }

            val fallback = fallbackTags(file)
            val bitrateKbps = if (durationMs > 0) {
                ((file.length() * 8.0) / (durationMs / 1000.0) / 1000.0).toInt()
            } else 0

            return AudioFileTags(
                title = title?.ifBlank { null } ?: fallback.title,
                artist = artist?.ifBlank { null } ?: fallback.artist,
                album = album?.ifBlank { null } ?: fallback.album,
                albumArtist = albumArtist ?: "",
                genre = genre ?: "",
                trackNumber = trackNumber,
                year = year,
                composer = composer ?: "",
                durationMs = durationMs,
                lyrics = lyrics,
                sampleRate = flacSampleRate,
                bitsPerSample = flacBitsPerSample,
                channels = flacChannels,
                bitrateKbps = bitrateKbps
            )
        }
    }

    // ==========================================
    // WAV PARSER (RIFF / WAVE)
    // ==========================================
    private fun readWavTags(file: File): AudioFileTags {
        file.inputStream().buffered().use { stream ->
            val riffHeader = ByteArray(12)
            if (stream.read(riffHeader) != 12) return fallbackTags(file)
            val riffMagic = String(riffHeader, 0, 4, Charsets.US_ASCII)
            val waveMagic = String(riffHeader, 8, 4, Charsets.US_ASCII)
            if (riffMagic != "RIFF" || waveMagic != "WAVE") return fallbackTags(file)

            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var genre: String? = null
            var year = 0
            var trackNumber = 0
            var byteRate = 0
            var dataBytes = 0L
            var lyrics: String? = null
            var wavChannels = 2
            var wavSampleRate = 0
            var wavBitsPerSample = 16

            val chunkHeader = ByteArray(8)
            while (stream.read(chunkHeader) == 8) {
                val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val chunkSize = readLeInt(chunkHeader, 4).toLong() and 0xFFFFFFFFL
                if (chunkSize < 0) break

                when (chunkId) {
                    "fmt " -> {
                        if (chunkSize >= 16) {
                            val fmtData = ByteArray(chunkSize.toInt())
                            stream.readFully(fmtData)
                            wavChannels = readLeShort(fmtData, 2).coerceAtLeast(1)
                            wavSampleRate = readLeInt(fmtData, 4)
                            byteRate = readLeInt(fmtData, 8)
                            wavBitsPerSample = readLeShort(fmtData, 14).coerceAtLeast(8)
                        } else {
                            stream.skipFully(chunkSize)
                        }
                    }
                    "data" -> {
                        dataBytes = chunkSize
                        stream.skipFully(chunkSize)
                    }
                    "LIST" -> {
                        if (chunkSize >= 4) {
                            val listTypeBytes = ByteArray(4)
                            stream.readFully(listTypeBytes)
                            val listType = String(listTypeBytes, Charsets.US_ASCII)
                            if (listType == "INFO") {
                                var bytesRemaining = chunkSize - 4
                                val subHeader = ByteArray(8)
                                while (bytesRemaining >= 8) {
                                    if (stream.read(subHeader) != 8) break
                                    bytesRemaining -= 8
                                    val subId = String(subHeader, 0, 4, Charsets.US_ASCII)
                                    val subLen = readLeInt(subHeader, 4).toLong() and 0xFFFFFFFFL
                                    val paddedLen = if (subLen % 2 != 0L) subLen + 1 else subLen
                                    if (subLen in 1..4096 && bytesRemaining >= subLen) {
                                        val subData = ByteArray(subLen.toInt())
                                        stream.readFully(subData)
                                        val text = String(subData, Charsets.UTF_8).trimEnd('\u0000', ' ')
                                        when (subId) {
                                            "INAM" -> if (title == null) title = text
                                            "IART" -> if (artist == null) artist = text
                                            "IPRD" -> if (album == null) album = text
                                            "IGNR" -> if (genre == null) genre = text
                                            "ICRD" -> if (year == 0) year = extractYear(text)
                                            "ITRK" -> if (trackNumber == 0) trackNumber = text.split("/")[0].trim().toIntOrNull() ?: 0
                                        }
                                        if (paddedLen > subLen) {
                                            stream.skipFully(paddedLen - subLen)
                                        }
                                        bytesRemaining -= paddedLen
                                    } else {
                                        stream.skipFully(paddedLen)
                                        bytesRemaining -= paddedLen
                                    }
                                }
                            } else {
                                stream.skipFully(chunkSize - 4)
                            }
                        } else {
                            stream.skipFully(chunkSize)
                        }
                    }
                    "id3 ", "ID3 " -> {
                        val id3Data = ByteArray(chunkSize.toInt())
                        stream.readFully(id3Data)
                        val parsedId3 = parseId3Bytes(id3Data)
                        if (title == null) title = parsedId3.title
                        if (artist == null) artist = parsedId3.artist
                        if (album == null) album = parsedId3.album
                        if (genre == null) genre = parsedId3.genre
                        if (year == 0) year = parsedId3.year
                        if (trackNumber == 0) trackNumber = parsedId3.trackNumber
                        if (lyrics == null) lyrics = parsedId3.lyrics
                    }
                    else -> {
                        val padded = if (chunkSize % 2 != 0L) chunkSize + 1 else chunkSize
                        stream.skipFully(padded)
                    }
                }
            }

            val durationMs = if (byteRate > 0 && dataBytes > 0) (dataBytes * 1000L) / byteRate else 0L
            val bitrateKbps = if (byteRate > 0) (byteRate * 8) / 1000 else 0
            val fallback = fallbackTags(file)
            return AudioFileTags(
                title = title?.ifBlank { null } ?: fallback.title,
                artist = artist?.ifBlank { null } ?: fallback.artist,
                album = album?.ifBlank { null } ?: fallback.album,
                genre = genre ?: "",
                trackNumber = trackNumber,
                year = year,
                durationMs = durationMs,
                lyrics = lyrics,
                sampleRate = wavSampleRate,
                bitsPerSample = wavBitsPerSample,
                channels = wavChannels,
                bitrateKbps = bitrateKbps
            )
        }
    }

    // ==========================================
    // ID3v2 PARSER (MP3 / WAV id3 chunk)
    // ==========================================
    private fun readId3Tags(file: File): AudioFileTags {
        file.inputStream().buffered().use { stream ->
            val header = ByteArray(10)
            if (stream.read(header) != 10) return fallbackTags(file)
            if (String(header, 0, 3, Charsets.US_ASCII) != "ID3") return fallbackTags(file)

            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)

            if (tagSize <= 0) return fallbackTags(file)
            val tagData = ByteArray(minOf(tagSize, 256 * 1024))
            stream.readFully(tagData)
            val parsed = parseId3Bytes(tagData)

            val fallback = fallbackTags(file)
            return AudioFileTags(
                title = parsed.title.ifBlank { fallback.title },
                artist = parsed.artist.ifBlank { fallback.artist },
                album = parsed.album.ifBlank { fallback.album },
                albumArtist = parsed.albumArtist,
                genre = parsed.genre,
                trackNumber = parsed.trackNumber,
                year = parsed.year,
                composer = parsed.composer,
                durationMs = parsed.durationMs,
                lyrics = parsed.lyrics,
                sampleRate = parsed.sampleRate,
                bitsPerSample = parsed.bitsPerSample,
                channels = parsed.channels,
                bitrateKbps = parsed.bitrateKbps
            )
        }
    }

    private fun parseId3Bytes(data: ByteArray): AudioFileTags {
        var offset = 0
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var genre: String? = null
        var trackNumber = 0
        var year = 0
        var composer: String? = null
        var lyrics: String? = null

        while (offset + 10 <= data.size) {
            val frameId = String(data, offset, 4, Charsets.US_ASCII)
            if (frameId.any { it !in 'A'..'Z' && it !in '0'..'9' }) break

            val frameSize = ((data[offset + 4].toInt() and 0xFF) shl 24) or
                    ((data[offset + 5].toInt() and 0xFF) shl 16) or
                    ((data[offset + 6].toInt() and 0xFF) shl 8) or
                    (data[offset + 7].toInt() and 0xFF)
            offset += 10

            if (frameSize <= 0 || offset + frameSize > data.size) break

            val text = parseId3Text(data, offset, frameSize)
            when (frameId) {
                "TIT2" -> if (title == null) title = text
                "TPE1" -> if (artist == null) artist = text
                "TALB" -> if (album == null) album = text
                "TPE2" -> if (albumArtist == null) albumArtist = text
                "TCON" -> if (genre == null) genre = text
                "TRCK" -> if (trackNumber == 0) trackNumber = text.split("/")[0].trim().toIntOrNull() ?: 0
                "TYER", "TDRC" -> if (year == 0) year = extractYear(text)
                "TCOM" -> if (composer == null) composer = text
                "USLT", "SYLT" -> {
                    if (lyrics == null) {
                        val uslt = parseId3Uslt(data, offset, frameSize)
                        if (uslt.isNotBlank()) lyrics = uslt
                    }
                }
                "TXXX" -> {
                    if (lyrics == null) {
                        val txxx = parseId3Txxx(data, offset, frameSize)
                        if (txxx != null && (txxx.first.contains("LYRICS", ignoreCase = true) || txxx.second.contains("[00:", ignoreCase = true))) {
                            lyrics = txxx.second
                        }
                    }
                }
            }
            offset += frameSize
        }

        return AudioFileTags(
            title = title ?: "",
            artist = artist ?: "",
            album = album ?: "",
            albumArtist = albumArtist ?: "",
            genre = genre ?: "",
            trackNumber = trackNumber,
            year = year,
            composer = composer ?: "",
            lyrics = lyrics
        )
    }

    private fun parseId3Text(data: ByteArray, offset: Int, size: Int): String {
        if (size <= 1) return ""
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = when (encodingByte) {
            1 -> Charset.forName("UTF-16")
            2 -> Charset.forName("UTF-16BE")
            3 -> Charset.forName("UTF-8")
            else -> Charset.forName("ISO-8859-1")
        }
        val textBytes = data.copyOfRange(offset + 1, offset + size)
        return String(textBytes, charset).trim('\u0000', ' ')
    }

    private fun parseId3Uslt(data: ByteArray, offset: Int, size: Int): String {
        if (size <= 5) return ""
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = when (encodingByte) {
            1 -> Charset.forName("UTF-16")
            2 -> Charset.forName("UTF-16BE")
            3 -> Charset.forName("UTF-8")
            else -> Charset.forName("ISO-8859-1")
        }
        // Skip 1 byte encoding + 3 bytes language code = 4 bytes
        var pos = offset + 4
        val end = offset + size

        // Content descriptor is null-terminated according to encoding
        if (encodingByte == 1 || encodingByte == 2) {
            while (pos + 1 < end) {
                if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                    pos += 2
                    break
                }
                pos += 2
            }
        } else {
            while (pos < end) {
                if (data[pos] == 0.toByte()) {
                    pos += 1
                    break
                }
                pos += 1
            }
        }

        if (pos >= end) return ""
        val textBytes = data.copyOfRange(pos, end)
        return String(textBytes, charset).trim('\u0000', ' ')
    }

    private fun parseId3Txxx(data: ByteArray, offset: Int, size: Int): Pair<String, String>? {
        if (size <= 2) return null
        val encodingByte = data[offset].toInt() and 0xFF
        val charset = when (encodingByte) {
            1 -> Charset.forName("UTF-16")
            2 -> Charset.forName("UTF-16BE")
            3 -> Charset.forName("UTF-8")
            else -> Charset.forName("ISO-8859-1")
        }
        var pos = offset + 1
        val end = offset + size
        val descStart = pos

        if (encodingByte == 1 || encodingByte == 2) {
            while (pos + 1 < end) {
                if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                    break
                }
                pos += 2
            }
        } else {
            while (pos < end) {
                if (data[pos] == 0.toByte()) break
                pos += 1
            }
        }

        val descBytes = data.copyOfRange(descStart, minOf(pos, end))
        val description = String(descBytes, charset).trim('\u0000', ' ')

        val valueStart = if (encodingByte == 1 || encodingByte == 2) pos + 2 else pos + 1
        if (valueStart >= end) return Pair(description, "")
        val valueBytes = data.copyOfRange(valueStart, end)
        val value = String(valueBytes, charset).trim('\u0000', ' ')
        return Pair(description, value)
    }

    private fun extractYear(str: String): Int {
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(str)
        return match?.value?.toIntOrNull() ?: 0
    }

    private fun readLeInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readLeShort(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var readTotal = 0
        while (readTotal < buffer.size) {
            val count = this.read(buffer, readTotal, buffer.size - readTotal)
            if (count == -1) break
            readTotal += count
        }
    }

    private fun InputStream.skipFully(bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = this.skip(remaining)
            if (skipped <= 0) {
                if (this.read() == -1) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }
}
