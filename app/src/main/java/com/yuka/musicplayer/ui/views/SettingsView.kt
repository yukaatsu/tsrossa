package com.yuka.musicplayer.ui.views

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.views.settings.*
import com.yuka.musicplayer.update.UpdateCheckState

/**
 * Android-Style Hierarchical Settings Screen:
 * - Root: 5 Clean Glassmorphic Category Cards (Appearance, Playback & Audio, Setup, Updates, About).
 * - Submenus: Detail views with top navigation header and BackHandler support.
 * - Mini Player & Navigation Bar remain visible throughout.
 */
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
    themeMode: String = "DARK",
    onThemeModeChange: (String) -> Unit = {},
    isNotificationGranted: Boolean = true,
    isStorageGranted: Boolean = true,
    isDndGranted: Boolean = false,
    isBatteryOptimizationIgnored: Boolean = true,
    onRequestNotification: () -> Unit = {},
    onRequestStorage: () -> Unit = {},
    onRequestDnd: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    onOpenPermissionDialog: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    onOpenUpdateDialog: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    usbBufferProfile: Int = 1,
    onUsbBufferProfileChange: (Int) -> Unit = {},
    initialSubPage: SettingsSubPage = SettingsSubPage.ROOT,
    onBackFromRoot: () -> Unit = {},
    surfaceStyle: String = "BLUR_OPACITY",
    onSurfaceStyleChange: (String) -> Unit = {},
    makeFolderAsPlaylist: Boolean = false,
    onMakeFolderAsPlaylistChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    var currentSubPage by remember(initialSubPage) { mutableStateOf(initialSubPage) }

    // Intercept back gesture: navigate to ROOT if in submenu, or return to previous tab if at ROOT
    BackHandler(enabled = true) {
        if (currentSubPage != SettingsSubPage.ROOT) {
            currentSubPage = SettingsSubPage.ROOT
        } else {
            onBackFromRoot()
        }
    }

    AnimatedContent(
        targetState = currentSubPage,
        transitionSpec = {
            if (targetState != SettingsSubPage.ROOT) {
                (androidx.compose.animation.slideInHorizontally { it / 3 } + fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.96f)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally { -it / 3 } + fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.96f))
            } else {
                (androidx.compose.animation.slideInHorizontally { -it / 3 } + fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.96f)) togetherWith
                        (androidx.compose.animation.slideOutHorizontally { it / 3 } + fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.96f))
            }
        },
        label = "settingsSubPageTransition",
        modifier = modifier.fillMaxSize()
    ) { subPage ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subPage == SettingsSubPage.ROOT) {
                // ==========================================
                // ROOT: 5 CATEGORY MENU CARDS
                // ==========================================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TactileBox(
                            onClick = onBackFromRoot,
                            pressedScale = 0.88f,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f))
                        ) {
                            GlyphArrowBack(color = accent, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Settings",
                            color = accent,
                            fontSize = 20.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ModalCloseButton(onClick = onBackFromRoot)
                }

                // 1. Appearance Card
                SettingsMenuCard(
                    title = "Appearance",
                    subtitle = "Theme, Light/Dark mode, Accent color, Surface style",
                    icon = { GlyphAlbum(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = { currentSubPage = SettingsSubPage.APPEARANCE }
                )

                // 2. Playback & Audio Card
                SettingsMenuCard(
                    title = "Playback & Audio",
                    subtitle = "Screen timeout, Default view, USB buffer profile",
                    icon = { GlyphPlay(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = { currentSubPage = SettingsSubPage.PLAYBACK_AUDIO }
                )

                // 3. Playlist Card
                SettingsMenuCard(
                    title = "Playlist",
                    subtitle = "Folder-based playlists and playlist behavior",
                    icon = { GlyphPlaylists(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = { currentSubPage = SettingsSubPage.PLAYLIST }
                )

                // 4. Setup & Permissions Card
                SettingsMenuCard(
                    title = "Setup & Permissions",
                    subtitle = "Notifications, Storage access, Do Not Disturb, Battery",
                    icon = { GlyphLock(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = { currentSubPage = SettingsSubPage.SETUP }
                )

                // 5. Updates Card
                SettingsMenuCard(
                    title = "Updates",
                    subtitle = "Application updates and changelog",
                    icon = { GlyphRefresh(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = { currentSubPage = SettingsSubPage.UPDATES }
                )

                // 6. About Card
                SettingsMenuCard(
                    title = "About",
                    subtitle = "Developer profile, source code & donation support",
                    icon = { GlyphInfo(color = accent, modifier = Modifier.size(20.dp)) },
                    onClick = { currentSubPage = SettingsSubPage.ABOUT }
                )
            } else {
                // ==========================================
                // SUBMENU DETAIL VIEW
                // ==========================================
                // Top Navigation Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileBox(
                            onClick = { currentSubPage = SettingsSubPage.ROOT },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                            modifier = Modifier
                                .glassMorphic(
                                    backgroundColor = com.yuka.musicplayer.ui.theme.m3SubtleFill(alpha = 0.08f),
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphArrowBack(color = accent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Settings",
                                    color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                                    fontSize = 12.sp,
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = subPage.title,
                            color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                            fontSize = 17.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    ModalCloseButton(onClick = onBackFromRoot)
                }

                Spacer(modifier = Modifier.height(4.dp))

                when (subPage) {
                    SettingsSubPage.APPEARANCE -> {
                        ThemeSettingsSection(
                            bgMode = bgMode,
                            onBgModeChange = onBgModeChange,
                            accentMode = accentMode,
                            onAccentModeChange = onAccentModeChange,
                            accentFixedColorStr = accentFixedColorStr,
                            onAccentFixedColorChange = onAccentFixedColorChange,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            surfaceStyle = surfaceStyle,
                            onSurfaceStyleChange = onSurfaceStyleChange
                        )

                        TypographyHapticsSection(
                            prefs = prefs,
                            fontScale = fontScale,
                            onFontScaleChange = onFontScaleChange,
                            hapticEnabled = hapticEnabled,
                            onHapticChange = onHapticChange
                        )
                    }

                    SettingsSubPage.PLAYBACK_AUDIO -> {
                        PlaybackLaunchSection(
                            prefs = prefs,
                            keepAwake = keepAwake,
                            onKeepAwakeChange = onKeepAwakeChange,
                            defaultScreen = defaultScreen,
                            onDefaultScreenChange = onDefaultScreenChange,
                            usbBufferProfile = usbBufferProfile,
                            onUsbBufferProfileChange = onUsbBufferProfileChange
                        )
                    }

                    SettingsSubPage.PLAYLIST -> {
                        PlaylistSettingsSection(
                            makeFolderAsPlaylist = makeFolderAsPlaylist,
                            onMakeFolderAsPlaylistChange = onMakeFolderAsPlaylistChange
                        )
                    }

                    SettingsSubPage.SETUP -> {
                        PermissionsSettingsSection(
                            isNotificationGranted = isNotificationGranted,
                            isStorageGranted = isStorageGranted,
                            isDndGranted = isDndGranted,
                            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                            onRequestNotification = onRequestNotification,
                            onRequestStorage = onRequestStorage,
                            onRequestDnd = onRequestDnd,
                            onRequestBatteryOptimization = onRequestBatteryOptimization,
                            onOpenPermissionDialog = onOpenPermissionDialog
                        )
                    }

                    SettingsSubPage.UPDATES -> {
                        ApplicationUpdatesSection(
                            updateCheckState = updateCheckState,
                            onOpenUpdateDialog = onOpenUpdateDialog
                        )
                    }

                    SettingsSubPage.ABOUT -> {
                        DeveloperInfoSection()

                        DonationSupportSection()
                    }

                    SettingsSubPage.ROOT -> { /* Handled above */ }
                }
            }

            // Bottom headroom so the last card scrolls comfortably above MiniPlayer & Navigation bar
            Spacer(modifier = Modifier.height(168.dp))
        }
    }
}
