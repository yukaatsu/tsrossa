package com.yuka.musicplayer.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.audio.PeqConfiguration
import com.yuka.musicplayer.audio.PeqEvaluator
import com.yuka.musicplayer.audio.UserPeqPreset
import com.yuka.musicplayer.audio.compressor.CompressorConfig
import com.yuka.musicplayer.audio.compressor.SavedCustomCompressorProfile
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.dialogs.compressor.DynamicsCompressorTabContent
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.ColorContrastHelper
import java.util.Locale
import kotlin.math.round

@Composable
fun ParametricEqModalSheet(
    visible: Boolean,
    config: PeqConfiguration,
    userPresets: List<UserPeqPreset> = emptyList(),
    compressorConfig: CompressorConfig = CompressorConfig(),
    savedCustomCompressorProfile: SavedCustomCompressorProfile? = null,
    audioEngine: AudioEngine? = null,
    onConfigChange: (PeqConfiguration) -> Unit,
    onCompressorConfigChange: (CompressorConfig) -> Unit = {},
    onSaveCustomCompressorProfile: (String) -> Unit = {},
    onDeleteCustomCompressorProfile: () -> Unit = {},
    onSaveUserPreset: (name: String, overwriteId: String?) -> Unit = { _, _ -> },
    onDeleteUserPreset: (presetId: String) -> Unit = {},
    onImportPresetsText: (rawText: String) -> Int = { 0 },
    onExportJsonFileRequest: (jsonContent: String, suggestedFileName: String) -> Unit = { _, _ -> },
    onImportJsonFileRequest: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.94f,
        maxWidth = 480.dp
    ) {
        val accent = LocalAccentColor.current
        val onAccent = ColorContrastHelper.getOnAccentColor(accent)
        val isDark = LocalIsDarkTheme.current
        val onSurfaceColor = m3OnSurface()
        val isEqEnabled = config.enabled
        val isAnyDspActive = config.enabled || compressorConfig.enabled
        val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
        val effectiveAccent = if (isEqEnabled) accent else disabledGrey
        val sectionAlpha = if (isEqEnabled) 1.0f else 0.50f
        val subtleBorder = onSurfaceColor.copy(alpha = if (isDark) 0.08f else 0.14f)
        val subtleDivider = onSurfaceColor.copy(alpha = if (isDark) 0.08f else 0.12f)

        var selectedStudioTab by remember { mutableIntStateOf(0) } // 0 = Parametric EQ, 1 = Dynamics
        var selectedBandIndex by remember { mutableIntStateOf(5) } // Default: 1 kHz Vocal Focus
        var showGuideCard by remember { mutableStateOf(false) }
        var showSavePresetCard by remember { mutableStateOf(false) }
        var showExportImportCard by remember { mutableStateOf(false) }
        var presetNameInput by remember { mutableStateOf("") }

        // Automatically collapse secondary panels when PEQ is bypassed
        LaunchedEffect(isEqEnabled) {
            if (!isEqEnabled) {
                showGuideCard = false
                showSavePresetCard = false
                showExportImportCard = false
            }
        }

        val activeUserPreset = remember(userPresets, config.selectedPresetId) {
            userPresets.find { it.id == config.selectedPresetId }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // =====================================================================
            // 1. UNIFIED AUDIO DSP STUDIO HEADER BAR & 2-TAB SELECTOR
            // =====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlyphEqualizer(
                            color = if (isAnyDspActive) accent else disabledGrey,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Audio DSP Studio",
                            color = onSurfaceColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            config.enabled && compressorConfig.enabled -> "ACTIVE • PEQ + DYNAMICS • 64-BIT DSP"
                            config.enabled -> "ACTIVE • 64-BIT PEQ • ZERO OS RESAMPLING"
                            compressorConfig.enabled -> "ACTIVE • 64-BIT DYNAMICS • ZERO-CLIP"
                            else -> "BYPASSED • 100% BIT-PERFECT DIRECT USB"
                        },
                        color = if (isAnyDspActive) accent else disabledGrey,
                        fontSize = 10.5.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedStudioTab == 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isEqEnabled) accent
                                    else m3SurfaceContainerHigh()
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isEqEnabled) accent else onSurfaceColor.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable {
                                    onConfigChange(config.copy(enabled = !config.enabled))
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEqEnabled) "PEQ ON" else "PEQ OFF",
                                color = if (isEqEnabled) onAccent else onSurfaceColor,
                                fontSize = 11.sp,
                                fontFamily = TerminalFont,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { isEnabled ->
                                onConfigChange(config.copy(enabled = isEnabled))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onAccent,
                                checkedTrackColor = accent,
                                checkedBorderColor = accent,
                                uncheckedThumbColor = onSurfaceColor.copy(alpha = 0.85f),
                                uncheckedTrackColor = m3SurfaceContainerHigh(),
                                uncheckedBorderColor = onSurfaceColor.copy(alpha = 0.28f)
                            )
                        )
                    }
                    ModalCloseButton(onClick = onDismissRequest)
                }
            }

            // Segmented 2-Tab Bar: [ Parametric EQ ] | [ Dynamics ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(m3SurfaceContainerHigh())
                    .border(1.dp, subtleBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple(0, "Parametric EQ", config.enabled),
                    Triple(1, "Dynamics", compressorConfig.enabled)
                ).forEach { (tabIdx, tabTitle, moduleOn) ->
                    val isSelected = selectedStudioTab == tabIdx
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) m3SurfaceContainerLow() else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) accent.copy(alpha = 0.45f) else Color.Transparent,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable { selectedStudioTab = tabIdx }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (moduleOn) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = tabTitle,
                            color = if (isSelected) onSurfaceColor else m3OnSurfaceVariant(),
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (selectedStudioTab == 1) {
                DynamicsCompressorTabContent(
                    visible = visible,
                    config = compressorConfig,
                    audioEngine = audioEngine,
                    savedCustomProfile = savedCustomCompressorProfile,
                    onConfigChange = onCompressorConfigChange,
                    onSaveCustomProfile = onSaveCustomCompressorProfile,
                    onDeleteCustomProfile = onDeleteCustomCompressorProfile
                )
            } else {

            // =====================================================================
            // 2. ERGONOMIC ACTION TOOLBAR (minHeight = 40.dp, 100% Phosphor Icons)
            // =====================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = sectionAlpha }
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save / Manage Preset
                PeqToolbarActionChip(
                    label = if (showSavePresetCard) "Close" else "Save",
                    isActive = showSavePresetCard,
                    enabled = isEqEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        showSavePresetCard = !showSavePresetCard
                        if (showSavePresetCard) {
                            showGuideCard = false
                            showExportImportCard = false
                            if (activeUserPreset != null) {
                                presetNameInput = activeUserPreset.name
                            }
                        }
                    }
                ) { tint ->
                    if (showSavePresetCard) {
                        GlyphClose(color = tint, modifier = Modifier.size(15.dp))
                    } else {
                        GlyphFloppyDisk(color = tint, modifier = Modifier.size(16.dp))
                    }
                }

                // Export / Import Preset
                PeqToolbarActionChip(
                    label = if (showExportImportCard) "Close" else "Export / Import",
                    isActive = showExportImportCard,
                    enabled = isEqEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        showExportImportCard = !showExportImportCard
                        if (showExportImportCard) {
                            showGuideCard = false
                            showSavePresetCard = false
                        }
                    }
                ) { tint ->
                    if (showExportImportCard) {
                        GlyphClose(color = tint, modifier = Modifier.size(15.dp))
                    } else {
                        GlyphShareNetwork(color = tint, modifier = Modifier.size(16.dp))
                    }
                }

                // Reset Flat
                PeqToolbarActionChip(
                    label = "Reset Flat",
                    isActive = false,
                    enabled = isEqEnabled,
                    accentColor = effectiveAccent,
                    subtle = true,
                    onClick = {
                        val flatBands = PeqConfiguration.createDefaultBands()
                        onConfigChange(
                            config.copy(
                                selectedPresetId = "flat",
                                preampDb = 0.0f,
                                bands = flatBands
                            )
                        )
                    }
                ) { tint ->
                    GlyphRefresh(color = tint, modifier = Modifier.size(15.dp))
                }

                // EQ Guide
                PeqToolbarActionChip(
                    label = if (showGuideCard) "Close" else "Guide",
                    isActive = showGuideCard,
                    enabled = isEqEnabled,
                    accentColor = effectiveAccent,
                    subtle = !showGuideCard,
                    onClick = {
                        showGuideCard = !showGuideCard
                        if (showGuideCard) {
                            showSavePresetCard = false
                            showExportImportCard = false
                        }
                    }
                ) { tint ->
                    if (showGuideCard) {
                        GlyphClose(color = tint, modifier = Modifier.size(15.dp))
                    } else {
                        GlyphQuestion(color = tint, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // =====================================================================
            // 2b. USER SAVED PRESETS ROW (Pure Tap-to-Select, Zero Tiny Delete X)
            // =====================================================================
            if (userPresets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = sectionAlpha }
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    userPresets.forEach { userPreset ->
                        val isSelected = config.selectedPresetId == userPreset.id
                        val contentTint = when {
                            isSelected -> effectiveAccent
                            isEqEnabled -> onSurfaceColor
                            else -> disabledGrey
                        }
                        Row(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) {
                                        if (isEqEnabled) accent.copy(alpha = 0.20f)
                                        else disabledGrey.copy(alpha = 0.14f)
                                    } else {
                                        m3SurfaceContainerLow()
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) effectiveAccent else subtleBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = isEqEnabled) {
                                    onConfigChange(
                                        config.copy(
                                            selectedPresetId = userPreset.id,
                                            preampDb = userPreset.preampDb,
                                            autoPreampEnabled = userPreset.autoPreampEnabled,
                                            bands = userPreset.bands
                                        )
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            GlyphBookmark(
                                color = contentTint,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = userPreset.name,
                                color = contentTint,
                                fontSize = 12.5.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 3. EXPANDABLE SECONDARY CARDS (Save/Delete, Export/Import, Guide)
            // =====================================================================
            AnimatedVisibility(
                visible = showSavePresetCard && isEqEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PeqSavePresetCard(
                    accentColor = effectiveAccent,
                    presetName = presetNameInput,
                    activeUserPreset = activeUserPreset,
                    onNameChange = { presetNameInput = it },
                    onSaveNew = {
                        if (presetNameInput.isNotBlank()) {
                            onSaveUserPreset(presetNameInput.trim(), null)
                            showSavePresetCard = false
                        }
                    },
                    onUpdateExisting = {
                        if (activeUserPreset != null) {
                            val targetName = if (presetNameInput.isNotBlank()) presetNameInput.trim() else activeUserPreset.name
                            onSaveUserPreset(targetName, activeUserPreset.id)
                            showSavePresetCard = false
                        }
                    },
                    onDeleteExisting = {
                        if (activeUserPreset != null) {
                            onDeleteUserPreset(activeUserPreset.id)
                            presetNameInput = ""
                            showSavePresetCard = false
                        }
                    },
                    onClose = { showSavePresetCard = false }
                )
            }

            AnimatedVisibility(
                visible = showExportImportCard && isEqEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PeqExportImportCard(
                    accentColor = effectiveAccent,
                    config = config,
                    activeUserPreset = activeUserPreset,
                    allUserPresets = userPresets,
                    onImportText = { text ->
                        val count = onImportPresetsText(text)
                        if (count > 0) {
                            showExportImportCard = false
                        }
                    },
                    onExportJsonFile = onExportJsonFileRequest,
                    onImportJsonFile = {
                        onImportJsonFileRequest()
                        showExportImportCard = false
                    },
                    onClose = { showExportImportCard = false }
                )
            }

            AnimatedVisibility(
                visible = showGuideCard && isEqEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PeqTutorialGuideCard(
                    accentColor = effectiveAccent,
                    onClose = { showGuideCard = false }
                )
            }

            // =====================================================================
            // 4. INTERACTIVE FREQUENCY RESPONSE GRAPH + INTEGRATED PRE-AMP BAR
            // =====================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(m3SurfaceContainerLow())
                    .border(
                        0.8.dp,
                        if (isEqEnabled) accent.copy(alpha = 0.25f) else subtleBorder,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                PeqFrequencyResponseGraph(
                    bands = config.bands,
                    preampDb = config.effectivePreampDb,
                    accentColor = accent,
                    isEnabled = isEqEnabled,
                    selectedBandIndex = selectedBandIndex,
                    onSelectBand = { selectedBandIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                )

                // Adaptive divider between graph and preamp bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(subtleDivider)
                )

                // Integrated Pre-Amp & Auto-Clip Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = sectionAlpha }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val displayPreamp = config.effectivePreampDb
                        val preampText = String.format(Locale.US, "Pre-Amp: %+.1f dB", displayPreamp)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = preampText,
                                color = if (isEqEnabled) onSurfaceColor else disabledGrey,
                                fontSize = 12.5.sp,
                                fontFamily = TerminalFont,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (config.autoPreampEnabled && displayPreamp < -0.05f) {
                                Text(
                                    text = "Anti-Clip",
                                    color = effectiveAccent,
                                    fontSize = 10.5.sp,
                                    fontFamily = TerminalFont,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (!config.autoPreampEnabled) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Slider(
                                value = config.preampDb,
                                enabled = isEqEnabled,
                                onValueChange = { newPre ->
                                    if (isEqEnabled) {
                                        onConfigChange(
                                            config.copy(
                                                preampDb = (round(newPre * 10f) / 10f),
                                                selectedPresetId = "custom"
                                            )
                                        )
                                    }
                                },
                                valueRange = -12.0f..6.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = effectiveAccent,
                                    activeTrackColor = effectiveAccent,
                                    inactiveTrackColor = m3SurfaceContainerHigh(),
                                    disabledThumbColor = disabledGrey,
                                    disabledActiveTrackColor = disabledGrey.copy(alpha = 0.6f),
                                    disabledInactiveTrackColor = m3SurfaceContainerHigh().copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Auto-Clip Toggle Pill (minHeight = 38.dp)
                    Box(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (config.autoPreampEnabled) effectiveAccent.copy(alpha = 0.18f)
                                else m3SurfaceContainerHigh()
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (config.autoPreampEnabled) effectiveAccent.copy(alpha = 0.45f)
                                else subtleBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = isEqEnabled) {
                                onConfigChange(
                                    config.copy(
                                        autoPreampEnabled = !config.autoPreampEnabled
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (config.autoPreampEnabled) "Auto-Clip ON" else "Manual",
                            color = if (config.autoPreampEnabled) effectiveAccent
                            else if (isEqEnabled) m3OnSurfaceVariant()
                            else disabledGrey,
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // =====================================================================
            // 5. POWERAMP-STYLE SPACIOUS 10-BAND VERTICAL FADER BANK
            //    (62.dp wide columns, 164.dp tall track, 0.1 dB precision, Light/Dark adaptive)
            // =====================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = sectionAlpha }
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "10-Band Faders",
                    color = if (isEqEnabled) onSurfaceColor else disabledGrey,
                    fontSize = 12.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Double-tap fader to reset 0 dB",
                    color = if (isEqEnabled) m3OnSurfaceVariant() else disabledGrey,
                    fontSize = 10.5.sp,
                    fontFamily = ModernSansFont
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = sectionAlpha }
                    .clip(RoundedCornerShape(16.dp))
                    .background(m3SurfaceContainerLow())
                    .border(
                        width = 0.8.dp,
                        color = subtleBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                config.bands.forEachIndexed { idx, band ->
                    val isSelected = selectedBandIndex == idx
                    PeqVerticalBandSlider(
                        band = band,
                        accentColor = accent,
                        isEnabled = isEqEnabled,
                        isSelected = isSelected,
                        onSelect = { selectedBandIndex = idx },
                        onGainChange = { newGain ->
                            selectedBandIndex = idx
                            val updatedBands = config.bands.toMutableList()
                            updatedBands[idx] = band.copy(gainDb = newGain)
                            onConfigChange(
                                config.copy(
                                    bands = updatedBands,
                                    selectedPresetId = "custom"
                                )
                            )
                        }
                    )
                }
            }

            // =====================================================================
            // 6. UAPP-STYLE COMPACT SELECTED BAND EDITOR
            //    (3-Way Phosphor Filter Bar 42.dp + 40.dp x 40.dp Steppers, 0.1 dB Gain Steps)
            // =====================================================================
            val currentBand = config.bands.getOrNull(selectedBandIndex)
            if (currentBand != null) {
                PeqBandFineTuneInspector(
                    band = currentBand,
                    accentColor = accent,
                    isEnabled = isEqEnabled,
                    onBandChange = { updatedBand ->
                        val updatedBands = config.bands.toMutableList()
                        updatedBands[selectedBandIndex] = updatedBand
                        onConfigChange(
                            config.copy(
                                bands = updatedBands,
                                selectedPresetId = "custom"
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            } // end selectedStudioTab == 0
        }
    }
}
