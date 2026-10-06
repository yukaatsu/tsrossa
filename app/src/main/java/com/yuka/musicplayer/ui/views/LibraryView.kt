package com.yuka.musicplayer.ui.views

import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.LibrarySortMode
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryView(
    currentDirectory: File,
    filesInDir: List<File>,
    playingFile: File?,
    playlistSet: Set<String>,
    searchQuery: String,
    sortMode: LibrarySortMode,
    onCycleSort: () -> Unit,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFileSelected: (File) -> Unit,
    onFileLongPressed: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val filteredIndexedFiles = remember(filesInDir, searchQuery) {
        val indexed = filesInDir.mapIndexed { index, file -> index to file }
        if (searchQuery.isEmpty()) indexed
        else indexed.filter { it.second.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "— FILE LIBRARY (${filteredIndexedFiles.size}) —",
                color = Color.White,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sort Toggle Button
                Box(
                    modifier = Modifier
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(LocalAccentColor.current.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCycleSort()
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⇵ ${sortMode.shortLabel}",
                        color = LocalAccentColor.current,
                        fontSize = 9.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Refresh Button
                Box(
                    modifier = Modifier
                        .border(1.dp, LocalAccentColor.current.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(LocalAccentColor.current.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRefresh()
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⟳ REFRESH",
                        color = LocalAccentColor.current,
                        fontSize = 9.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Styled Search Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .background(LocalAccentColor.current.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(">", color = LocalAccentColor.current, fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = LocalAccentColor.current, fontFamily = TerminalFont, fontSize = 11.sp),
                cursorBrush = SolidColor(LocalAccentColor.current),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "SEARCH FILES...",
                                color = TerminalGray.copy(alpha = 0.6f),
                                fontFamily = TerminalFont,
                                fontSize = 11.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (searchQuery.isNotEmpty()) {
                Text(
                    "✕",
                    color = TerminalGray,
                    fontSize = 12.sp,
                    fontFamily = TerminalFont,
                    modifier = Modifier.clickable { onSearchChange("") }.padding(4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (searchQuery.isEmpty() && currentDirectory.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .clickable { onFileSelected(File("..")) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📁 ..", color = LocalAccentColor.current, fontWeight = FontWeight.Normal, fontFamily = TerminalFont, fontSize = 11.5.sp)
                    }
                }
            }

            items(filteredIndexedFiles) { (originalIndex, file) ->
                val isDir = file.isDirectory
                val isPlaying = file.absolutePath == playingFile?.absolutePath
                val inPlaylist = playlistSet.contains(file.absolutePath)
                val displayName = if (isDir) file.name else (file.nameWithoutExtension.ifEmpty { file.name })
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            if (isPlaying) LocalAccentColor.current.copy(alpha = 0.10f) else Color(0xFF090D18),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 0.6.dp,
                            color = if (isPlaying) LocalAccentColor.current.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .combinedClickable(
                            onClick = { onFileSelected(file) },
                            onLongClick = {
                                if (!isDir) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFileLongPressed(file)
                                }
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDir) {
                        Text(
                            text = "📁",
                            color = LocalAccentColor.current,
                            fontFamily = TerminalFont,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (isPlaying) {
                        // Playing Wave Bars Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.height(12.dp)
                        ) {
                            Box(modifier = Modifier.width(2.dp).fillMaxHeight(0.6f).background(LocalAccentColor.current, RoundedCornerShape(1.dp)))
                            Box(modifier = Modifier.width(2.dp).fillMaxHeight(1.0f).background(LocalAccentColor.current, RoundedCornerShape(1.dp)))
                            Box(modifier = Modifier.width(2.dp).fillMaxHeight(0.4f).background(LocalAccentColor.current, RoundedCornerShape(1.dp)))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = displayName,
                        color = if (isPlaying) LocalAccentColor.current else TerminalWhite,
                        fontFamily = TerminalFont,
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isDir && (sortMode == LibrarySortMode.DATE_DESC || sortMode == LibrarySortMode.DATE_ASC)) {
                        val dateFormatted = remember(file.lastModified()) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(file.lastModified()))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = dateFormatted,
                            color = TerminalGray.copy(alpha = 0.6f),
                            fontFamily = TerminalFont,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }

                    if (!isDir) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val ext = file.extension.uppercase()
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF101626), RoundedCornerShape(4.dp))
                                .border(0.5.dp, if (isPlaying) LocalAccentColor.current.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = ext,
                                color = if (isPlaying) LocalAccentColor.current else TerminalGray,
                                fontSize = 8.5.sp,
                                fontFamily = TerminalFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (inPlaylist && !isDir) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "★",
                            color = LocalAccentColor.current,
                            fontSize = 11.sp,
                            fontFamily = TerminalFont
                        )
                    }
                }
            }
        }
    }
}
