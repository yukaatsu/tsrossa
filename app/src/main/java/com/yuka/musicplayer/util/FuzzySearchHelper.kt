package com.yuka.musicplayer.util

/**
 * High-performance, memory-efficient fuzzy search matcher for tracks, artists, albums, and playlists.
 * Supports:
 * - Direct substring and case-insensitive matching
 * - Multi-token matching (every word in query matched)
 * - Subsequence matching (characters appearing in sequence)
 */
object FuzzySearchHelper {

    fun matches(target: String?, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (target.isNullOrBlank()) return false

        val cleanQuery = query.trim().lowercase()
        val cleanTarget = target.lowercase()

        // 1. Direct contains check (fastest path)
        if (cleanTarget.contains(cleanQuery)) return true

        // 2. Tokenized multi-word search (e.g. "led iv" in "Led Zeppelin IV")
        val tokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            val allTokensMatch = tokens.all { token ->
                cleanTarget.contains(token) || isSubsequence(token, cleanTarget)
            }
            if (allTokensMatch) return true
        }

        // 3. Subsequence fuzzy match (e.g. "bhm" matches "bohemian")
        return isSubsequence(cleanQuery, cleanTarget)
    }

    fun matchesAny(query: String?, vararg targets: String?): Boolean {
        return matchesAny(targets.asIterable(), query)
    }

    fun matchesAny(targets: Iterable<String?>, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        val cleanQuery = query.trim().lowercase()

        // First check direct contains across all targets
        for (target in targets) {
            if (target != null && target.lowercase().contains(cleanQuery)) {
                return true
            }
        }

        // If multi-token, check across combined fields
        val tokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            val combined = targets.filterNotNull().joinToString(" ").lowercase()
            if (tokens.all { token -> combined.contains(token) || isSubsequence(token, combined) }) {
                return true
            }
        }

        // Check subsequence matching
        for (target in targets) {
            if (target != null && isSubsequence(cleanQuery, target.lowercase())) {
                return true
            }
        }

        return false
    }

    private fun isSubsequence(query: String, target: String): Boolean {
        if (query.length > target.length) return false
        var qIdx = 0
        var tIdx = 0
        val qLen = query.length
        val tLen = target.length

        while (qIdx < qLen && tIdx < tLen) {
            if (query[qIdx] == target[tIdx]) {
                qIdx++
            }
            tIdx++
        }
        return qIdx == qLen
    }
}
