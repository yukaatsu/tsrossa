package com.yuka.musicplayer.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File

fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

fun checkDndPermission(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    return nm?.isNotificationPolicyAccessGranted ?: false
}

data class StorageVolumeRoot(
    val label: String,
    val directory: File,
    val isExternalRemovable: Boolean
)

/**
 * Discovers the default Music directory plus any mounted external microSD cards or USB OTG drives.
 * Does NOT expose the full internal storage root (/storage/emulated/0), keeping access scoped to Music
 * and external drives.
 */
fun discoverAvailableMusicRoots(context: Context): List<StorageVolumeRoot> {
    val roots = ArrayList<StorageVolumeRoot>()
    val seenCanonical = HashSet<String>()

    fun addRoot(label: String, dir: File, isExternal: Boolean) {
        if (!dir.exists() || !dir.isDirectory) return
        val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (seenCanonical.add(key)) {
            roots.add(StorageVolumeRoot(label = label, directory = dir, isExternalRemovable = isExternal))
        }
    }

    // 1. Scoped Internal Music Directory ONLY (never full internal storage)
    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        ?: File(Environment.getExternalStorageDirectory(), "Music")
    if (!musicDir.exists()) {
        runCatching { musicDir.mkdirs() }
    }
    addRoot("Music Folder", musicDir, isExternal = false)

    // 2. Discover removable microSD & USB OTG volumes via StorageManager (API 24+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        runCatching {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            sm?.storageVolumes?.forEach { vol ->
                if (vol.isRemovable) {
                    val volDir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory
                    } else {
                        runCatching {
                            val getPath = vol.javaClass.getMethod("getPath")
                            (getPath.invoke(vol) as? String)?.let { File(it) }
                        }.getOrNull()
                    }
                    if (volDir != null && volDir.exists()) {
                        val desc = vol.getDescription(context).takeIf { it.isNotBlank() } ?: volDir.name
                        addRoot(desc, volDir, isExternal = true)
                    }
                }
            }
        }
    }

    // 3. Fallback: scan /storage/* for mounted microSD / USB OTG volumes
    runCatching {
        File("/storage").listFiles()?.forEach { dir ->
            val name = dir.name
            if (dir.isDirectory && dir.canRead() &&
                name != "emulated" && name != "self" && !name.startsWith(".")
            ) {
                addRoot("External ($name)", dir, isExternal = true)
            }
        }
    }

    // 4. Fallback via Context.getExternalFilesDirs
    runCatching {
        context.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { appExtDir ->
            val rootVol = appExtDir.parentFile?.parentFile?.parentFile?.parentFile
            if (rootVol != null && rootVol.exists() && rootVol.canRead()) {
                addRoot("Drive (${rootVol.name})", rootVol, isExternal = true)
            }
        }
    }

    return roots
}
