package com.yuka.musicplayer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val isPrerelease: Boolean,
    val publishedAt: String
)

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpToDate(val currentVersion: String, val latestTag: String, val release: ReleaseInfo? = null) : UpdateCheckState()
    data class UpdateAvailable(val release: ReleaseInfo, val currentVersion: String) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class ReadyToInstall(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object AppUpdateManager {
    private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/yukaatsu/tsrossa/releases"

    /**
     * Checks GitHub releases for the latest valid release containing an APK.
     */
    suspend fun checkForUpdates(currentVersion: String): UpdateCheckState = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_RELEASES_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "tsrossa-updater")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                return@withContext UpdateCheckState.Error("HTTP $responseCode: ${conn.responseMessage}")
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(responseText)
            if (jsonArray.length() == 0) {
                return@withContext UpdateCheckState.UpToDate(currentVersion, currentVersion)
            }

            var targetRelease: ReleaseInfo? = null
            for (i in 0 until jsonArray.length()) {
                val relObj = jsonArray.getJSONObject(i)
                if (relObj.optBoolean("draft", false)) continue

                val tagName = relObj.optString("tag_name", "")
                val releaseName = relObj.optString("name", tagName)
                val body = relObj.optString("body", "")
                val isPrerelease = relObj.optBoolean("prerelease", false)
                val publishedAt = relObj.optString("published_at", "")

                val assets = relObj.optJSONArray("assets") ?: continue
                var apkUrl: String? = null
                var apkSize = 0L

                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }

                if (apkUrl != null) {
                    targetRelease = ReleaseInfo(
                        tagName = tagName,
                        name = releaseName,
                        changelog = body,
                        apkDownloadUrl = apkUrl,
                        apkSize = apkSize,
                        isPrerelease = isPrerelease,
                        publishedAt = publishedAt
                    )
                    break
                }
            }

            if (targetRelease == null) {
                return@withContext UpdateCheckState.Error("No valid release with APK asset found")
            }

            if (isNewerVersion(targetRelease.tagName, currentVersion)) {
                UpdateCheckState.UpdateAvailable(targetRelease, currentVersion)
            } else {
                UpdateCheckState.UpToDate(currentVersion, targetRelease.tagName, targetRelease)
            }
        } catch (e: Exception) {
            UpdateCheckState.Error(e.message ?: "Failed to connect to update server")
        }
    }

    /**
     * Downloads the APK file to the application cache with real-time progress.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedSize: Long,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var currentUrl = downloadUrl
            var conn: HttpURLConnection
            var redirectCount = 0

            while (true) {
                val u = URL(currentUrl)
                conn = u.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "tsrossa-updater")
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (location != null && redirectCount < 5) {
                        currentUrl = location
                        redirectCount++
                        conn.disconnect()
                        continue
                    }
                }
                break
            }

            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else expectedSize
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()
            val apkFile = File(updateDir, "tsrossa-update.apk")
            if (apkFile.exists()) apkFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            // Verify complete download
            if (totalBytes > 0 && apkFile.length() < totalBytes) {
                apkFile.delete()
                return@withContext Result.failure(
                    java.io.IOException("Incomplete APK download (${apkFile.length()} / $totalBytes bytes)")
                )
            }

            // Verify APK archive integrity and package name
            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (packageInfo == null || packageInfo.packageName != context.packageName) {
                apkFile.delete()
                return@withContext Result.failure(
                    java.io.IOException("Invalid or corrupt APK file downloaded (signature/package mismatch)")
                )
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if the app is allowed to request package installations on Android 8.0+.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens Android System Settings so the user can grant unknown app install permission.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the Android Package Installer using FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!canInstallPackages(context)) {
            openInstallPermissionSettings(context)
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Compares pre-release suffix with natural number ordering and tier ranking.
     * e.g. "beta10" > "beta2", "rc1" > "beta5"
     */
    fun comparePreReleaseSuffix(remote: String, current: String): Int {
        val tokenRegex = Regex("([a-zA-Z]+)|(\\d+)")
        val remoteTokens = tokenRegex.findAll(remote).map { it.value }.toList()
        val currentTokens = tokenRegex.findAll(current).map { it.value }.toList()

        val maxLen = maxOf(remoteTokens.size, currentTokens.size)
        for (i in 0 until maxLen) {
            val rToken = remoteTokens.getOrNull(i) ?: return -1
            val cToken = currentTokens.getOrNull(i) ?: return 1

            val rNum = rToken.toLongOrNull()
            val cNum = cToken.toLongOrNull()

            if (rNum != null && cNum != null) {
                if (rNum != cNum) return rNum.compareTo(cNum)
            } else {
                val rRank = rankPreReleaseType(rToken)
                val cRank = rankPreReleaseType(cToken)
                if (rRank != 0 && cRank != 0 && rRank != cRank) {
                    return rRank.compareTo(cRank)
                }
                val textComp = rToken.compareTo(cToken, ignoreCase = true)
                if (textComp != 0) return textComp
            }
        }
        return 0
    }

    private fun rankPreReleaseType(token: String): Int {
        return when (token.lowercase()) {
            "alpha" -> 1
            "beta" -> 2
            "rc" -> 3
            else -> 0
        }
    }

    /**
     * Semantic version comparison supporting:
     * - "v1.2.0-beta2" vs "1.2.0-beta1" -> true
     * - "v1.2.0-beta10" vs "1.2.0-beta2" -> true
     * - "v1.2.0" vs "1.2.0-beta1" -> true
     * - "v1.2.1" vs "1.2.0" -> true
     * - "v1.2.0-beta1" vs "1.2.0-beta1" -> false
     * - "v1.1.0-rc" vs "1.2.0-beta1" -> false
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) {
            return false
        }

        val remoteParts = cleanRemote.split("-", limit = 2)
        val currentParts = cleanCurrent.split("-", limit = 2)

        val remoteNums = remoteParts[0].split(".").mapNotNull { it.toIntOrNull() }
        val currentNums = currentParts[0].split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteNums.size, currentNums.size)
        for (i in 0 until maxLen) {
            val r = remoteNums.getOrElse(i) { 0 }
            val c = currentNums.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // Numeric parts are equal (e.g. 1.2.0 vs 1.2.0)
        // If remote has no suffix (stable release), it is newer than a prerelease (e.g. 1.2.0 > 1.2.0-beta1)
        val remoteSuffix = remoteParts.getOrNull(1)
        val currentSuffix = currentParts.getOrNull(1)

        if (remoteSuffix == null && currentSuffix != null) return true
        if (remoteSuffix != null && currentSuffix == null) return false
        if (remoteSuffix != null && currentSuffix != null) {
            return comparePreReleaseSuffix(remoteSuffix, currentSuffix) > 0
        }

        return false
    }
}
