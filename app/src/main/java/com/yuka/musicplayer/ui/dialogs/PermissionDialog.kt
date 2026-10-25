package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite

@Composable
fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    grantedText: String,
    deniedText: String,
    onActionClick: () -> Unit
) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                color = TerminalWhite,
                fontFamily = ModernSansFont,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontFamily = ModernSansFont,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (isGranted) grantedText else deniedText,
                color = if (isGranted) Color(0xFF00E676) else Color(0xFFFF9100),
                fontFamily = ModernSansFont,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        TactileBox(
            onClick = onActionClick,
            pressedScale = 0.90f,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "SETUP",
                color = accent,
                fontFamily = ModernSansFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionCard(
    icon: @Composable () -> Unit,
    title: String,
    badge: String,
    isGranted: Boolean,
    isEssential: Boolean,
    desc: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val badgeColor = if (isGranted) Color(0xFF00E676)
    else if (isEssential) Color(0xFFFF9100)
    else TerminalGray

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = TerminalWhite,
                    fontFamily = ModernSansFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontFamily = ModernSansFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = desc,
            color = Color.White.copy(alpha = 0.65f),
            fontFamily = ModernSansFont,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (!isGranted) {
            TactileBox(
                onClick = onAction,
                pressedScale = 0.94f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.20f))
                    .padding(vertical = 9.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = badgeColor,
                    fontFamily = ModernSansFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.yuka.musicplayer.ui.components.GlyphCheck(
                    color = Color(0xFF00E676),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = actionLabel,
                    color = Color.White.copy(alpha = 0.60f),
                    fontFamily = ModernSansFont,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PermissionSetupDialog(
    isNotificationGranted: Boolean,
    isStorageGranted: Boolean,
    isDndGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean = true,
    onRequestNotification: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestDnd: () -> Unit,
    onRequestBatteryOptimization: () -> Unit = {},
    onComplete: () -> Unit
) {
    val accent = LocalAccentColor.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "System Access Setup",
                color = TerminalWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ModernSansFont
            )
            if (isStorageGranted) {
                TactileBox(
                    onClick = onComplete,
                    pressedScale = 0.85f,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    com.yuka.musicplayer.ui.components.GlyphClose(
                        color = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "tsrossa requires these system permissions so the C++ bit-perfect audio engine and media controls operate uninterrupted.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontFamily = ModernSansFont,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PermissionCard(
                    icon = { com.yuka.musicplayer.ui.components.GlyphFolder(color = accent, modifier = Modifier.size(18.dp)) },
                    title = "Audio Storage Access",
                    badge = if (isStorageGranted) "GRANTED" else "REQUIRED",
                    isGranted = isStorageGranted,
                    isEssential = true,
                    desc = "Required for the native engine (dr_flac & dr_wav) to read FLAC & WAV files directly from internal memory or MicroSD.",
                    actionLabel = if (isStorageGranted) "Already Granted" else "Grant Storage Access",
                    onAction = onRequestStorage
                )
            }

            item {
                PermissionCard(
                    icon = { com.yuka.musicplayer.ui.components.GlyphNotification(color = accent, modifier = Modifier.size(18.dp)) },
                    title = "Notification & Lockscreen Controls",
                    badge = if (isNotificationGranted) "GRANTED" else "REQUIRED",
                    isGranted = isNotificationGranted,
                    isEssential = true,
                    desc = "Required on Android 13+ to display MediaStyle player controls and bit-perfect DAC status on your notification shade and lockscreen.",
                    actionLabel = if (isNotificationGranted) "Already Granted" else "Grant Notification Permission",
                    onAction = onRequestNotification
                )
            }

            item {
                PermissionCard(
                    icon = { com.yuka.musicplayer.ui.components.GlyphBolt(color = accent, modifier = Modifier.size(18.dp)) },
                    title = "OEM Background Power (Anti-Kill)",
                    badge = if (isBatteryOptimizationIgnored) "UNRESTRICTED" else "RECOMMENDED",
                    isGranted = isBatteryOptimizationIgnored,
                    isEssential = false,
                    desc = "Important on Xiaomi (HyperOS), OnePlus, Oppo, Vivo, & Samsung so bit-perfect playback isn't terminated when the screen turns off.",
                    actionLabel = if (isBatteryOptimizationIgnored) "Configured" else "Configure Battery Settings",
                    onAction = onRequestBatteryOptimization
                )
            }

            item {
                PermissionCard(
                    icon = { com.yuka.musicplayer.ui.components.GlyphDoNotDisturb(color = accent, modifier = Modifier.size(18.dp)) },
                    title = "Do Not Disturb (DND)",
                    badge = if (isDndGranted) "ACTIVE" else "OPTIONAL",
                    isGranted = isDndGranted,
                    isEssential = false,
                    desc = "Silences ringtones/notifications automatically during playback so audiophile streams aren't interrupted.",
                    actionLabel = if (isDndGranted) "Active" else "Configure DND (Optional)",
                    onAction = onRequestDnd
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isStorageGranted) {
            Text(
                text = "Please grant Storage Access to begin scanning and playing music.",
                color = Color(0xFFFF9100),
                fontFamily = ModernSansFont,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Button(
            onClick = onComplete,
            enabled = isStorageGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                disabledContainerColor = Color.White.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                text = if (isStorageGranted) "Continue to Player" else "Grant Permissions Above",
                fontFamily = ModernSansFont,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStorageGranted) Color(0xFF0E0D13) else Color.White.copy(alpha = 0.45f)
            )
        }
    }
}
