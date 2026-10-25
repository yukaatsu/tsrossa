package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphFolder
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.util.ColorContrastHelper
import com.yuka.musicplayer.util.StorageVolumeRoot
import com.yuka.musicplayer.util.discoverAvailableMusicRoots
import java.io.File

@Composable
fun FolderPickerDialog(
    visible: Boolean,
    currentPath: File,
    onDismissRequest: () -> Unit,
    onSelectDirectory: (File) -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccentColor.current
    val onAccent = if (ColorContrastHelper.calculateLuminance(accent) > 0.45f) Color.Black else Color.White

    val availableRoots = remember(visible) {
        discoverAvailableMusicRoots(context)
    }

    var browsingDir by remember(visible, currentPath) {
        val initial = if (currentPath.exists() && currentPath.isDirectory) {
            currentPath
        } else {
            availableRoots.firstOrNull()?.directory ?: File("/storage/emulated/0/Music")
        }
        mutableStateOf(initial)
    }

    var manualPathInput by remember(browsingDir) {
        mutableStateOf(browsingDir.absolutePath)
    }

    // Android OpenDocumentTree system folder picker integration (for USB OTG / SD Cards)
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val resolved = resolvePhysicalPathFromUri(context, uri)
            if (resolved != null) {
                val f = File(resolved)
                if (f.exists() && f.isDirectory) {
                    onSelectDirectory(f)
                }
            }
        }
    }

    val subdirectories = remember(browsingDir) {
        runCatching {
            if (browsingDir.exists() && browsingDir.canRead() && browsingDir.isDirectory) {
                browsingDir.listFiles { file ->
                    file.isDirectory && !file.name.startsWith(".") && file.canRead()
                }?.sortedBy { it.name.lowercase() } ?: emptyList()
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
    }

    val activeRootVolume = remember(browsingDir, availableRoots) {
        availableRoots.firstOrNull { browsingDir.absolutePath.startsWith(it.directory.absolutePath) }
    }

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.88f,
        maxWidth = 460.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Music Root Directory",
                        color = m3OnSurface(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Scoped access to Music directory, microSD, or USB drive.",
                        color = m3OnSurfaceVariant(),
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ModalCloseButton(onClick = onDismissRequest)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Volume Switcher Pills (Music Folder, MicroSD, USB Drive, System Picker)
            Text(
                text = "STORAGE VOLUMES",
                color = accent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFont
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(availableRoots) { root ->
                    val isSelected = activeRootVolume?.directory?.absolutePath == root.directory.absolutePath
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) accent.copy(alpha = 0.22f) else m3SubtleFill(0.10f))
                            .clickable {
                                browsingDir = root.directory
                                manualPathInput = root.directory.absolutePath
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphFolder(color = if (isSelected) accent else m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = root.label,
                                color = if (isSelected) accent else m3OnSurface(),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = ModernSansFont
                            )
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(m3SubtleFill(0.12f))
                            .clickable { openDocumentTreeLauncher.launch(null) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "+ System Picker (USB / SD)",
                            color = accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ModernSansFont
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Path Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(m3SurfaceContainerHigh())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphFolder(color = accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = manualPathInput,
                    onValueChange = { manualPathInput = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = m3OnSurface(),
                        fontFamily = TerminalFont,
                        fontSize = 12.sp
                    ),
                    cursorBrush = SolidColor(accent),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .clickable {
                            val target = File(manualPathInput.trim())
                            if (target.exists() && target.isDirectory && target.canRead()) {
                                browsingDir = target
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Go",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subdirectories list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Parent navigation: only if parent exists and is not above the active root boundary
                val canNavigateParent = browsingDir.parentFile?.let { parent ->
                    activeRootVolume == null || (browsingDir.absolutePath != activeRootVolume.directory.absolutePath && parent.canRead())
                } ?: false

                if (canNavigateParent) {
                    val parent = browsingDir.parentFile!!
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(m3SurfaceContainerHigh())
                                .clickable {
                                    browsingDir = parent
                                    manualPathInput = parent.absolutePath
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphFolder(color = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = ".. (Parent Folder)",
                                color = accent,
                                fontSize = 13.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                items(subdirectories) { dir ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(m3SurfaceContainerHigh())
                            .clickable {
                                browsingDir = dir
                                manualPathInput = dir.absolutePath
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlyphFolder(color = m3OnSurfaceVariant().copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = dir.name,
                            color = m3OnSurface(),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(m3SurfaceContainerHigh())
                        .clickable { onDismissRequest() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = m3OnSurfaceVariant(),
                        fontSize = 13.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent)
                        .clickable {
                            val chosen = File(manualPathInput.trim())
                            val finalDir = if (chosen.exists() && chosen.isDirectory && chosen.canRead()) chosen else browsingDir
                            if (finalDir.exists() && finalDir.isDirectory && finalDir.canRead()) {
                                onSelectDirectory(finalDir)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select This Folder",
                        color = onAccent,
                        fontSize = 13.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Resolves a Storage Access Framework DocumentTree Uri into a physical File path if possible.
 */
private fun resolvePhysicalPathFromUri(context: Context, treeUri: Uri): String? {
    return runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parts = docId.split(":")
        val volumeId = parts.getOrNull(0) ?: return null
        val subPath = if (parts.size > 1) parts[1].trimStart('/', '\\') else ""

        if (volumeId.equals("primary", ignoreCase = true)) {
            val base = "/storage/emulated/0"
            if (subPath.isNotEmpty()) "$base/$subPath" else base
        } else {
            // Removable volume (e.g. 1A2B-3C4D or USB drive)
            val directPath = "/storage/$volumeId/$subPath".trimEnd('/')
            if (File(directPath).exists()) {
                directPath
            } else {
                val mntPath = "/mnt/media_rw/$volumeId/$subPath".trimEnd('/')
                if (File(mntPath).exists()) mntPath else directPath
            }
        }
    }.getOrNull()
}
