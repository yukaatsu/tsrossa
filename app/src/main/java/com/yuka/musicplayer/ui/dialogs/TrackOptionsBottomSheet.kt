package com.yuka.musicplayer.ui.dialogs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.PlaybackSource
import com.yuka.musicplayer.model.ViewState
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.RetroActionItem
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.TerminalWhite
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bottom Sheet menu for individual audio track actions:
 * Play Now, Play Next, Add to Queue, Add/Remove from Playlist.
 */
@Composable
fun TrackOptionsBottomSheet(
    target: File?,
    onDismissRequest: () -> Unit,
    playlistSet: Set<String>,
    viewState: ViewState,
    onPlayTrack: (File, PlaybackSource) -> Unit,
    onPlayNextInQueue: (File) -> Unit,
    onAddToQueue: (File) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit
) {
    val inPlaylist = if (target != null) playlistSet.contains(target.absolutePath) else false

    CyberBottomSheet(
        visible = target != null,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.50f,
        maxWidth = 400.dp,
        wrapHeight = true
    ) {
        if (target != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRACK OPTIONS",
                    color = LocalAccentColor.current,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFont
                )
                Text(
                    text = "✕",
                    color = TerminalGray,
                    fontSize = 12.sp,
                    fontFamily = TerminalFont,
                    modifier = Modifier.clickable { onDismissRequest() }.padding(4.dp)
                )
            }
            Text(
                text = target.nameWithoutExtension.ifEmpty { target.name },
                color = TerminalWhite,
                fontSize = 13.sp,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            RetroActionItem(
                icon = "▶",
                label = "PLAY NOW",
                onClick = {
                    val targetSource = if (viewState == ViewState.PLAYLIST) {
                        PlaybackSource.PLAYLIST
                    } else if (viewState == ViewState.LIBRARY) {
                        PlaybackSource.LIBRARY
                    } else {
                        if (inPlaylist) PlaybackSource.PLAYLIST else PlaybackSource.LIBRARY
                    }
                    onDismissRequest()
                    onPlayTrack(target, targetSource)
                }
            )
            RetroActionItem(
                icon = "⏩",
                label = "PLAY NEXT (TOP OF QUEUE)",
                onClick = {
                    onDismissRequest()
                    onPlayNextInQueue(target)
                }
            )
            RetroActionItem(
                icon = "☰",
                label = "ADD TO QUEUE",
                onClick = {
                    onDismissRequest()
                    onAddToQueue(target)
                }
            )
            if (inPlaylist) {
                RetroActionItem(
                    icon = "✕",
                    label = "REMOVE FROM PLAYLIST",
                    isDestructive = true,
                    onClick = {
                        val pathToRemove = target.absolutePath
                        onDismissRequest()
                        onRemoveFromPlaylist(pathToRemove)
                    }
                )
            } else {
                RetroActionItem(
                    icon = "★",
                    label = "ADD TO PLAYLIST",
                    onClick = {
                        val pathToAdd = target.absolutePath
                        onDismissRequest()
                        onAddToPlaylist(pathToAdd)
                    }
                )
            }
        }
    }
}
