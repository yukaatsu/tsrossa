package com.yuka.musicplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object OemBatteryHelper {

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    /**
     * Attempts to open vendor-specific autostart/battery optimization screens
     * (Xiaomi, Oppo, Vivo, Huawei, Samsung) with standard fallback to Android system settings.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val packageName = context.packageName
        val manufacturer = Build.MANUFACTURER.lowercase()
        val isIgnored = isBatteryOptimizationIgnored(context)
        com.yuka.musicplayer.utils.AppLogger.i("SYSTEM", "openBatteryOptimizationSettings: isIgnored=$isIgnored, manufacturer=$manufacturer")

        val vendorIntents = mutableListOf<Intent>()

        // 1. If not yet ignored, direct system prompt is the most effective on all Android 6+
        if (!isIgnored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            vendorIntents.add(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                vendorIntents.add(
                    Intent().setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
                vendorIntents.add(
                    Intent().setClassName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    ).putExtra("package_name", packageName).putExtra("package_label", "tsrossa")
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                vendorIntents.add(
                    Intent().setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
                vendorIntents.add(
                    Intent().setClassName(
                        "com.oplus.battery",
                        "com.oplus.battery.AppBatteryUseActivity"
                    ).putExtra("pkg_name", packageName)
                )
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                vendorIntents.add(
                    Intent().setClassName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                )
                vendorIntents.add(
                    Intent().setClassName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                vendorIntents.add(
                    Intent().setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
            }
        }

        // Standard Android Battery Optimization Intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            vendorIntents.add(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }

        // Final fallback: App Details Settings
        vendorIntents.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )

        for (intent in vendorIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    com.yuka.musicplayer.utils.AppLogger.i("SYSTEM", "Launched battery settings intent: ${intent.action ?: intent.component}")
                    return
                }
            } catch (ignored: Exception) {
                // Try next fallback intent
            }
        }

        try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (e: Exception) {
            com.yuka.musicplayer.utils.AppLogger.e("SYSTEM", "Failed to launch battery settings", e)
        }
    }
}
