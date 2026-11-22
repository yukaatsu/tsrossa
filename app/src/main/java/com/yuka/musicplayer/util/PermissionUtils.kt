package com.yuka.musicplayer.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Checks whether full direct filesystem access (bypassing Android 11+ FUSE .nomedia filtering)
 * is granted. On Android 11+ (API 30+), this checks Environment.isExternalStorageManager().
 * On Android 10 and below, standard READ_EXTERNAL_STORAGE already provides direct filesystem access.
 */
fun isAllFilesAccessGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Checks whether storage permission (either All Files Access or basic media access) is granted.
 * On Android 11+, if MANAGE_EXTERNAL_STORAGE is active, returns true immediately.
 */
fun checkStoragePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
        return true
    }
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

/**
 * Builds an Intent to direct the user to the All Files Access management screen.
 */
fun buildAllFilesAccessIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        )
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }
}

/**
 * Opens the All Files Access settings with fallback support for custom OEM ROMs.
 */
fun openAllFilesAccessSettings(context: Context) {
    runCatching {
        val intent = buildAllFilesAccessIntent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }.onFailure {
                runCatching {
                    val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(appSettingsIntent)
                }
            }
        }
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

data class StorageVolumeRoot(
    val label: String,
    val directory: File,
    val isExternalRemovable: Boolean
)

/**
 * Discovers the primary Internal Storage root (/storage/emulated/0), standard music/download shortcuts,
 * plus any mounted external microSD cards or USB OTG drives.
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

    // 1. Primary Internal Storage Root (/storage/emulated/0) so user can freely navigate any music folder
    runCatching {
        val internalRoot = Environment.getExternalStorageDirectory()
        if (internalRoot != null && internalRoot.exists() && internalRoot.isDirectory) {
            addRoot("Internal Storage", internalRoot, isExternal = false)
        }
    }

    // 1b. Standard Music directory shortcut (/storage/emulated/0/Music)
    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        ?: File(Environment.getExternalStorageDirectory(), "Music")
    if (!musicDir.exists()) {
        runCatching { musicDir.mkdirs() }
    }
    addRoot("Music", musicDir, isExternal = false)

    // 1c. Standard Download directory shortcut (/storage/emulated/0/Download)
    runCatching {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File(Environment.getExternalStorageDirectory(), "Download")
        if (downloadDir.exists() && downloadDir.isDirectory) {
            addRoot("Download", downloadDir, isExternal = false)
        }
    }

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
