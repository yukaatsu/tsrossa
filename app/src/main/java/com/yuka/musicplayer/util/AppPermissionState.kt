package com.yuka.musicplayer.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * State holder for runtime app permissions (Storage, Notifications, DND).
 */
class AppPermissionState(
    val isNotificationGranted: Boolean,
    val isStorageGranted: Boolean,
    val isDndGranted: Boolean,
    val requestNotificationPermission: () -> Unit,
    val requestStoragePermission: () -> Unit,
    val requestDndPermission: () -> Unit
)

@Composable
fun rememberAppPermissionState(context: Context): AppPermissionState {
    var isNotificationGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var isStorageGranted by remember { mutableStateOf(checkStoragePermission(context)) }
    var isDndGranted by remember { mutableStateOf(checkDndPermission(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationGranted = granted
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isStorageGranted = granted
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        }
    }

    val requestStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } else {
            legacyStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val requestDndPermission = {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        context.startActivity(intent)
    }

    val appLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(appLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationGranted = checkNotificationPermission(context)
                isStorageGranted = checkStoragePermission(context)
                isDndGranted = checkDndPermission(context)
            }
        }
        appLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            appLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return remember(isNotificationGranted, isStorageGranted, isDndGranted) {
        AppPermissionState(
            isNotificationGranted = isNotificationGranted,
            isStorageGranted = isStorageGranted,
            isDndGranted = isDndGranted,
            requestNotificationPermission = requestNotificationPermission,
            requestStoragePermission = requestStoragePermission,
            requestDndPermission = requestDndPermission
        )
    }
}
