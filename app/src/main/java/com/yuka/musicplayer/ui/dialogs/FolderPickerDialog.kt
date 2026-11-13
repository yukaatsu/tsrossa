package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphChevronRight
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphEdit
import com.yuka.musicplayer.ui.components.GlyphFolder
import com.yuka.musicplayer.ui.components.GlyphFolderOpen
import com.yuka.musicplayer.ui.components.GlyphMusicNote
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Data model for detected folders on device that already contain audio tracks.
 */
data class DetectedAudioFolder(
    val directory: File,
    val displayName: String,
    val displayPath: String,
    val audioCount: Int
)

data class BreadcrumbSegment(
    val title: String,
    val directory: File
)

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "alac", "aif", "aiff", "dsd", "dff", "dsf"
)

/**
 * Rapidly queries MediaStore to find all folders containing audio files and their counts.
 */
private fun discoverFoldersWithAudio(context: Context): List<DetectedAudioFolder> {
    val folderCounts = linkedMapOf<String, Int>()
    runCatching {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        cursor?.use { c ->
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol != -1) {
                while (c.moveToNext()) {
                    val filePath = c.getString(dataCol) ?: continue
                    val parent = File(filePath).parent ?: continue
                    folderCounts[parent] = (folderCounts[parent] ?: 0) + 1
                }
            }
        }
    }
    val internalStorage = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    return folderCounts.entries.mapNotNull { (path, count) ->
        val dir = File(path)
        if (dir.exists() && dir.isDirectory && dir.canRead()) {
            val shortPath = if (path.startsWith(internalStorage)) {
                "Internal" + path.removePrefix(internalStorage)
            } else path
            DetectedAudioFolder(
                directory = dir,
                displayName = dir.name.ifBlank { shortPath },
                displayPath = shortPath,
                audioCount = count
            )
        } else null
    }.sortedByDescending { it.audioCount }
}

