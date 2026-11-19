package com.yuka.musicplayer.ui.dialogs

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.*
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.*
import java.util.Locale
import kotlin.math.*

@Composable
fun ParametricEqModalSheet(
    visible: Boolean,
    config: PeqConfiguration,
    userPresets: List<UserPeqPreset> = emptyList(),
    onConfigChange: (PeqConfiguration) -> Unit,
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
        val isDark = LocalIsDarkTheme.current
        val onSurfaceColor = m3OnSurface()
        val isEqEnabled = config.enabled
        val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
        val effectiveAccent = if (isEqEnabled) accent else disabledGrey
        val sectionAlpha = if (isEqEnabled) 1.0f else 0.50f
        val subtleBorder = onSurfaceColor.copy(alpha = if (isDark) 0.08f else 0.14f)
        val subtleDivider = onSurfaceColor.copy(alpha = if (isDark) 0.08f else 0.12f)

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
            // 1. CLEAN HEADER BAR & MASTER BYPASS SWITCH
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
                        GlyphEqualizer(color = effectiveAccent, modifier = Modifier.size(22.dp))
                        Text(
                            text = "Parametric EQ",
                            color = onSurfaceColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModernSansFont
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEqEnabled) {
                            "ACTIVE • 64-BIT DSP • ZERO OS RESAMPLING"
                        } else {
                            "BYPASSED • 100% BIT-PERFECT DIRECT USB"
                        },
                        color = if (isEqEnabled) accent else disabledGrey,
                        fontSize = 10.5.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { isEnabled ->
                            onConfigChange(config.copy(enabled = isEnabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = m3OnSurfaceVariant(),
                            uncheckedTrackColor = m3SurfaceContainerHigh()
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ModalCloseButton(onClick = onDismissRequest)
                }
            }

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
                        val maxBoost = remember(config.bands) { PeqEvaluator.calculateMaxBoost(config.bands) }
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
                            if (config.autoPreampEnabled && maxBoost > 0.05f) {
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
        }
    }
}

/**
 * Ergonomic toolbar chip (minHeight = 40.dp) with Phosphor Icon + text label.
 */
