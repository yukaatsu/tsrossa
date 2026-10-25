package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.GlyphRefresh
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.update.DownloadState
import com.yuka.musicplayer.update.ReleaseInfo
import com.yuka.musicplayer.update.UpdateCheckState
import com.yuka.musicplayer.util.ColorContrastHelper
import java.io.File

@Composable
fun UpdatePanel(
    currentVersion: String,
    updateCheckState: UpdateCheckState,
    downloadState: DownloadState,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstall: (ReleaseInfo) -> Unit,
    onInstallFile: (File) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    canInstallPackages: Boolean,
    onClose: () -> Unit
) {
    val accent = LocalAccentColor.current
    val onAccent = if (ColorContrastHelper.calculateLuminance(accent) > 0.45f) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Software Updates",
                    color = m3OnSurface(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont
                )
                Text(
                    text = "GitHub releases & direct APK updates",
                    color = m3OnSurfaceVariant(),
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont
                )
            }

            ModalCloseButton(onClick = onClose)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Version Info Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(m3SurfaceContainer())
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Installed Version", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 13.sp)
                        Text("v$currentVersion", color = m3OnSurface(), fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (updateCheckState) {
                        is UpdateCheckState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Status: ", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 13.sp)
                                Text("Checking GitHub releases...", color = Color(0xFFFFD700), fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        is UpdateCheckState.UpToDate -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Status: ", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 13.sp)
                                Text("Up to date (${updateCheckState.latestTag})", color = Color(0xFF00E676), fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        is UpdateCheckState.UpdateAvailable -> {
                            val rel = updateCheckState.release
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Status: ", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 13.sp)
                                Text("New version available!", color = Color(0xFF00E676), fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Latest Release", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 12.sp)
                                Text(rel.tagName, color = Color(0xFF00E676), fontFamily = ModernSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Package Size", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 12.sp)
                                Text(String.format("%.1f MB", rel.apkSize / (1024.0 * 1024.0)), color = m3OnSurface(), fontFamily = ModernSansFont, fontSize = 12.sp)
                            }
                        }
                        is UpdateCheckState.Error -> {
                            Text("Status: Error", color = Color(0xFFFF5252), fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(updateCheckState.message, color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 12.sp)
                        }
                        is UpdateCheckState.Idle -> {
                            Text("Status: Ready to check", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Permission Warning if cannot install packages
            if (!canInstallPackages) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFF9100).copy(alpha = 0.12f))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Permission Required: Install Unknown Apps", color = Color(0xFFFF9100), fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("Android requires permission to install APK updates from this app.", color = m3OnSurfaceVariant(), fontFamily = ModernSansFont, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TactileBox(
                            onClick = onOpenPermissionSettings,
                            pressedScale = 0.94f,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFFF9100))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("Grant in Settings →", color = Color.Black, fontFamily = ModernSansFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Changelog Section if Release info is Available
            val activeRelease = when (updateCheckState) {
                is UpdateCheckState.UpdateAvailable -> updateCheckState.release
                is UpdateCheckState.UpToDate -> updateCheckState.release
                else -> null
            }
            if (activeRelease != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Release Notes",
                    color = m3OnSurface(),
                    fontFamily = ModernSansFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(m3SurfaceContainer())
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (activeRelease.changelog.isNotBlank()) activeRelease.changelog else "No release notes provided.",
                        color = m3OnSurfaceVariant(),
                        fontFamily = ModernSansFont,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Download & Progress Section
            when (downloadState) {
                is DownloadState.Downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(m3SurfaceContainer())
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading update...", color = m3OnSurface(), fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(downloadState.progress * 100).toInt()}%", color = LocalAccentColor.current, fontFamily = ModernSansFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${String.format("%.1f", downloadState.downloadedBytes / 1048576.0)} MB / ${String.format("%.1f", downloadState.totalBytes / 1048576.0)} MB",
                            color = m3OnSurfaceVariant(),
                            fontFamily = ModernSansFont,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(m3OnSurfaceVariant().copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(downloadState.progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(LocalAccentColor.current)
                            )
                        }
                    }
                }
                is DownloadState.ReadyToInstall -> {
                    TactileBox(
                        onClick = { onInstallFile(downloadState.apkFile) },
                        pressedScale = 0.94f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(LocalAccentColor.current)
                    ) {
                        Text(
                            text = "Launch Package Installer",
                            color = onAccent,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                is DownloadState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Download failed: ${downloadState.message}", color = Color(0xFFFF5252), fontFamily = ModernSansFont, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                            TactileBox(
                                onClick = { onDownloadAndInstall(updateCheckState.release) },
                                pressedScale = 0.94f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(m3SurfaceContainerHigh())
                            ) {
                                Text(
                                    text = "Retry Download",
                                    color = m3OnSurface(),
                                    fontFamily = ModernSansFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
                is DownloadState.Idle -> {
                    if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                        TactileBox(
                            onClick = { onDownloadAndInstall(updateCheckState.release) },
                            pressedScale = 0.94f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(LocalAccentColor.current)
                        ) {
                            Text(
                                text = "Download & Install (${String.format("%.1f MB", updateCheckState.release.apkSize / (1024.0 * 1024.0))})",
                                color = onAccent,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TactileBox(
                onClick = onCheckForUpdate,
                pressedScale = 0.94f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(m3SurfaceContainerHigh())
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphRefresh(color = LocalAccentColor.current, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (updateCheckState is UpdateCheckState.Checking) "Checking releases..." else "Check for Updates",
                        color = m3OnSurface(),
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
