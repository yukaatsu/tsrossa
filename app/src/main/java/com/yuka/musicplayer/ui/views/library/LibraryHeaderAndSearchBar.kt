package com.yuka.musicplayer.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill

@Composable
internal fun LibraryHeaderAndSearchBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onCloseSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onBulkAddToPlaylist: () -> Unit,
    onBulkDelete: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    onChangeDirectory: () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current

    if (isSelectionMode) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
                .glassMorphic(
                    backgroundColor = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TactileBox(
                onClick = onCloseSelection,
                modifier = Modifier.size(32.dp).clip(CircleShape)
            ) {
                GlyphClose(color = accent, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "$selectedCount selected",
                color = m3OnSurface(),
                fontSize = 14.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Select All / Deselect All
            TactileBox(
                onClick = onToggleSelectAll,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(m3SubtleFill(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    color = m3OnSurface(),
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Bulk Add to Playlist
            if (selectedCount > 0) {
                TactileBox(
                    onClick = onBulkAddToPlaylist,
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                ) {
                    GlyphPlaylists(color = accent, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Bulk Delete
                TactileBox(
                    onClick = onBulkDelete,
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                ) {
                    GlyphDelete(color = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                }
            }
        }
    } else {
        // Standard Search Bar
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(m3SubtleFill(alpha = 0.08f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphSearch(color = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search titles, artists, albums...",
                        color = m3OnSurfaceVariant(),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onSearchFocusChanged(it.isFocused) },
                    textStyle = TextStyle(
                        color = m3OnSurface(),
                        fontSize = 14.sp,
                        fontFamily = ModernSansFont
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(accent)
                )
            }

            if (searchQuery.isNotEmpty()) {
                TactileBox(
                    onClick = { onSearchChange("") },
                    pressedScale = 0.85f,
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                ) {
                    GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Folder Picker Icon Button
            TactileBox(
                onClick = onChangeDirectory,
                pressedScale = 0.88f,
                modifier = Modifier.size(34.dp).clip(CircleShape)
            ) {
                GlyphFolder(color = accent, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            TactileBox(
                onClick = onRefresh,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isScanning) accent.copy(alpha = 0.16f) else Color.Transparent)
            ) {
                ScanningRefreshIcon(
                    isScanning = isScanning,
                    accent = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