@Composable
private fun PeqToolbarActionChip(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    accentColor: Color,
    subtle: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val contentTint = when {
        isActive -> Color.White
        !enabled -> disabledGrey
        subtle -> m3OnSurfaceVariant()
        else -> accentColor
    }
    val bgColor = when {
        isActive -> accentColor
        subtle -> m3SurfaceContainerLow()
        else -> m3SurfaceContainerHigh()
    }
    val borderColor = when {
        isActive -> Color.Transparent
        subtle -> onSurfaceColor.copy(alpha = if (enabled) (if (isDark) 0.12f else 0.16f) else 0.06f)
        else -> accentColor.copy(alpha = if (enabled) 0.28f else 0.08f)
    }

    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(width = 0.8.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        icon(contentTint)
        Text(
            text = label,
            color = contentTint,
            fontSize = 12.sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Phosphor curve icon helper matching PeqFilterType.
 */
@Composable
private fun PeqFilterTypeIcon(
    filterType: PeqFilterType,
    color: Color,
    modifier: Modifier = Modifier.size(16.dp)
) {
    when (filterType) {
        PeqFilterType.LOW_SHELF -> GlyphFilterLowShelf(color = color, modifier = modifier)
        PeqFilterType.PEAKING -> GlyphFilterPeaking(color = color, modifier = modifier)
        PeqFilterType.HIGH_SHELF -> GlyphFilterHighShelf(color = color, modifier = modifier)
    }
}

/**
 * Ergonomic 40.dp x 40.dp Phosphor stepper button (Minus / Plus) for accurate thumb taps.
 */
@Composable
private fun PeqStepperButton(
    isPlus: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    TactileBox(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(m3SurfaceContainerHigh().copy(alpha = if (enabled) 1.0f else 0.45f))
            .border(
                width = 0.8.dp,
                color = if (enabled) accentColor.copy(alpha = 0.24f) else onSurfaceColor.copy(alpha = if (isDark) 0.06f else 0.10f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        val iconColor = if (enabled) onSurfaceColor else disabledGrey
        if (isPlus) {
            GlyphAdd(color = iconColor, modifier = Modifier.size(18.dp))
        } else {
            GlyphMinus(color = iconColor, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 60 FPS Logarithmic Frequency Response Graph with Light/Dark adaptive grid & interactive band dots.
 */
@Composable
private fun PeqFrequencyResponseGraph(
    bands: List<PeqBand>,
    preampDb: Float,
    accentColor: Color,
    isEnabled: Boolean,
    selectedBandIndex: Int,
    onSelectBand: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val zeroGridColor = onSurfaceColor.copy(
        alpha = if (isEnabled) (if (isDark) 0.24f else 0.30f) else 0.12f
    )
    val minorGridColor = onSurfaceColor.copy(
        alpha = if (isEnabled) (if (isDark) 0.08f else 0.14f) else 0.05f
    )

    val samplePoints = remember {
        val numPoints = 96
        val logMin = log10(20.0)
        val logMax = log10(20000.0)
        FloatArray(numPoints) { i ->
            val frac = i.toDouble() / (numPoints - 1)
            10.0.pow(logMin + frac * (logMax - logMin)).toFloat()
        }
    }

    val curveGainValues = remember(bands, preampDb) {
        PeqEvaluator.evaluateCurve(bands, samplePoints, preampDb)
    }

    val currentOnSelectBand by rememberUpdatedState(onSelectBand)

    Canvas(
        modifier = modifier.pointerInput(bands, isEnabled) {
            if (!isEnabled) return@pointerInput
            detectTapGestures { offset ->
                val w = size.width.toFloat()
                val logMin = log10(20.0)
                val logSpan = log10(20000.0) - logMin
                var nearestIdx = 0
                var minDiff = Float.MAX_VALUE
                bands.forEachIndexed { i, b ->
                    val bx = ((log10(b.freqHz.toDouble().coerceIn(20.0, 20000.0)) - logMin) / logSpan * w).toFloat()
                    val diff = abs(bx - offset.x)
                    if (diff < minDiff) {
                        minDiff = diff
                        nearestIdx = i
                    }
                }
                if (minDiff < 48.dp.toPx()) {
                    currentOnSelectBand(nearestIdx)
                }
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val logMin = log10(20.0)
        val logMax = log10(20000.0)
        val logSpan = logMax - logMin

        val minDb = -15.0f
        val maxDb = 15.0f
        val dbSpan = maxDb - minDb

        fun freqToX(freq: Double): Float {
            val clamped = freq.coerceIn(20.0, 20000.0)
            val frac = (log10(clamped) - logMin) / logSpan
            return (frac * w).toFloat()
        }

        fun dbToY(db: Float): Float {
            val clamped = db.coerceIn(minDb, maxDb)
            val frac = (maxDb - clamped) / dbSpan
            return frac * h
        }

        // Horizontal dB grid lines (+12, +6, 0, -6, -12 dB) — Light/Dark adaptive
        val gridDbs = floatArrayOf(12f, 6f, 0f, -6f, -12f)
        for (gDb in gridDbs) {
            val y = dbToY(gDb)
            val isZero = gDb == 0f
            drawLine(
                color = if (isZero) zeroGridColor else minorGridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (isZero) 1.2f else 0.8f
            )
        }

        // Vertical octave frequency grid lines — Light/Dark adaptive
        val gridFreqs = doubleArrayOf(31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        for (gFreq in gridFreqs) {
            val x = freqToX(gFreq)
            drawLine(
                color = minorGridColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 0.8f
            )
        }

        val curveColor = if (isEnabled) accentColor else Color.Gray.copy(alpha = 0.55f)

        val curvePath = Path()
        val fillPath = Path()
        for (i in samplePoints.indices) {
            val x = freqToX(samplePoints[i].toDouble())
            val y = dbToY(curveGainValues[i])
            if (i == 0) {
                curvePath.moveTo(x, y)
                fillPath.moveTo(x, dbToY(0f))
                fillPath.lineTo(x, y)
            } else {
                curvePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val lastX = freqToX(samplePoints.last().toDouble())
        fillPath.lineTo(lastX, dbToY(0f))
        fillPath.close()

        // Gradient fill under curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    curveColor.copy(alpha = if (isEnabled) 0.26f else 0.08f),
                    curveColor.copy(alpha = 0.01f)
                ),
                startY = 0f,
                endY = h
            ),
            style = Fill
        )

        // Curve outline
        drawPath(
            path = curvePath,
            color = curveColor,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Interactive dots for each band (high-contrast on both Light & Dark themes)
        val nodeColor = if (isEnabled) accentColor else Color.Gray.copy(alpha = 0.60f)
        bands.forEachIndexed { idx, band ->
            val bx = freqToX(band.freqHz.toDouble())
            val by = dbToY(band.gainDb + preampDb)
            val isSelected = selectedBandIndex == idx

            if (isSelected) {
                drawCircle(
                    color = nodeColor.copy(alpha = 0.32f),
                    radius = 9.dp.toPx(),
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = nodeColor,
                    radius = 5.2.dp.toPx(),
                    center = Offset(bx, by)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.2.dp.toPx(),
                    center = Offset(bx, by)
                )
            } else {
                drawCircle(
                    color = nodeColor,
                    radius = 3.2.dp.toPx(),
                    center = Offset(bx, by)
                )
            }
        }
    }
}

/**
 * Poweramp-inspired spacious vertical EQ band fader:
 * - Wide 62.dp column & tall 164.dp touchable vertical track
 * - 0.1 dB precision on tap & drag + double-tap reset to 0.0 dB
 * - Light & Dark theme adaptive inactive rail, 0 dB center tick, and thumb contrast
 */
@Composable
private fun PeqVerticalBandSlider(
    band: PeqBand,
    accentColor: Color,
    isEnabled: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onGainChange: (Float) -> Unit
) {
    val freqText = remember(band.freqHz) { formatShortFreq(band.freqHz) }
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val effectiveAccent = if (isEnabled) accentColor else disabledGrey

    // Adaptive contrast palette for both Dark Mode and Light Mode
    val inactiveRailColor = if (isDark) {
        Color.White.copy(alpha = if (isEnabled) 0.16f else 0.07f)
    } else {
        onSurfaceColor.copy(alpha = if (isEnabled) 0.24f else 0.10f)
    }
    val zeroTickColor = if (isDark) {
        Color.White.copy(alpha = if (isEnabled) 0.42f else 0.18f)
    } else {
        onSurfaceColor.copy(alpha = if (isEnabled) 0.52f else 0.22f)
    }

    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    var sliderHeightPx by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) effectiveAccent.copy(alpha = if (isEnabled) 0.14f else 0.08f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 0.9.dp else 0.dp,
                color = if (isSelected) effectiveAccent.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = isEnabled) { currentOnSelect() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Live Gain dB Readout (0.1 dB precision)
        val gainStr = String.format(Locale.US, "%+.1f", band.gainDb)
        Text(
            text = gainStr,
            color = when {
                !isEnabled -> disabledGrey
                isSelected -> accentColor
                abs(band.gainDb) > 0.05f -> onSurfaceColor
                else -> m3OnSurfaceVariant()
            },
            fontSize = 11.sp,
            fontFamily = TerminalFont,
            fontWeight = if (isSelected || abs(band.gainDb) > 0.05f) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )

        // 2. Tall 164.dp Vertical Fader Track (full column touch width, 0.1 dB step)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
                .onGloballyPositioned { coords ->
                    sliderHeightPx = coords.size.height.toFloat()
                }
                .pointerInput(band.index, isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            currentOnSelect()
                            currentOnGainChange(0.0f)
                        },
                        onTap = { offset ->
                            currentOnSelect()
                            val pad = 10.dp.toPx()
                            val trackLen = sliderHeightPx - 2f * pad
                            if (trackLen > 0f) {
                                val clampedY = offset.y.coerceIn(pad, sliderHeightPx - pad)
                                val frac = (sliderHeightPx - pad - clampedY) / trackLen
                                val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)
                                val stepped = (round(newGain.toDouble() * 10.0) / 10.0).toFloat()
                                currentOnGainChange(stepped)
                            }
                        }
                    )
                }
                .pointerInput(band.index, isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { currentOnSelect() },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val pad = 10.dp.toPx()
                            val trackLen = sliderHeightPx - 2f * pad
                            if (trackLen > 0f) {
                                val clampedY = change.position.y.coerceIn(pad, sliderHeightPx - pad)
                                val frac = (sliderHeightPx - pad - clampedY) / trackLen
                                val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)
                                val stepped = (round(newGain.toDouble() * 10.0) / 10.0).toFloat()
                                currentOnGainChange(stepped)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val topY = 10.dp.toPx()
                val botY = size.height - 10.dp.toPx()
                val trackLen = botY - topY
                val midY = topY + trackLen / 2f

                // Inactive rail (adaptive for Dark & Light mode)
                drawLine(
                    color = inactiveRailColor,
                    start = Offset(cx, topY),
                    end = Offset(cx, botY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Zero dB center reference tick (adaptive for Dark & Light mode)
                drawLine(
                    color = zeroTickColor,
                    start = Offset(cx - 8.dp.toPx(), midY),
                    end = Offset(cx + 8.dp.toPx(), midY),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Active gain fill from 0 dB to thumb
                val frac = ((band.gainDb.coerceIn(-12f, 12f) + 12f) / 24f)
                val thumbY = botY - (frac * trackLen)

                if (abs(thumbY - midY) > 1.dp.toPx()) {
                    drawLine(
                        color = effectiveAccent,
                        start = Offset(cx, midY),
                        end = Offset(cx, thumbY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Soft halo ring for selected band
                if (isSelected && isEnabled) {
                    drawCircle(
                        color = effectiveAccent.copy(alpha = 0.24f),
                        radius = 13.5.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                }

                // High-contrast thumb for both Light and Dark themes
                if (isSelected && isEnabled) {
                    drawCircle(
                        color = effectiveAccent,
                        radius = 8.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.2.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                } else {
                    drawCircle(
                        color = if (isEnabled) effectiveAccent else disabledGrey,
                        radius = 6.dp.toPx(),
                        center = Offset(cx, thumbY)
                    )
                }
            }
        }

        // 3. Frequency Label
        Text(
            text = freqText,
            color = when {
                !isEnabled -> disabledGrey
                isSelected -> accentColor
                else -> onSurfaceColor
            },
            fontSize = 12.sp,
            fontFamily = ModernSansFont,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )

        // 4. Phosphor Filter Curve Icon (15.dp)
        PeqFilterTypeIcon(
            filterType = band.filterType,
            color = if (isSelected) effectiveAccent else if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * UAPP-inspired compact parametric band editor:
 * - Clean header with Band #, Frequency, Acoustic Zone & 1-line description
 * - Full-width 3-Way Filter Type Segmented Bar (height = 42.dp) with Phosphor curve icons
 * - 3 precision rows with 40.dp x 40.dp Phosphor steppers:
 *   1) Gain stepper row (±0.1 dB per tap + Reset 0 dB)
 *   2) Frequency row (logarithmic slider 20 Hz..20 kHz + smart steppers)
 *   3) Q-Factor row (slider 0.3..5.0 + ±0.05 steppers)
 */
@Composable
private fun PeqBandFineTuneInspector(
    band: PeqBand,
    accentColor: Color,
    isEnabled: Boolean,
    onBandChange: (PeqBand) -> Unit
) {
    val onSurfaceColor = m3OnSurface()
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val effectiveAccent = if (isEnabled) accentColor else disabledGrey

    val filterTypesOrdered = remember {
        listOf(PeqFilterType.LOW_SHELF, PeqFilterType.PEAKING, PeqFilterType.HIGH_SHELF)
    }

    val logMinFreq = remember { log10(20.0) }
    val logMaxFreq = remember { log10(20000.0) }
    val freqLogFraction = remember(band.freqHz) {
        ((log10(band.freqHz.toDouble().coerceIn(20.0, 20000.0)) - logMinFreq) / (logMaxFreq - logMinFreq))
            .toFloat()
            .coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isEnabled) 1.0f else 0.50f }
            .clip(RoundedCornerShape(16.dp))
            .background(m3SurfaceContainerLow())
            .border(0.8.dp, effectiveAccent.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Selected Band Identity
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Band ${band.index + 1} • ${formatFullFreq(band.freqHz)} • ${band.zoneName}",
                color = if (isEnabled) onSurfaceColor else disabledGrey,
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (band.acousticDescription.isNotBlank()) {
                Text(
                    text = band.acousticDescription,
                    color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = ModernSansFont,
                    lineHeight = 15.sp
                )
            }
        }

        // Full-Width 3-Way Filter Type Segmented Selector (height = 42.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.45f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            filterTypesOrdered.forEach { type ->
                val isTypeActive = band.filterType == type
                val tint = when {
                    isTypeActive -> Color.White
                    isEnabled -> m3OnSurfaceVariant()
                    else -> disabledGrey
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isTypeActive) effectiveAccent else Color.Transparent)
                        .clickable(enabled = isEnabled) {
                            onBandChange(band.copy(filterType = type))
                        }
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PeqFilterTypeIcon(
                        filterType = type,
                        color = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = type.fullName,
                        color = tint,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = if (isTypeActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1. Gain Precision Row (±0.1 dB per tap — vertical fader handles fast drag)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PeqStepperButton(
                isPlus = false,
                enabled = isEnabled,
                accentColor = effectiveAccent,
                onClick = {
                    val next = (round((band.gainDb - 0.1f) * 10f) / 10f).coerceIn(-12.0f, 12.0f)
                    onBandChange(band.copy(gainDb = next))
                }
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 0.55f else 0.30f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "Gain: %+.1f dB", band.gainDb),
                    color = if (isEnabled) onSurfaceColor else disabledGrey,
                    fontSize = 12.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )

                val isNonZero = abs(band.gainDb) > 0.05f
                if (isNonZero && isEnabled) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(effectiveAccent.copy(alpha = 0.16f))
                            .clickable { onBandChange(band.copy(gainDb = 0.0f)) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GlyphRefresh(color = effectiveAccent, modifier = Modifier.size(12.dp))
                        Text(
                            text = "0 dB",
                            color = effectiveAccent,
                            fontSize = 11.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = "Flat",
                        color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont
                    )
                }
            }

            PeqStepperButton(
                isPlus = true,
                enabled = isEnabled,
                accentColor = effectiveAccent,
                onClick = {
                    val next = (round((band.gainDb + 0.1f) * 10f) / 10f).coerceIn(-12.0f, 12.0f)
                    onBandChange(band.copy(gainDb = next))
                }
            )
        }

        // 2. Frequency Row (Logarithmic Slider + 40.dp Steppers)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Frequency",
                    color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatFullFreq(band.freqHz),
                    color = if (isEnabled) onSurfaceColor else disabledGrey,
                    fontSize = 12.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeqStepperButton(
                    isPlus = false,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val step = smartFreqStep(band.freqHz, steppingUp = false)
                        onBandChange(band.copy(freqHz = (band.freqHz - step).coerceAtLeast(20f)))
                    }
                )

                Slider(
                    value = freqLogFraction,
                    enabled = isEnabled,
                    onValueChange = { frac ->
                        if (isEnabled) {
                            val rawFreq = 10.0.pow(logMinFreq + frac.toDouble() * (logMaxFreq - logMinFreq)).toFloat()
                            val snapped = snapFrequencyNice(rawFreq).coerceIn(20f, 20000f)
                            onBandChange(band.copy(freqHz = snapped))
                        }
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = effectiveAccent,
                        activeTrackColor = effectiveAccent,
                        inactiveTrackColor = m3SurfaceContainerHigh(),
                        disabledThumbColor = disabledGrey,
                        disabledActiveTrackColor = disabledGrey.copy(alpha = 0.6f),
                        disabledInactiveTrackColor = m3SurfaceContainerHigh().copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                )

                PeqStepperButton(
                    isPlus = true,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val step = smartFreqStep(band.freqHz, steppingUp = true)
                        onBandChange(band.copy(freqHz = (band.freqHz + step).coerceAtMost(20000f)))
                    }
                )
            }
        }

        // 3. Q-Factor Row (Bandwidth Control + 40.dp Steppers)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val qDesc = when {
                band.q <= 0.85f -> "Wide / Musical"
                band.q <= 1.65f -> "1-Octave Standard"
                else -> "Narrow / Surgical"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Q-Factor (Bandwidth)",
                    color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = String.format(Locale.US, "Q: %.2f • %s", band.q, qDesc),
                    color = if (isEnabled) onSurfaceColor else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeqStepperButton(
                    isPlus = false,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val next = (round((band.q - 0.05f) * 20f) / 20f).coerceIn(0.3f, 5.0f)
                        onBandChange(band.copy(q = next))
                    }
                )

                Slider(
                    value = band.q,
                    enabled = isEnabled,
                    onValueChange = { newQ ->
                        if (isEnabled) {
                            val stepped = (round(newQ * 20f) / 20f).coerceIn(0.3f, 5.0f)
                            onBandChange(band.copy(q = stepped))
                        }
                    },
                    valueRange = 0.3f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = effectiveAccent,
                        activeTrackColor = effectiveAccent,
                        inactiveTrackColor = m3SurfaceContainerHigh(),
                        disabledThumbColor = disabledGrey,
                        disabledActiveTrackColor = disabledGrey.copy(alpha = 0.6f),
                        disabledInactiveTrackColor = m3SurfaceContainerHigh().copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                )

                PeqStepperButton(
                    isPlus = true,
                    enabled = isEnabled,
                    accentColor = effectiveAccent,
                    onClick = {
                        val next = (round((band.q + 0.05f) * 20f) / 20f).coerceIn(0.3f, 5.0f)
                        onBandChange(band.copy(q = next))
                    }
                )
            }
        }
    }
}

/**
 * In-Sheet Interactive Guide Card (100% Phosphor Icons, Full English).
 */
@Composable
private fun PeqTutorialGuideCard(
    accentColor: Color,
    onClose: () -> Unit
) {
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
                GlyphQuestion(color = accentColor, modifier = Modifier.size(18.dp))
                Text(
                    text = "10-Band Parametric EQ Guide",
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

        PeqGuideSection(
            title = "1. Quick Start",
            accentColor = accentColor,
            body = "• Drag the 10 vertical faders up (+dB) or down (-dB) in 0.1 dB steps to shape your sound. Double-tap any fader to reset it to 0.0 dB.\n" +
                "• Select any band to adjust its filter type (Low Shelf / Peaking / High Shelf), center frequency, or Q-Factor in the editor below.\n" +
                "• Use Save to store or delete custom presets, or Export / Import to share preset codes with the community."
        )

        PeqGuideSection(
            title = "2. Parameter Glossary",
            accentColor = accentColor,
            body = "• Gain (dB): Boost (+) or cut (-) amount in 0.1 dB increments. Adjustments of ±1.5 to ±4.0 dB sound most natural.\n" +
                "• Q-Factor: Bandwidth of the filter curve. Q = 1.41 is the true 1-octave Constant-Q standard for clean instrument separation. Lower Q (<0.9) is wider; higher Q (>2.0) is narrower.\n" +
                "• Auto-Clip: Automatically maintains 100% linear headroom (zero clipping/IMD) whenever you boost frequencies."
        )

        PeqGuideSection(
            title = "3. 10-Band Acoustic Map (31 Hz – 16 kHz)",
            accentColor = accentColor,
            body = "• 31 Hz – 125 Hz (Bass): Sub-bass rumble, kick drum punch, and lower body warmth.\n" +
                "• 250 Hz – 1 kHz (Mids & Vocals): Instrument thickness (cut 250 Hz slightly to remove mud) and vocal intimacy (1 kHz).\n" +
                "• 2 kHz – 4 kHz (Clarity & Attack): Vocal articulation, string definition, and transient bite.\n" +
                "• 8 kHz – 16 kHz (Soundstage & Air): Cymbal sparkle, soundstage width, and fine micro-detail."
        )
    }
}

@Composable
private fun PeqGuideSection(
    title: String,
    accentColor: Color,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 11.5.sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            color = m3OnSurface(),
            fontSize = 10.5.sp,
            fontFamily = ModernSansFont,
            lineHeight = 15.sp
        )
    }
}

/**
 * Inline card for saving, updating, or safely deleting custom user PEQ presets.
 */
@Composable
private fun PeqSavePresetCard(
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
                val tint = if (presetName.isNotBlank()) Color.White else m3OnSurfaceVariant()
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

/**
 * Inline card for exporting and importing custom PEQ presets (100% Phosphor Icons, Full English).
 */
@Composable
private fun PeqExportImportCard(
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
                    GlyphClipboard(color = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Copy",
                        color = Color.White,
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
                    GlyphClipboard(color = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Paste Clipboard",
                        color = Color.White,
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
                    GlyphDownloadSimple(color = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import Typed Code",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatShortFreq(freqHz: Float): String {
    return if (freqHz >= 1000f) {
        val k = freqHz / 1000f
        if (abs(k - round(k)) < 0.05f) "${round(k).toInt()}k"
        else String.format(Locale.US, "%.1fk", k)
    } else {
        "${freqHz.toInt()}"
    }
}

private fun formatFullFreq(freqHz: Float): String {
    return if (freqHz >= 1000f) {
        val k = freqHz / 1000f
        if (abs(k - round(k)) < 0.01f) "${round(k).toInt()} kHz"
        else String.format(Locale.US, "%.2f kHz", k)
    } else {
        "${freqHz.toInt()} Hz"
    }
}

private fun smartFreqStep(freqHz: Float, steppingUp: Boolean): Float {
    val ref = if (steppingUp) freqHz else (freqHz - 1f)
    return when {
        ref < 100f -> 5f
        ref < 500f -> 10f
        ref < 1000f -> 25f
        ref < 5000f -> 100f
        else -> 250f
    }
}

private fun snapFrequencyNice(rawHz: Float): Float {
    return when {
        rawHz < 100f -> round(rawHz)
        rawHz < 1000f -> round(rawHz / 5f) * 5f
        rawHz < 5000f -> round(rawHz / 25f) * 25f
        else -> round(rawHz / 50f) * 50f
    }
}
