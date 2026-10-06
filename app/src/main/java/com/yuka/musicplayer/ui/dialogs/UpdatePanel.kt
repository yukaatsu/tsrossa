package com.yuka.musicplayer.ui.dialogs

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.RetroButton
import com.yuka.musicplayer.ui.diagnostics.LogSectionHeader
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import com.yuka.musicplayer.update.DownloadState
import com.yuka.musicplayer.update.ReleaseInfo
import com.yuka.musicplayer.update.UpdateCheckState
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.dp, LocalAccentColor.current, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (updateCheckState is UpdateCheckState.UpdateAvailable) "⚡" else "⬆",
                        color = LocalAccentColor.current,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SOFTWARE UPDATER",
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    letterSpacing = 0.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("✕ CLOSE", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalAccentColor.current.copy(alpha = 0.25f)))
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Version Info Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LocalAccentColor.current.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .background(Color(0xFF141414), RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("INSTALLED VERSION:", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                        Text("v$currentVersion", color = TerminalWhite, fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    when (updateCheckState) {
                        is UpdateCheckState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("STATUS: ", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                                Text("CHECKING GITHUB RELEASES...", color = Color(0xFFFFD700), fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateCheckState.UpToDate -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("STATUS: ", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                                Text("[✓] UP TO DATE (${updateCheckState.latestTag})", color = Color(0xFF00E676), fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateCheckState.UpdateAvailable -> {
                            val rel = updateCheckState.release
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("STATUS: ", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                                Text("[⚡ NEW UPDATE AVAILABLE!]", color = Color(0xFF00E676), fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("LATEST TAG:", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                                Text(rel.tagName, color = Color(0xFF00E676), fontFamily = TerminalFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("PACKAGE SIZE:", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                                Text(String.format("%.1f MB", rel.apkSize / (1024.0 * 1024.0)), color = TerminalWhite, fontFamily = TerminalFont, fontSize = 10.sp)
                            }
                        }
                        is UpdateCheckState.Error -> {
                            Text("STATUS: ERROR", color = Color.Red, fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(updateCheckState.message, color = Color(0xFFFF8888), fontFamily = TerminalFont, fontSize = 9.sp)
                        }
                        is UpdateCheckState.Idle -> {
                            Text("STATUS: READY TO CHECK", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Permission Warning if cannot install packages
            if (!canInstallPackages) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFF9100).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(Color(0xFF221600), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("⚠ PERMISSION REQUIRED: INSTALL UNKNOWN APPS", color = Color(0xFFFF9100), fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Android requires permission to install APK updates from this app.", color = TerminalWhite, fontFamily = TerminalFont, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFFFF9100), RoundedCornerShape(4.dp))
                                .clickable { onOpenPermissionSettings() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("GRANT IN SETTINGS →", color = Color(0xFFFF9100), fontFamily = TerminalFont, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Changelog Section if Update is Available
            if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                val rel = updateCheckState.release
                Spacer(modifier = Modifier.height(12.dp))
                LogSectionHeader("RELEASE NOTES & CHANGELOG")
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp)
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (rel.changelog.isNotBlank()) rel.changelog else "No release notes provided.",
                        color = TerminalWhite,
                        fontFamily = TerminalFont,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Download & Progress Section
            when (downloadState) {
                is DownloadState.Downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .background(Color(0xFF141414), RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("DOWNLOADING APK...", color = Color(0xFFFFD700), fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${(downloadState.progress * 100).toInt()}%", color = Color(0xFF00E676), fontFamily = TerminalFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${String.format("%.1f", downloadState.downloadedBytes / 1048576.0)} MB / ${String.format("%.1f", downloadState.totalBytes / 1048576.0)} MB",
                            color = TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(Color(0xFF222222), RoundedCornerShape(4.dp))
                                .border(1.dp, LocalAccentColor.current.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(downloadState.progress)
                                    .fillMaxHeight()
                                    .background(LocalAccentColor.current, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
                is DownloadState.ReadyToInstall -> {
                    RetroButton(
                        text = "[ ⚡ LAUNCH PACKAGE INSTALLER ]",
                        onClick = { onInstallFile(downloadState.apkFile) },
                        isSelected = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is DownloadState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Download failed: ${downloadState.message}", color = Color.Red, fontFamily = TerminalFont, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                            RetroButton(
                                text = "RETRY DOWNLOAD",
                                onClick = { onDownloadAndInstall(updateCheckState.release) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                is DownloadState.Idle -> {
                    if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                        RetroButton(
                            text = "[ ⬇ DOWNLOAD & INSTALL APK (${String.format("%.1f MB", updateCheckState.release.apkSize / (1024.0 * 1024.0))}) ]",
                            onClick = { onDownloadAndInstall(updateCheckState.release) },
                            isSelected = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            RetroButton(
                text = if (updateCheckState is UpdateCheckState.Checking) "CHECKING RELEASES..." else "[ ⟳ CHECK GITHUB FOR UPDATES ]",
                onClick = onCheckForUpdate,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
