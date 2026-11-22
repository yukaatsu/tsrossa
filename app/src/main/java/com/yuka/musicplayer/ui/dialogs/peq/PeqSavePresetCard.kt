package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.UserPeqPreset
import com.yuka.musicplayer.ui.components.GlyphAdd
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphDelete
import com.yuka.musicplayer.ui.components.GlyphFloppyDisk
import com.yuka.musicplayer.ui.components.GlyphRefresh
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.ColorContrastHelper

/**
 * Inline card for saving, updating, or safely deleting custom user PEQ presets.
 */
@Composable
internal fun PeqSavePresetCard(
    accentColor: Color,
    presetName: String,
    activeUserPreset: UserPeqPreset?,
    onNameChange: (String) -> Unit,
    onSaveNew: () -> Unit,
    onUpdateExisting: () -> Unit,
    onDeleteExisting: () -> Unit,
    onClose: () -> Unit
) {
    val deleteRed = Color(0xFFFF6B6B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(m3SurfaceContainerLow())
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlyphFloppyDisk(color = accentColor, modifier = Modifier.size(18.dp))
                Text(
                    text = "Save & Manage Preset",
                    color = m3OnSurface(),
                    fontSize = 13.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GlyphClose(color = accentColor, modifier = Modifier.size(13.dp))
                Text(
                    text = "Close",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = "Save your current EQ tuning as a new preset, update, or delete the active preset:",
            color = m3OnSurfaceVariant(),
            fontSize = 11.sp,
            fontFamily = ModernSansFont,
            lineHeight = 15.sp
        )

        // Text input field (minHeight = 44.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(m3SurfaceContainerHigh())
                .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (presetName.isEmpty()) {
                Text(
                    text = "Enter preset name (e.g. IEM Bass, Vocal Clear)...",
                    color = m3OnSurfaceVariant().copy(alpha = 0.6f),
                    fontSize = 12.5.sp,
                    fontFamily = ModernSansFont
                )
            }
            BasicTextField(
                value = presetName,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = m3OnSurface(),
                    fontSize = 12.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(accentColor),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Primary Save / Update Buttons (minHeight = 42.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (presetName.isNotBlank()) accentColor else m3SurfaceContainerHigh())
                    .clickable(enabled = presetName.isNotBlank(), onClick = onSaveNew)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val tint = if (presetName.isNotBlank()) ColorContrastHelper.getOnAccentColor(accentColor) else m3OnSurfaceVariant()
                GlyphAdd(color = tint, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Save New",
                    color = tint,
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (activeUserPreset != null) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onUpdateExisting)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphRefresh(color = accentColor, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Update",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Safe Dedicated Delete Active Preset Button (minHeight = 42.dp)
        if (activeUserPreset != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(deleteRed.copy(alpha = 0.12f))
                    .border(0.9.dp, deleteRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDeleteExisting)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                GlyphDelete(color = deleteRed, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Preset '${activeUserPreset.name}'",
                    color = deleteRed,
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
