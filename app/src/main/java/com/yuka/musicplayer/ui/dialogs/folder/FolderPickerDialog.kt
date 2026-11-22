package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.util.ColorContrastHelper
import com.yuka.musicplayer.util.StorageVolumeRoot
import com.yuka.musicplayer.util.discoverAvailableMusicRoots
import com.yuka.musicplayer.util.isAllFilesAccessGranted
import com.yuka.musicplayer.util.openAllFilesAccessSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    val defaultStorageDir = remember {
        Environment.getExternalStorageDirectory()?.takeIf { it.exists() && it.canRead() }
            ?: File("/storage/emulated/0")
    }
    var availableRoots by remember {
        mutableStateOf<List<StorageVolumeRoot>>(listOf(StorageVolumeRoot("Internal Storage", defaultStorageDir, false)))
    }
    LaunchedEffect(visible) {
        if (visible) {
            withContext(Dispatchers.IO) {
                val roots = discoverAvailableMusicRoots(context)
                if (roots.isNotEmpty()) {
                    availableRoots = roots
                }
            }
        }
    }

    var browsingDir by remember(visible, currentPath) {
        val initial = if (currentPath.exists() && currentPath.isDirectory && currentPath.canRead()) {
            currentPath
        } else {
            defaultStorageDir
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
            delay(180L)
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
            val sp = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
            val kewSp = com.yuka.musicplayer.core.prefs.AppPreferences.get(context)
            val uriStr = uri.toString()
            sp.edit().putString("music_root_tree_uri", uriStr).apply()
            kewSp.edit().putString("music_root_tree_uri", uriStr).apply()

            val resolved = resolvePhysicalPathFromUri(uri)
            if (resolved != null) {
                sp.edit().putString("music_root_tree_path", resolved).apply()
                kewSp.edit().putString("music_root_tree_path", resolved).apply()
                val f = File(resolved)
                if (f.exists() && f.isDirectory) {
                    onSelectDirectory(f)
                }
            }
        }
    }

    var allFilesGranted by remember { mutableStateOf(isAllFilesAccessGranted(context)) }
    var resumeRefreshNonce by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allFilesGranted = isAllFilesAccessGranted(context)
                resumeRefreshNonce++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val subdirectories by produceState<List<File>>(initialValue = emptyList(), key1 = browsingDir, key2 = resumeRefreshNonce) {
        value = withContext(Dispatchers.IO) {
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

            // Android 11+ All Files Access Recommendation Banner
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !allFilesGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFF9100).copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Android 11+ Full Access Recommended",
                            color = Color(0xFFFF9100),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                        Text(
                            text = "Enable 'All Files Access' to ensure folders with deleted .nomedia and direct FLAC files are never hidden by Android system storage.",
                            color = m3OnSurfaceVariant(),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFF9100).copy(alpha = 0.22f))
                            .clickable { openAllFilesAccessSettings(context) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Enable",
                            color = Color(0xFFFF9100),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
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
                }
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(detectedAudioFolders) { item ->
                        val isSelected = browsingDir.absolutePath == item.directory.absolutePath
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.22f) else m3SubtleFill(0.08f))
                                .clickable {
                                    browsingDir = item.directory
                                    manualPathInput = item.directory.absolutePath
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlyphMusicNote(color = if (isSelected) accent else m3OnSurfaceVariant(), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = item.displayName,
                                        color = if (isSelected) accent else m3OnSurface(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = ModernSansFont,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.audioCount} tracks",
                                    color = m3OnSurfaceVariant(),
                                    fontSize = 10.sp,
                                    fontFamily = ModernSansFont
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Breadcrumbs & Directory Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CURRENT DIRECTORY",
                    color = accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
                Text(
                    text = if (isManualInputVisible) "Tree View" else "Manual Edit",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ModernSansFont,
                    modifier = Modifier
                        .clickable { isManualInputVisible = !isManualInputVisible }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (isManualInputVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(m3SubtleFill(0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphEdit(color = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = manualPathInput,
                        onValueChange = { input ->
                            manualPathInput = input
                            val f = File(input.trim())
                            if (f.exists() && f.isDirectory && f.canRead()) {
                                browsingDir = f
                            }
                        },
                        textStyle = TextStyle(
                            color = m3OnSurface(),
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breadcrumbSegments.forEachIndexed { index, segment ->
                        val isLast = index == breadcrumbSegments.lastIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isLast) accent.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable {
                                    browsingDir = segment.directory
                                    manualPathInput = segment.directory.absolutePath
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = segment.title,
                                color = if (isLast) accent else m3OnSurfaceVariant(),
                                fontSize = 11.5.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = ModernSansFont
                            )
                        }
                        if (!isLast) {
                            GlyphChevronRight(color = m3OnSurfaceVariant().copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subdirectories List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Up/Parent Folder Row
                if (canNavigateParent) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(m3SubtleFill(0.05f))
                                .clickable {
                                    browsingDir.parentFile?.let { p ->
                                        browsingDir = p
                                        manualPathInput = p.absolutePath
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphFolderOpen(color = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = ".. (Parent Folder)",
                                color = accent,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = ModernSansFont
                            )
                        }
                    }
                }

                if (subdirectories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No accessible subfolders",
                                color = m3OnSurfaceVariant(),
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont
                            )
                        }
                    }
                } else {
                    items(subdirectories) { dir ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(m3SubtleFill(0.04f))
                                .clickable {
                                    browsingDir = dir
                                    manualPathInput = dir.absolutePath
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphFolder(color = accent.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = dir.name,
                                color = m3OnSurface(),
                                fontSize = 12.5.sp,
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
