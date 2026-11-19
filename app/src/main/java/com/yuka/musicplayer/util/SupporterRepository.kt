package com.yuka.musicplayer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Model representing a project supporter who donated via Bagi-Bagi.
 */
data class DonationSupporter(
    val name: String,
    val message: String,
    val badge: String = "Supporter",
    val date: String = "",
    val isFeatureRequest: Boolean = false
)

object SupporterRepository {
    const val DONATION_URL = "https://bagibagi.co/Yukaatsu"
    const val GITHUB_REPO_URL = "https://github.com/yukaatsu/tsrossa"
    private const val REMOTE_SUPPORTERS_JSON = "https://raw.githubusercontent.com/yukaatsu/tsrossa/main/supporters.json"

    const val DEVELOPER_THANK_YOU_MESSAGE =
        "Endless gratitude to all supporters, donors, and fellow audiophiles who have contributed to the development of tsrossa! Every contribution fuels the ongoing maintenance and refinement of this ad-free, bit-perfect audio engine."

    const val PRIORITY_FEATURE_REQUEST_NOTE =
        "Have a new feature idea or improvement you want prioritized in the release queue? Write your feature request in the message box when donating via Bagi-Bagi. Supporter requests are reviewed and prioritized directly in the tsrossa development roadmap!"

    /**
     * Curated list of early supporters and feature request contributors.
     * User-submitted supporter messages are preserved verbatim as authentic community quotes.
     */
    val DEFAULT_SUPPORTERS = listOf(
        DonationSupporter(
            name = "Arya",
            message = "Nah kemarin kan udah di kabulkan fitur EQ nya, nah sekarang aku ada usulan fitur bit-perfect biar bisa jalan di aplikasi lain... Kayak UAPP, ini ku coba play Apple music dac nya ga beneran kerja (indikator masih idle)... Dah itu aja..",
            badge = "Supporter",
            date = "Community Feedback",
            isFeatureRequest = true
        ),
        DonationSupporter(
            name = "Seseorang",
            message = "Bang, untuk aplikasi nya bagus, semoga kedepannya ada fitur EQ dan next patch tambahin informasi waktu play lagu... Berapa bit itu lagunya, outputnya brp, dll, itu aja sih bang",
            badge = "Supporter",
            date = "Community Feedback",
            isFeatureRequest = true
        )
    )

    /**
     * Merges remote supporter entries with curated local defaults, preserving all local entries
     * without duplicates so newly added supporters always appear both online and offline.
     */
    internal fun mergeSupporters(remote: List<DonationSupporter>): List<DonationSupporter> {
        if (remote.isEmpty()) return DEFAULT_SUPPORTERS
        val merged = ArrayList<DonationSupporter>(DEFAULT_SUPPORTERS.size + remote.size)
        val seenKeys = HashSet<String>()
        fun keyOf(s: DonationSupporter): String = "${s.name.trim().lowercase()}|${s.message.trim()}"
        for (item in DEFAULT_SUPPORTERS) {
            if (seenKeys.add(keyOf(item))) {
                merged.add(item)
            }
        }
        for (item in remote) {
            if (seenKeys.add(keyOf(item))) {
                merged.add(item)
            }
        }
        return merged
    }

    /**
     * Attempts to fetch latest remote supporters list from GitHub and merges with curated defaults.
     */
    suspend fun getSupporters(): List<DonationSupporter> = withContext(Dispatchers.IO) {
        try {
            val url = URL(REMOTE_SUPPORTERS_JSON)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "tsrossa-android")
            }
            if (conn.responseCode == 200) {
                val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val arr = root.optJSONArray("supporters") ?: JSONArray()
                if (arr.length() > 0) {
                    val list = mutableListOf<DonationSupporter>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            DonationSupporter(
                                name = item.optString("name", "Supporter"),
                                message = item.optString("message", ""),
                                badge = item.optString("badge", "Supporter"),
                                date = item.optString("date", ""),
                                isFeatureRequest = item.optBoolean("isFeatureRequest", false)
                            )
                        )
                    }
                    if (list.isNotEmpty()) return@withContext mergeSupporters(list)
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to default curated list on offline or timeout
        }
        return@withContext DEFAULT_SUPPORTERS
    }
}
