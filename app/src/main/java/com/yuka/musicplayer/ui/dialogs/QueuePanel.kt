package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.diagnostics.LogSectionHeader
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import java.io.File

@Composable
fun QueuePanel(
    currentTrack: TrackInfo?,
    priorityQueue: List<File>,
    upcomingTracks: List<File>,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onSelectTrack: (File) -> Unit,
    onClose: () -> Unit
) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text("☰", color = LocalAccentColor.current, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY QUEUE & UP NEXT (${priorityQueue.size})",
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (priorityQueue.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.Red.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable { onClearQueue() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text("CLEAR", color = Color.Red, fontSize = 9.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("✕", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LocalAccentColor.current.copy(alpha = 0.25f)))
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                LogSectionHeader("NOW PLAYING")
                if (currentTrack != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LocalAccentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .background(LocalAccentColor.current.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("▶", color = LocalAccentColor.current, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentTrack.title, color = TerminalWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = TerminalFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentTrack.artist, color = LocalAccentColor.current, fontSize = 10.sp, fontFamily = TerminalFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Text("No track actively playing", color = TerminalGray, fontFamily = TerminalFont, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                LogSectionHeader("UP NEXT (PRIORITY QUEUE) [${priorityQueue.size}]")
            }

            if (priorityQueue.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, TerminalGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ PRIORITY QUEUE EMPTY ]\nLong-press any track to 'Play Next' or 'Add to Queue'",
                            color = TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            } else {
                items(priorityQueue.mapIndexed { idx, f -> idx to f }) { (idx, file) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .background(LocalAccentColor.current.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                            .clickable { onSelectTrack(file) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("#%02d. ", idx + 1),
                            color = LocalAccentColor.current,
                            fontFamily = TerminalFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = file.nameWithoutExtension.ifEmpty { file.name },
                            color = TerminalWhite,
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clickable { onRemoveFromQueue(idx) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("✕", color = Color.Red.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = TerminalFont)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(10.dp)) }
            }

            if (upcomingTracks.isNotEmpty()) {
                item {
                    LogSectionHeader("UPCOMING IN PLAYLIST / FOLDER")
                }
                items(upcomingTracks.take(8).mapIndexed { idx, f -> idx to f }) { (idx, file) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .clickable { onSelectTrack(file) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• ",
                            color = TerminalGray,
                            fontFamily = TerminalFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = file.nameWithoutExtension.ifEmpty { file.name },
                            color = TerminalGray.copy(alpha = 0.85f),
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