private fun buildBreadcrumbSegments(currentDir: File, roots: List<StorageVolumeRoot>): List<BreadcrumbSegment> {
    val segments = mutableListOf<BreadcrumbSegment>()
    var curr: File? = currentDir
    while (curr != null) {
        val matchingRoot = roots.firstOrNull { runCatching { it.directory.canonicalPath == curr?.canonicalPath }.getOrDefault(false) }
        val name = when {
            matchingRoot != null -> matchingRoot.label
            curr.parent == null || curr.name.isEmpty() -> "/"
            else -> curr.name
        }
        segments.add(0, BreadcrumbSegment(title = name, directory = curr))
        if (matchingRoot != null) {
            break
        }
        curr = curr.parentFile
    }
    return segments
}

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
        val initial = if (currentPath.exists() && currentPath.isDirectory && currentPath.canRead()) {
            val hasSubdirs = currentPath.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.isNotEmpty() == true
            val hasAudio = currentPath.listFiles { f -> f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS }?.isNotEmpty() == true
            if (!hasSubdirs && !hasAudio) {
                Environment.getExternalStorageDirectory()?.takeIf { it.exists() && it.canRead() } ?: currentPath
            } else {
                currentPath
            }
        } else {
            Environment.getExternalStorageDirectory()?.takeIf { it.exists() && it.canRead() }
                ?: availableRoots.firstOrNull()?.directory
                ?: File("/storage/emulated/0")
        }
        mutableStateOf(initial)
    }

    var isManualInputVisible by remember { mutableStateOf(false) }
    var manualPathInput by remember(browsingDir) {
        mutableStateOf(browsingDir.absolutePath)
    }

    var detectedAudioFolders by remember { mutableStateOf<List<DetectedAudioFolder>>(emptyList()) }
    LaunchedEffect(visible) {
        if (visible) {
            val detected = withContext(Dispatchers.IO) {
                discoverFoldersWithAudio(context)
            }
            detectedAudioFolders = detected
        }
    }

    // Android OpenDocumentTree system folder picker integration (for USB OTG / SD Cards)
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val resolved = resolvePhysicalPathFromUri(uri)
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

    val audioFileCountInCurrentDir = remember(browsingDir) {
        runCatching {
            if (browsingDir.exists() && browsingDir.canRead() && browsingDir.isDirectory) {
                browsingDir.listFiles { f ->
                    f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS
                }?.size ?: 0
            } else 0
        }.getOrDefault(0)
    }

    val activeRootVolume = remember(browsingDir, availableRoots) {
        availableRoots.firstOrNull { browsingDir.absolutePath.startsWith(it.directory.absolutePath) }
    }

    val breadcrumbSegments = remember(browsingDir, availableRoots) {
        buildBreadcrumbSegments(browsingDir, availableRoots)
    }

    val internalStorageRoot = Environment.getExternalStorageDirectory()?.canonicalPath ?: "/storage/emulated/0"
    val canNavigateParent = browsingDir.parentFile?.let { parent ->
        val parentCanonical = runCatching { parent.canonicalPath }.getOrDefault(parent.absolutePath)
        val browsingCanonical = runCatching { browsingDir.canonicalPath }.getOrDefault(browsingDir.absolutePath)
        if (browsingCanonical == internalStorageRoot) {
            false
        } else if (activeRootVolume?.isExternalRemovable == true) {
            val rootCanonical = runCatching { activeRootVolume.directory.canonicalPath }.getOrDefault(activeRootVolume.directory.absolutePath)
            browsingCanonical != rootCanonical && parent.canRead()
        } else {
            parent.canRead() && (parentCanonical.startsWith(internalStorageRoot) || parentCanonical == internalStorageRoot)
        }
    } ?: false

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.90f,
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
                        text = "Select Music Directory",
                        color = m3OnSurface(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModernSansFont
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Choose a folder to load and scan your music collection.",
                        color = m3OnSurfaceVariant(),
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ModalCloseButton(onClick = onDismissRequest)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Storage Volume Quick-Jumps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORAGE VOLUMES",
                    color = accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(availableRoots) { root ->
                    val isSelected = browsingDir.absolutePath == root.directory.absolutePath
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
                            text = "+ System Picker",
                            color = accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ModernSansFont
                        )
                    }
                }
            }

            // Quick 1-Tap Detected Music Folders Section
            if (detectedAudioFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETECTED MUSIC FOLDERS",
                        color = accent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFont
                    )
                    Text(
                        text = "${detectedAudioFolders.size} found",
                        color = m3OnSurfaceVariant(),
                        fontSize = 10.sp,
                        fontFamily = TerminalFont
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(detectedAudioFolders) { item ->
                        val isCurrent = browsingDir.absolutePath == item.directory.absolutePath
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) accent.copy(alpha = 0.22f) else m3SurfaceContainerHigh())
                                .clickable {
                                    browsingDir = item.directory
                                    manualPathInput = item.directory.absolutePath
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphMusicNote(color = accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = item.displayName,
                                    color = m3OnSurface(),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ModernSansFont,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${item.audioCount} tracks",
                                    color = accent,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = TerminalFont
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Breadcrumb Bar OR Manual Path Input
            if (isManualInputVisible) {
                // Manual Text Input Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(m3SurfaceContainerHigh())
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphFolderOpen(color = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = manualPathInput,
                        onValueChange = { manualPathInput = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = m3OnSurface(),
                            fontFamily = TerminalFont,
                            fontSize = 11.5.sp
                        ),
                        cursorBrush = SolidColor(accent),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.20f))
                            .clickable {
                                val target = File(manualPathInput.trim())
                                if (target.exists() && target.isDirectory && target.canRead()) {
                                    browsingDir = target
                                    isManualInputVisible = false
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                manualPathInput = browsingDir.absolutePath
                                isManualInputVisible = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                // Interactive Breadcrumb Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(m3SurfaceContainerHigh())
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphFolderOpen(color = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))

                    val breadcrumbScrollState = rememberScrollState()
                    LaunchedEffect(breadcrumbSegments.size) {
                        breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(breadcrumbScrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbSegments.forEachIndexed { index, seg ->
                            val isLast = index == breadcrumbSegments.lastIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        browsingDir = seg.directory
                                        manualPathInput = seg.directory.absolutePath
                                    }
                                    .padding(horizontal = 5.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = seg.title,
                                    color = if (isLast) accent else m3OnSurface(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = ModernSansFont,
                                    maxLines = 1
                                )
                            }
                            if (!isLast) {
                                GlyphChevronRight(
                                    color = m3OnSurfaceVariant().copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Edit Icon to toggle manual input
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(m3SubtleFill(0.12f))
                            .clickable {
                                manualPathInput = browsingDir.absolutePath
                                isManualInputVisible = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphEdit(color = accent, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subdirectories list & folder status
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Parent navigation item: can navigate up whenever inside internal storage or SD root
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
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphFolder(color = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = ".. (Parent Folder: ${parent.name.ifBlank { "Root" }})",
                                color = accent,
                                fontSize = 12.5.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Current Directory Info Banner
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.10f))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlyphFolderOpen(color = accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current: ${browsingDir.name.ifBlank { "Storage Root" }}",
                                color = m3OnSurface(),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ModernSansFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$audioFileCountInCurrentDir audio files in this folder • ${subdirectories.size} subfolders",
                                color = accent,
                                fontSize = 10.5.sp,
                                fontFamily = TerminalFont
                            )
                        }
                    }
                }

                // If no subdirectories found, show an informative card instead of a blank void!
                if (subdirectories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (audioFileCountInCurrentDir > 0) {
                                    GlyphMusicNote(color = accent, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "$audioFileCountInCurrentDir Audio Files in '${browsingDir.name}'",
                                        color = m3OnSurface(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ModernSansFont
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No subfolders inside. Tap 'Select This Folder' below to use it.",
                                        color = m3OnSurfaceVariant(),
                                        fontSize = 11.5.sp,
                                        fontFamily = ModernSansFont
                                    )
                                } else {
                                    GlyphFolder(color = m3OnSurfaceVariant().copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Subfolders in '${browsingDir.name}'",
                                        color = m3OnSurfaceVariant(),
                                        fontSize = 12.sp,
                                        fontFamily = ModernSansFont
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(m3SubtleFill(0.12f))
                                            .clickable {
                                                Environment.getExternalStorageDirectory()?.let {
                                                    browsingDir = it
                                                    manualPathInput = it.absolutePath
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Go to Internal Storage Root",
                                            color = accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = ModernSansFont
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Subdirectory rows with 1-Tap Select Button
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
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlyphFolder(color = m3OnSurfaceVariant().copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = dir.name,
                            color = m3OnSurface(),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Quick 1-Tap Select Pill Button on each row
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .clickable {
                                    onSelectDirectory(dir)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Select",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ModernSansFont
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        .weight(1.6f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent)
                        .clickable {
                            val chosen = File(manualPathInput.trim())
                            val finalDir = if (isManualInputVisible && chosen.exists() && chosen.isDirectory && chosen.canRead()) chosen else browsingDir
                            if (finalDir.exists() && finalDir.isDirectory && finalDir.canRead()) {
                                onSelectDirectory(finalDir)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Use Current Folder",
                        color = onAccent,
                        fontSize = 13.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Resolves a Storage Access Framework DocumentTree Uri into a physical File path if possible.
 */
private fun resolvePhysicalPathFromUri(treeUri: Uri): String? {
    return runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri) ?: return null
        val parts = docId.split(":", limit = 2)
        val volumeId = parts.getOrNull(0) ?: return null
        val subPath = parts.getOrNull(1)?.trimStart('/', '\\') ?: ""

        if (volumeId.equals("primary", ignoreCase = true) || volumeId.equals("home", ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
            if (subPath.isNotEmpty()) "$base/$subPath" else base
        } else {
            // Removable volume (e.g. 1A2B-3C4D or USB drive)
            val directPath = if (subPath.isNotEmpty()) "/storage/$volumeId/$subPath" else "/storage/$volumeId"
            if (File(directPath).exists()) {
                directPath
            } else {
                val mntPath = if (subPath.isNotEmpty()) "/mnt/media_rw/$volumeId/$subPath" else "/mnt/media_rw/$volumeId"
                if (File(mntPath).exists()) mntPath else directPath
            }
        }
    }.getOrNull()
}
