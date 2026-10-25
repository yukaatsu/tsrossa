package com.yuka.musicplayer.ui.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.dialogs.PermissionStatusRow
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont

@Composable
fun PermissionsSettingsSection(
    isNotificationGranted: Boolean = true,
    isStorageGranted: Boolean = true,
    isDndGranted: Boolean = false,
    isBatteryOptimizationIgnored: Boolean = true,
    onRequestNotification: () -> Unit = {},
    onRequestStorage: () -> Unit = {},
    onRequestDnd: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    onOpenPermissionDialog: () -> Unit = {}
) {
    val accent = LocalAccentColor.current

    SettingsCard(title = "Permissions & Access") {
        PermissionStatusRow(
            title = "Notification Controls",
            subtitle = "Lockscreen player & background MediaStyle playback",
            isGranted = isNotificationGranted,
            grantedText = "Active",
            deniedText = "Restricted",
            onActionClick = onRequestNotification
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionStatusRow(
            title = "All Audio Files Access",
            subtitle = "Direct C++ bit-perfect reader for FLAC/DSD/WAV",
            isGranted = isStorageGranted,
            grantedText = "Active",
            deniedText = "Restricted",
            onActionClick = onRequestStorage
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionStatusRow(
            title = "Background Power (Anti-Killer)",
            subtitle = "Prevents aggressive OEM battery termination",
            isGranted = isBatteryOptimizationIgnored,
            grantedText = "Unrestricted",
            deniedText = "Optimized",
            onActionClick = onRequestBatteryOptimization
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionStatusRow(
            title = "Do Not Disturb (DND)",
            subtitle = "Auto-mute notification sounds during playback",
            isGranted = isDndGranted,
            grantedText = "Active",
            deniedText = "Optional",
            onActionClick = onRequestDnd
        )

        Spacer(modifier = Modifier.height(14.dp))

        TactileBox(
            onClick = onOpenPermissionDialog,
            pressedScale = 0.95f,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.16f))
                .padding(vertical = 11.dp)
        ) {
            Text(
                text = "Launch Permission Setup Wizard",
                color = accent,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
