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
        "Terima kasih tak terhingga kepada seluruh pendukung, donatur, dan rekan audiophile yang telah berkontribusi bagi perkembangan tsrossa! Setiap dukungan yang kalian berikan menjadi bahan bakar utama agar pemutar musik bit-perfect ini terus aktif dirawat dan disempurnakan tanpa iklan."

    const val PRIORITY_FEATURE_REQUEST_NOTE =
        "Punya ide fitur baru atau perbaikan yang ingin dipertimbangkan lebih utama dalam antrean rilis? Tuliskan request fitur kamu di kolom pesan saat melakukan donasi melalui Bagi-Bagi. Request dari para donatur akan ditinjau dan diprioritaskan langsung dalam roadmap pengembangan tsrossa!"

    /**
     * Curated list of early supporters and feature request contributors.
     */
    val DEFAULT_SUPPORTERS = listOf(
        DonationSupporter(
            name = "Seseorang",
            message = "Bang, untuk aplikasi nya bagus, semoga kedepannya ada fitur EQ dan next patch tambahin informasi waktu play lagu... Berapa bit itu lagunya, outputnya brp, dll, itu aja sih bang",
            badge = "Supporter",
            date = "Community Feedback",
            isFeatureRequest = true
        )
    )

    /**
     * Attempts to fetch latest remote supporters list from GitHub with graceful fallback.
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
                    if (list.isNotEmpty()) return@withContext list
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to default curated list on offline or timeout
        }
        return@withContext DEFAULT_SUPPORTERS
    }
}
