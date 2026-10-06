package com.yuka.musicplayer.ui.views

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.BuildConfig
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.dialogs.PermissionStatusRow
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.SakuraPink
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.update.UpdateCheckState

/**
 * Modern Segmented Control for settings selection.
 */
@Composable
fun <T> SettingsSegmentedControl(
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF070B14), RoundedCornerShape(8.dp))
            .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (item, label) ->
            val isSelected = item == selectedItem
            TactileBox(
                onClick = { onItemSelected(item) },
                pressedScale = 0.95f,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent
                    )
                    .border(
                        0.8.dp,
                        if (isSelected) accent.copy(alpha = 0.6f) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 7.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) accent else TerminalGray,
                    fontFamily = TerminalFont,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Grouped Preference Container Card
 */
@Composable
fun SettingsCard(
    title: String,
    tag: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = LocalAccentColor.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF090D18))
            .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 11.dp)
                        .background(accent, RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = TerminalWhite,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
            if (tag != null) {
                Text(
                    text = tag,
                    color = accent.copy(alpha = 0.8f),
                    fontFamily = TerminalFont,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        content()
    }
}

/**
 * Toggle Row inside Settings Card
 */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val accent = LocalAccentColor.current
    TactileBox(
        onClick = { onCheckedChange(!isChecked) },
        pressedScale = 0.98f,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF070B14))
            .border(
                0.8.dp,
                if (isChecked) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = title,
                    color = TerminalWhite,
                    fontFamily = TerminalFont,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TerminalGray,
                    fontFamily = TerminalFont,
                    fontSize = 9.sp
                )
            }

            // Sleek Hardware-like Pill Switch
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 20.dp)
                    .background(
                        if (isChecked) accent.copy(alpha = 0.25f) else Color(0xFF141926),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        0.8.dp,
                        if (isChecked) accent else Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 3.dp),
                contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .background(
                            if (isChecked) accent else TerminalGray,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun SettingsView(
    prefs: SharedPreferences,
    bgMode: String,
    onBgModeChange: (String) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit,
    keepAwake: Boolean,
    onKeepAwakeChange: (Boolean) -> Unit,
    defaultScreen: String,
    onDefaultScreenChange: (String) -> Unit,
    accentMode: String,
    onAccentModeChange: (String) -> Unit,
    accentFixedColorStr: String,
    onAccentFixedColorChange: (String) -> Unit,
    isNotificationGranted: Boolean = true,
    isStorageGranted: Boolean = true,
    isDndGranted: Boolean = false,
    onRequestNotification: () -> Unit = {},
    onRequestStorage: () -> Unit = {},
    onRequestDnd: () -> Unit = {},
    onOpenPermissionDialog: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    onCheckForUpdate: () -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. THEME & DISPLAY ---
        SettingsCard(title = "THEME & DISPLAY", tag = "VISUAL ENGINE") {
            Text(
                text = "BACKGROUND CANVAS",
                color = TerminalGray,
                fontFamily = TerminalFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(5.dp))

            val bgOptions = listOf(
                "BLACK" to "SOLID BLACK",
                "BLUR" to "BLUR WALLPAPER",
                "SIGNATURE" to "★ NEON SAKURA"
            )
            SettingsSegmentedControl(
                items = bgOptions,
                selectedItem = bgMode,
                onItemSelected = { mode ->
                    onBgModeChange(mode)
                    prefs.edit().putString("bg_mode", mode).apply()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "ACCENT COLOR MODE",
                color = TerminalGray,
                fontFamily = TerminalFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(5.dp))

            val accentOptions = listOf(
                "DYNAMIC" to "DYNAMIC (ALBUM)",
                "FIXED" to "FIXED PALETTE"
            )
            SettingsSegmentedControl(
                items = accentOptions,
                selectedItem = accentMode,
                onItemSelected = { mode ->
                    onAccentModeChange(mode)
                    prefs.edit().putString("accent_mode", mode).apply()
                }
            )

            if (accentMode == "FIXED") {
                Spacer(modifier = Modifier.height(10.dp))
                val classicColors = listOf("#00FF00", "#00FFFF", "#FF00FF", "#FFFF00", "#FF5555", "#5555FF")
                val signatureColors = listOf(
                    "#FF85A1", // Sakura Pink
                    "#A259FF", // Vivid Violet
                    "#64FFDA", // Subtle Mint
                    "#B388FF"  // Pastel Purple
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14), RoundedCornerShape(8.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("TSROSSA SIGNATURE PALETTE", color = SakuraPink, fontFamily = TerminalFont, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        signatureColors.forEach { hex ->
                            val colorObj = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                            val isCurrent = accentFixedColorStr == hex
                            TactileBox(
                                onClick = {
                                    onAccentFixedColorChange(hex)
                                    prefs.edit().putString("accent_fixed_color", hex).apply()
                                },
                                pressedScale = 0.88f,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        if (isCurrent) 1.5.dp else 0.8.dp,
                                        if (isCurrent) Color.White else Color.White.copy(alpha = 0.2f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .background(colorObj)
                            ) {
                                if (isCurrent) {
                                    Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }

                    Text("CLASSIC TERMINAL PALETTE", color = TerminalGray, fontFamily = TerminalFont, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        classicColors.forEach { hex ->
                            val colorObj = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                            val isCurrent = accentFixedColorStr == hex
                            TactileBox(
                                onClick = {
                                    onAccentFixedColorChange(hex)
                                    prefs.edit().putString("accent_fixed_color", hex).apply()
                                },
                                pressedScale = 0.88f,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        if (isCurrent) 1.5.dp else 0.8.dp,
                                        if (isCurrent) Color.White else Color.White.copy(alpha = 0.2f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .background(colorObj)
                            ) {
                                if (isCurrent) {
                                    Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. TYPOGRAPHY & TOUCH ---
        SettingsCard(title = "TYPOGRAPHY & HAPTICS", tag = "INTERFACE SCALE") {
            Text("GLOBAL FONT SCALE", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TactileBox(
                    onClick = {
                        val newScale = (fontScale - 0.1f).coerceAtLeast(0.8f)
                        onFontScaleChange(newScale)
                        prefs.edit().putFloat("font_scale", newScale).apply()
                    },
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(width = 44.dp, height = 34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070B14))
                        .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                ) {
                    Text("-", color = accent, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .background(Color(0xFF070B14), RoundedCornerShape(6.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${String.format("%.1f", fontScale)}x SCALE",
                        color = TerminalWhite,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                TactileBox(
                    onClick = {
                        val newScale = (fontScale + 0.1f).coerceAtMost(1.5f)
                        onFontScaleChange(newScale)
                        prefs.edit().putFloat("font_scale", newScale).apply()
                    },
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(width = 44.dp, height = 34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070B14))
                        .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                ) {
                    Text("+", color = accent, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                title = "Haptic Tactile Feedback",
                subtitle = "Vibration ticks on button & transport interaction",
                isChecked = hapticEnabled,
                onCheckedChange = { enabled ->
                    onHapticChange(enabled)
                    prefs.edit().putBoolean("haptic_enabled", enabled).apply()
                }
            )
        }

        // --- 3. PLAYBACK & BEHAVIOR ---
        SettingsCard(title = "PLAYBACK & LAUNCH", tag = "BEHAVIOR") {
            SettingsToggleRow(
                title = "Keep Screen Awake",
                subtitle = "Prevent display timeout when audio is actively playing",
                isChecked = keepAwake,
                onCheckedChange = { awake ->
                    onKeepAwakeChange(awake)
                    prefs.edit().putBoolean("keep_awake", awake).apply()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("DEFAULT SCREEN ON LAUNCH", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(5.dp))

            val screens = listOf(
                "LIBRARY" to "LIBRARY",
                "PLAYLIST" to "PLAYLIST",
                "TRACK" to "NOW PLAYING"
            )
            SettingsSegmentedControl(
                items = screens,
                selectedItem = defaultScreen,
                onItemSelected = { screen ->
                    onDefaultScreenChange(screen)
                    prefs.edit().putString("default_screen", screen).apply()
                }
            )
        }

        // --- 4. PERMISSIONS & SYSTEM ACCESS ---
        SettingsCard(title = "PERMISSIONS & SYSTEM ACCESS", tag = "HARDWARE IO") {
            PermissionStatusRow(
                title = "NOTIFICATION PERMISSION",
                subtitle = "Lockscreen player & MediaStyle controls",
                isGranted = isNotificationGranted,
                grantedText = "ACTIVE (GRANTED)",
                deniedText = "DISABLED / RESTRICTED",
                onActionClick = onRequestNotification
            )

            Spacer(modifier = Modifier.height(6.dp))

            PermissionStatusRow(
                title = "AUDIO STORAGE ACCESS",
                subtitle = "Direct C++ fopen FLAC/WAV file read",
                isGranted = isStorageGranted,
                grantedText = "ACTIVE (ALL FILES ACCESS)",
                deniedText = "RESTRICTED",
                onActionClick = onRequestStorage
            )

            Spacer(modifier = Modifier.height(6.dp))

            PermissionStatusRow(
                title = "DO NOT DISTURB (DND)",
                subtitle = "Auto-mute ringtones during playback",
                isGranted = isDndGranted,
                grantedText = "ACTIVE (GRANTED)",
                deniedText = "OPTIONAL (NOT GRANTED)",
                onActionClick = onRequestDnd
            )

            Spacer(modifier = Modifier.height(8.dp))

            TactileBox(
                onClick = onOpenPermissionDialog,
                pressedScale = 0.96f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF070B14))
                    .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(vertical = 9.dp)
            ) {
                Text(
                    text = "RE-OPEN SETUP WIZARD",
                    color = accent,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 5. DOCUMENTATION & SYSTEM MANUAL ---
        SettingsCard(title = "DOCUMENTATION & MANUAL", tag = "HELP") {
            TactileBox(
                onClick = onOpenHelp,
                pressedScale = 0.96f,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF070B14))
                    .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "[ ? ] USER GUIDE & SYSTEM MANUAL",
                            color = TerminalWhite,
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Audio engine specifications, bit-perfect UAC, & gestures",
                            color = TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 9.sp
                        )
                    }
                    Text("→", color = accent, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // --- 6. APPLICATION UPDATES ---
        SettingsCard(title = "APPLICATION UPDATES", tag = "v${BuildConfig.VERSION_NAME}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        0.8.dp,
                        if (updateCheckState is UpdateCheckState.UpdateAvailable) Color(0xFF00E676).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(6.dp)
                    )
                    .background(Color(0xFF070B14))
                    .clickable { onOpenUpdateDialog() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "BUILD v${BuildConfig.VERSION_NAME}",
                        color = TerminalWhite,
                        fontFamily = TerminalFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val statusDesc = when (updateCheckState) {
                        is UpdateCheckState.Checking -> "Checking GitHub releases..."
                        is UpdateCheckState.UpToDate -> "Application is up to date"
                        is UpdateCheckState.UpdateAvailable -> "New version ${updateCheckState.release.tagName} available!"
                        is UpdateCheckState.Error -> "Check failed: ${updateCheckState.message}"
                        is UpdateCheckState.Idle -> "Tap to scan for updates"
                    }
                    Text(
                        text = statusDesc,
                        color = when (updateCheckState) {
                            is UpdateCheckState.UpdateAvailable -> Color(0xFF00E676)
                            is UpdateCheckState.Checking -> Color(0xFFFFD700)
                            is UpdateCheckState.Error -> Color.Red
                            else -> TerminalGray
                        },
                        fontFamily = TerminalFont,
                        fontSize = 9.5.sp
                    )
                }

                TactileBox(
                    onClick = onOpenUpdateDialog,
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            0.8.dp,
                            if (updateCheckState is UpdateCheckState.UpdateAvailable) Color(0xFF00E676) else accent.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (updateCheckState is UpdateCheckState.UpdateAvailable) Color(0xFF00E676).copy(alpha = 0.15f) else Color.Transparent
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (updateCheckState is UpdateCheckState.UpdateAvailable) "VIEW" else "CHECK",
                        color = if (updateCheckState is UpdateCheckState.UpdateAvailable) Color(0xFF00E676) else accent,
                        fontFamily = TerminalFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
