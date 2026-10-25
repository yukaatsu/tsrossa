package com.yuka.musicplayer.utils

import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppLogEntry(
    val id: Long,
    val timestamp: String,
    val level: String,      // "INFO", "WARN", "ERR", "DEBUG"
    val category: String,   // "AUDIO", "USB", "PLAYBACK", "UI", "SYSTEM"
    val message: String
) {
    fun formattedLine(): String = "[$timestamp] [$level/$category] $message"
}

/**
 * In-app ring-buffer logger + native logcat reader for live diagnostics and easy debugging (Request 5).
 */
object AppLogger {
    private const val MAX_ENTRIES = 500
    private const val LOG_TAG = "TsrossaApp"
    private val lock = Any()
    private var nextId = 1L
    private val buffer = ArrayDeque<AppLogEntry>(MAX_ENTRIES)

    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    private val syncedKeys = LinkedHashSet<String>()
    private val logcatLineRegex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(\s:]+)(?:\(\s*\d+\))?:\s*(.*)$""")

    private fun nowTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date())
    }

    fun log(level: String, category: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message | ${throwable.javaClass.simpleName}: ${throwable.message}"
        } else {
            message
        }

        when (level) {
            "ERR" -> Log.e("$LOG_TAG:$category", fullMessage, throwable)
            "WARN" -> Log.w("$LOG_TAG:$category", fullMessage)
            "DEBUG" -> Log.d("$LOG_TAG:$category", fullMessage)
            else -> Log.i("$LOG_TAG:$category", fullMessage)
        }

        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) {
                buffer.removeFirst()
            }
            val entry = AppLogEntry(
                id = nextId++,
                timestamp = nowTime(),
                level = level,
                category = category,
                message = fullMessage
            )
            buffer.addLast(entry)
            _entries.value = buffer.toList()
        }
    }

    fun i(category: String, message: String) = log("INFO", category, message)
    fun d(category: String, message: String) = log("DEBUG", category, message)
    fun w(category: String, message: String) = log("WARN", category, message)
    fun e(category: String, message: String, throwable: Throwable? = null) = log("ERR", category, message, throwable)

    fun clear() {
        synchronized(lock) {
            syncedKeys.clear()
            buffer.clear()
            _entries.value = emptyList()
        }
        i("SYSTEM", "Application log buffer cleared by user.")
    }

    /**
     * Ingests native C++ and system logcat lines into categorized AppLogEntry records.
     */
    suspend fun syncFromProcessLogcat(maxLines: Int = 300) = withContext(Dispatchers.IO) {
        val pid = Process.myPid()
        val rawLines = mutableListOf<String>()
        try {
            val cmd = arrayOf("logcat", "-d", "-v", "time", "-t", maxLines.toString(), "--pid=$pid")
            val proc = Runtime.getRuntime().exec(cmd)
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("--------- beginning of")) {
                        rawLines.add(trimmed)
                    }
                }
            }
        } catch (_: Exception) {
            return@withContext
        }

        if (rawLines.isEmpty()) return@withContext

        synchronized(lock) {
            var addedAny = false
            for (line in rawLines) {
                val match = logcatLineRegex.matchEntire(line) ?: continue
                val fullTimestamp = match.groupValues[1] // e.g. "10-17 03:19:40.123"
                val priorityChar = match.groupValues[2]
                val tag = match.groupValues[3]
                val message = match.groupValues[4]

                // Skip logs originating from AppLogger itself to prevent recursion / duplicate entries
                if (tag.startsWith(LOG_TAG)) continue

                val dedupeKey = "$fullTimestamp|$tag|$message"
                if (syncedKeys.contains(dedupeKey)) continue

                if (syncedKeys.size > 1000) {
                    val iterator = syncedKeys.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                syncedKeys.add(dedupeKey)

                val level = when (priorityChar) {
                    "E", "F" -> "ERR"
                    "W" -> "WARN"
                    "D", "V" -> "DEBUG"
                    else -> "INFO"
                }

                val tagUpper = tag.uppercase(Locale.US)
                val msgUpper = message.uppercase(Locale.US)

                val category = when {
                    tagUpper.contains("USB") || msgUpper.contains("USB") || msgUpper.contains("LIBUSB") ||
                            msgUpper.contains("UAC") || msgUpper.contains("ALTSETTING") || msgUpper.contains("SET_CUR") ||
                            msgUpper.contains("CLAIM") || msgUpper.contains("VID=") || msgUpper.contains("PID=") -> "USB"

                    tagUpper.contains("KEWAUDIOENGINE") || tagUpper.contains("TSROSSA_DIAG") ||
                            tagUpper.contains("AUDIO") || tagUpper.contains("DECODE") || msgUpper.contains("PCM") ||
                            msgUpper.contains("MLOCK") || msgUpper.contains("WARMUP") || msgUpper.contains("BIT-PERFECT") ||
                            msgUpper.contains("SAMPLERATE") || msgUpper.contains("SAMPLE RATE") -> "AUDIO"

                    tagUpper.contains("SERVICE") || tagUpper.contains("PLAYER") || tagUpper.contains("MEDIASESSION") ||
                            tagUpper.contains("AUDIOFOCUS") || msgUpper.contains("PLAY") || msgUpper.contains("PAUSE") ||
                            msgUpper.contains("TRACK") || msgUpper.contains("SEEK") || msgUpper.contains("ADVANCE") -> "PLAYBACK"

                    tagUpper.contains("ACTIVITY") || tagUpper.contains("VIEW") || tagUpper.contains("COMPOSE") ||
                            tagUpper.contains("THEME") || tagUpper.contains("UI") -> "UI"

                    else -> "SYSTEM"
                }

                // Format timestamp to HH:mm:ss.SSS for display
                val displayTime = if (fullTimestamp.length >= 12 && fullTimestamp.contains(" ")) {
                    fullTimestamp.substringAfter(" ")
                } else {
                    fullTimestamp
                }

                if (buffer.size >= MAX_ENTRIES) {
                    buffer.removeFirst()
                }
                buffer.addLast(
                    AppLogEntry(
                        id = nextId++,
                        timestamp = displayTime,
                        level = level,
                        category = category,
                        message = "[$tag] $message"
                    )
                )
                addedAny = true
            }

            if (addedAny) {
                _entries.value = buffer.toList()
            }
        }
    }

    /**
     * Reads recent logcat lines for the current process (includes C++ JNI TsrossaAudio & libusb logs).
     */
    suspend fun readProcessLogcat(maxLines: Int = 250): List<String> = withContext(Dispatchers.IO) {
        val pid = Process.myPid()
        val result = mutableListOf<String>()
        try {
            val cmd = arrayOf("logcat", "-d", "-v", "time", "-t", maxLines.toString(), "--pid=$pid")
            val proc = Runtime.getRuntime().exec(cmd)
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("--------- beginning of")) {
                        result.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            result.add("[WARN] Could not read process logcat: ${e.message}")
        }
        result
    }

    fun buildExportText(
        hardwareLogs: List<String> = emptyList(),
        logcatLines: List<String> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== TSROSSA DIAGNOSTIC & APPLICATION LOG ===")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine()

        val currentEntries = _entries.value
        sb.appendLine("--- APPLICATION EVENTS (${currentEntries.size}) ---")
        currentEntries.forEach { sb.appendLine(it.formattedLine()) }
        sb.appendLine()

        if (hardwareLogs.isNotEmpty()) {
            sb.appendLine("--- USB & HARDWARE TELEMETRY (${hardwareLogs.size}) ---")
            hardwareLogs.forEach { sb.appendLine(it) }
            sb.appendLine()
        }

        if (logcatLines.isNotEmpty()) {
            sb.appendLine("--- NATIVE & SYSTEM LOGCAT (${logcatLines.size}) ---")
            logcatLines.forEach { sb.appendLine(it) }
        }
        return sb.toString()
    }
}
