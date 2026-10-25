package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.RetroButton
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import java.io.File

/**
 * Confirmation dialog for deleting one or multiple audio tracks from local storage.
 */
@Composable
fun ConfirmDeleteDialog(
    visible: Boolean,
    tracksToDelete: List<File>,
    onDismissRequest: () -> Unit,
    onConfirmDelete: (List<File>) -> Unit
) {
    var rememberedTracks by remember { mutableStateOf<List<File>>(emptyList()) }
    if (tracksToDelete.isNotEmpty()) {
        rememberedTracks = tracksToDelete
    }
    val activeTracks = if (tracksToDelete.isNotEmpty()) tracksToDelete else rememberedTracks
    if (!visible && activeTracks.isEmpty()) return

    val isSingle = activeTracks.size == 1
    val titleText = if (isSingle) "Delete Track" else "Delete ${activeTracks.size} Tracks"
    val itemDescription = if (isSingle) {
        val file = activeTracks.first()
        file.nameWithoutExtension.ifEmpty { file.name }
    } else {
        "${activeTracks.size} selected tracks"
    }

    val destructiveRed = Color(0xFFFF5252)

    CyberBottomSheet(
        visible = visible && tracksToDelete.isNotEmpty(),
        onDismissRequest = onDismissRequest,
        wrapHeight = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    color = destructiveRed,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Are you sure you want to permanently delete $itemDescription from device storage?",
                color = m3OnSurface(),
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Physical audio files will be permanently deleted from storage and cannot be restored.",
                color = m3OnSurfaceVariant(),
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RetroButton(
                    text = "Cancel",
                    onClick = onDismissRequest,
                    color = m3OnSurface(),
                    modifier = Modifier.weight(1f)
                )

                RetroButton(
                    text = "Delete",
                    onClick = {
                        onDismissRequest()
                        onConfirmDelete(activeTracks)
                    },
                    isSelected = true,
                    color = destructiveRed,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
