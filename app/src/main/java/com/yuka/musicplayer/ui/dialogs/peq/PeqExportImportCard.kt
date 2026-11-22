package com.yuka.musicplayer.ui.dialogs

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.PeqConfiguration
import com.yuka.musicplayer.audio.PeqPreferencesHelper
import com.yuka.musicplayer.audio.UserPeqPreset
import com.yuka.musicplayer.ui.components.GlyphClipboard
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphDownloadSimple
import com.yuka.musicplayer.ui.components.GlyphFloppyDisk
import com.yuka.musicplayer.ui.components.GlyphFolderOpen
import com.yuka.musicplayer.ui.components.GlyphShareNetwork
import com.yuka.musicplayer.ui.components.GlyphUploadSimple
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.ColorContrastHelper
import java.util.Locale

/**
 * Inline card for exporting and importing custom PEQ presets (100% Phosphor Icons, Full English).
 */
@Composable
internal fun PeqExportImportCard(
    accentColor: Color,
    config: PeqConfiguration,
    activeUserPreset: UserPeqPreset?,
    allUserPresets: List<UserPeqPreset>,
    onImportText: (String) -> Unit,
    onExportJsonFile: (jsonContent: String, suggestedFileName: String) -> Unit,
    onImportJsonFile: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val onAccent = ColorContrastHelper.getOnAccentColor(accentColor)
    val subtleBorder = onSurfaceColor.copy(alpha = if (isDark) 0.12f else 0.16f)
    var manualInputText by remember { mutableStateOf("") }

    val presetToExport = remember(activeUserPreset, config) {
        activeUserPreset ?: PeqPreferencesHelper.createPresetFromCurrentConfig("Custom EQ", config)
    }

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
                GlyphShareNetwork(color = accentColor, modifier = Modifier.size(18.dp))
                Text(
                    text = "Export & Import Preset",
                    color = onSurfaceColor,
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

        // --- SECTION 1: EXPORT ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlyphUploadSimple(color = accentColor, modifier = Modifier.size(15.dp))
                Text(
                    text = "Export / Share Preset",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Active: \"${presetToExport.name}\" (${String.format(Locale.US, "%+.1f dB", presetToExport.preampDb)})",
                color = onSurfaceColor,
                fontSize = 10.5.sp,
                fontFamily = TerminalFont
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Copy Share Code
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor)
                        .clickable {
                            val code = PeqPreferencesHelper.exportPresetToShareCode(presetToExport)
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, "Copied '${presetToExport.name}' code to Clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphClipboard(color = onAccent, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Copy",
                        color = onAccent,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Android Share Intent
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable {
                            val code = PeqPreferencesHelper.exportPresetToShareCode(presetToExport)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, code)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share PEQ Preset (${presetToExport.name})")
                            context.startActivity(shareIntent)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphShareNetwork(color = accentColor, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Share",
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. Export File .json
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, subtleBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            val jsonStr = PeqPreferencesHelper.exportPresetsToJsonString(listOf(presetToExport))
                            val safeName = presetToExport.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                            onExportJsonFile(jsonStr, "peq_${safeName}.json")
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphFloppyDisk(color = onSurfaceColor, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = ".json",
                        color = onSurfaceColor,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (allUserPresets.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(m3SurfaceContainerLow())
                        .border(0.8.dp, subtleBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            val jsonStr = PeqPreferencesHelper.exportPresetsToJsonString(allUserPresets)
                            onExportJsonFile(jsonStr, "peq_all_presets.json")
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphUploadSimple(color = m3OnSurfaceVariant(), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Backup All Presets (${allUserPresets.size}) to .json",
                        color = m3OnSurfaceVariant(),
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- SECTION 2: IMPORT ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlyphDownloadSimple(color = accentColor, modifier = Modifier.size(15.dp))
                Text(
                    text = "Import Preset from Friend / Community",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Copy a preset code from Discord/chat, then tap below to apply it immediately:",
                color = m3OnSurfaceVariant(),
                fontSize = 10.5.sp,
                fontFamily = ModernSansFont
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. One-click Paste & Import from Clipboard
                Row(
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor)
                        .clickable {
                            val clipData = clipboardManager.getText()?.text ?: ""
                            if (clipData.isNotBlank()) {
                                onImportText(clipData)
                            } else {
                                Toast.makeText(context, "Clipboard is empty! Copy a preset code first.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphClipboard(color = onAccent, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Paste Clipboard",
                        color = onAccent,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Import File .json
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onImportJsonFile)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphFolderOpen(color = accentColor, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JSON File",
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Optional Manual text field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(m3SurfaceContainerHigh())
                    .border(0.8.dp, subtleBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (manualInputText.isEmpty()) {
                    Text(
                        text = "Or type/paste TSROSSA-PEQ1:... code here",
                        color = m3OnSurfaceVariant().copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont
                    )
                }
                BasicTextField(
                    value = manualInputText,
                    onValueChange = { manualInputText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 11.5.sp,
                        fontFamily = TerminalFont
                    ),
                    cursorBrush = SolidColor(accentColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (manualInputText.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor)
                        .clickable { onImportText(manualInputText.trim()) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphDownloadSimple(color = onAccent, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import Typed Code",
                        color = onAccent,
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
