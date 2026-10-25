package com.yuka.musicplayer.util

/**
 * Parses raw track display names into (Title, Artist).
 * 
 * Standard convention in music files: "Title - Artist" (splits on the LAST occurrence
 * of " - " to preserve song titles containing hyphens).
 */
object TrackTitleParser {
    fun parse(displayName: String): Pair<String, String> {
        val lastIdx = displayName.lastIndexOf(" - ")
        return if (lastIdx != -1) {
            val titlePart = displayName.substring(0, lastIdx).trim()
            val artistPart = displayName.substring(lastIdx + 3).trim()
            titlePart to artistPart
        } else {
            displayName.trim() to ""
        }
    }
}
