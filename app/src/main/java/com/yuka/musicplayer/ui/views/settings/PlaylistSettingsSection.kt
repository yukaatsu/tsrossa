package com.yuka.musicplayer.ui.views.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant

@Composable
fun PlaylistSettingsSection(
    makeFolderAsPlaylist: Boolean,
    onMakeFolderAsPlaylistChange: (Boolean) -> Unit
) {
    SettingsCard(title = "Folder Playlists") {
        SettingsToggleRow(
            title = "Make Folder as Playlist",
            subtitle = "Automatically treat each subfolder inside your selected music directory as a playlist",
            isChecked = makeFolderAsPlaylist,
            onCheckedChange = onMakeFolderAsPlaylistChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "When enabled, any subfolder containing playable FLAC or WAV tracks inside your active music folder will automatically appear in the Playlists tab.",
            color = m3OnSurfaceVariant(),
            fontFamily = ModernSansFont,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
    }
}
