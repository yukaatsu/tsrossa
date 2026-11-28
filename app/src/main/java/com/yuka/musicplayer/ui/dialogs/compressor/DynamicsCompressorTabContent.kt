package com.yuka.musicplayer.ui.dialogs.compressor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.audio.compressor.CompressorConfig
import com.yuka.musicplayer.audio.compressor.CompressorEvaluator
import com.yuka.musicplayer.audio.compressor.CompressorPreset
import com.yuka.musicplayer.audio.compressor.CompressorPresets
import com.yuka.musicplayer.audio.compressor.CompressorUiHelper
import com.yuka.musicplayer.audio.compressor.SavedCustomCompressorProfile
import com.yuka.musicplayer.ui.components.GlyphBookmark
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphDelete
import com.yuka.musicplayer.ui.components.GlyphFloppyDisk
import com.yuka.musicplayer.ui.components.GlyphRefresh
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.util.ColorContrastHelper
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DynamicsCompressorTabContent(
    visible: Boolean,
    config: CompressorConfig,
    audioEngine: AudioEngine?,
    savedCustomProfile: SavedCustomCompressorProfile? = null,
    onConfigChange: (CompressorConfig) -> Unit,
    onSaveCustomProfile: (name: String) -> Unit = {},
    onDeleteCustomProfile: () -> Unit = {}
) {
    val accent = LocalAccentColor.current
    val onAccent = ColorContrastHelper.getOnAccentColor(accent)
    val isDark = LocalIsDarkTheme.current
    val onSurfaceColor = m3OnSurface()
    val onSurfaceVar = m3OnSurfaceVariant()
    val isEnabled = config.enabled
    val disabledGrey = onSurfaceVar.copy(alpha = 0.42f)
    val effectiveAccent = if (isEnabled) accent else disabledGrey
    val sectionAlpha = if (isEnabled) 1.0f else 0.50f
    val subtleBorder = onSurfaceColor.copy(alpha = if (isDark) 0.08f else 0.14f)

    var showAdvanced by remember { mutableStateOf(false) }
    var showSaveCustomCard by remember { mutableStateOf(false) }
    var customNameInput by remember(savedCustomProfile?.name) {
        mutableStateOf(savedCustomProfile?.name ?: "Custom")
    }
    var lastActiveHpfHz by remember {
        mutableFloatStateOf(CompressorUiHelper.resolveRestoredHpfHz(config.sidechainHpfHz))
    }

    LaunchedEffect(config.sidechainHpfHz) {
        if (CompressorUiHelper.isSidechainHpfEnabled(config.sidechainHpfHz)) {
            lastActiveHpfHz = CompressorUiHelper.resolveRestoredHpfHz(config.sidechainHpfHz)
        }
    }

    LaunchedEffect(isEnabled) {
        if (!isEnabled) {
            showSaveCustomCard = false
        }
    }

    val isCustomSelected = config.selectedPresetId == CompressorPresets.CUSTOM_ID
    val hasSavedCustom = savedCustomProfile != null
    val isCustomDirty = isCustomSelected && (
        savedCustomProfile == null || !config.matchesAcoustics(savedCustomProfile.config)
    )

    val curvePoints = remember(
        config.downThresholdDb,
        config.downRatio,
        config.upThresholdDb,
        config.upRatio,
        config.maxUpwardBoostDb,
        config.floorThresholdDb,
        config.kneeDb,
        config.makeupGainDb
    ) {
        CompressorEvaluator.evaluateTransferCurve(config)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Master Dynamics Status + Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(m3SurfaceContainerLow())
                .border(1.dp, subtleBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) "DUAL-SLOPE DYNAMICS ON" else "DYNAMICS BYPASSED",
                    color = if (isEnabled) accent else onSurfaceColor,
                    fontSize = 11.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Stereo-Linked • Downward + Floor-Gated Upward",
                    color = onSurfaceVar,
                    fontSize = 11.sp,
                    fontFamily = ModernSansFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    onConfigChange(config.copy(enabled = checked))
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

        // 3 Preset Cards + Live GR/Boost Meter Badge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = sectionAlpha },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DYNAMICS PRESETS",
                    color = onSurfaceVar,
                    fontSize = 10.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )

                CompressorLiveGainBadge(
                    visible = visible,
                    isEnabled = isEnabled,
                    audioEngine = audioEngine,
                    accent = accent,
                    disabledGrey = disabledGrey
                )
            }

            // 2x2 Symmetrical Preset Grid: [Night Mode | Low-Volume Detail] + [Balanced Dynamics | Custom]
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompressorPresetGridCard(
                        preset = CompressorPresets.NIGHT_MODE,
                        isSelected = config.selectedPresetId == CompressorPresets.NIGHT_MODE.id,
                        effectiveAccent = effectiveAccent,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVar = onSurfaceVar,
                        subtleBorder = subtleBorder,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onConfigChange(CompressorPresets.NIGHT_MODE.config.copy(enabled = true))
                        }
                    )
                    CompressorPresetGridCard(
                        preset = CompressorPresets.LOW_VOLUME_DETAIL,
                        isSelected = config.selectedPresetId == CompressorPresets.LOW_VOLUME_DETAIL.id,
                        effectiveAccent = effectiveAccent,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVar = onSurfaceVar,
                        subtleBorder = subtleBorder,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onConfigChange(CompressorPresets.LOW_VOLUME_DETAIL.config.copy(enabled = true))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompressorPresetGridCard(
                        preset = CompressorPresets.BALANCED_DYNAMICS,
                        isSelected = config.selectedPresetId == CompressorPresets.BALANCED_DYNAMICS.id,
                        effectiveAccent = effectiveAccent,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVar = onSurfaceVar,
                        subtleBorder = subtleBorder,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onConfigChange(CompressorPresets.BALANCED_DYNAMICS.config.copy(enabled = true))
                        }
                    )

                    val (badgeText, badgeColor) = when {
                        isCustomDirty -> "MODIFIED" to Color(0xFFFFB74D)
                        hasSavedCustom -> "SAVED" to Color(0xFF00E676)
                        else -> "PERSONAL" to onSurfaceVar
                    }
                    val customSubtitle = when {
                        isCustomDirty -> "Tap Save Custom to keep changes"
                        hasSavedCustom -> "Saved personal tuning"
                        else -> "Customize sliders & save privately"
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCustomSelected) effectiveAccent.copy(alpha = 0.16f)
                                else m3SurfaceContainerLow()
                            )
                            .border(
                                width = if (isCustomSelected) 1.5.dp else 1.dp,
                                color = if (isCustomSelected) effectiveAccent else subtleBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (savedCustomProfile != null) {
                                    onConfigChange(
                                        savedCustomProfile.config.copy(
                                            enabled = true,
                                            selectedPresetId = CompressorPresets.CUSTOM_ID
                                        )
                                    )
                                } else {
                                    onConfigChange(
                                        config.copy(
                                            enabled = true,
                                            selectedPresetId = CompressorPresets.CUSTOM_ID
                                        )
                                    )
                                    showSaveCustomCard = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            GlyphBookmark(
                                color = if (isCustomSelected) effectiveAccent else onSurfaceVar,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = savedCustomProfile?.name ?: "Custom",
                                color = if (isCustomSelected) effectiveAccent else onSurfaceColor,
                                fontSize = 12.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 8.5.sp,
                                fontFamily = TerminalFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = customSubtitle,
                            color = onSurfaceVar,
                            fontSize = 10.sp,
                            fontFamily = ModernSansFont,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Static Transfer Curve Canvas (-72 to 0 dBFS Input vs Output + Floor Gate Marker)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .graphicsLayer { alpha = sectionAlpha }
                .clip(RoundedCornerShape(14.dp))
                .background(m3SurfaceContainerLow())
                .border(1.dp, subtleBorder, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            val refDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }
            val gateDash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }
            val gridDbs = remember { floatArrayOf(-60f, -48f, -36f, -24f, -12f) }
            val curveStroke = remember { Stroke(width = 3.2f, cap = StrokeCap.Round) }

            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val minDb = -72f
                        val maxDb = 0f
                        val span = maxDb - minDb
                        fun xOf(db: Float): Float = ((db - minDb) / span).coerceIn(0f, 1f) * size.width
                        fun yOf(db: Float): Float = (1f - ((db - minDb) / span).coerceIn(0f, 1f)) * size.height

                        val gridColor = onSurfaceColor.copy(alpha = 0.08f)
                        val refColor = onSurfaceColor.copy(alpha = 0.25f)
                        val gateColor = Color(0xFFFFB74D).copy(alpha = 0.55f)
                        val floorX = xOf(config.floorThresholdDb)
                        val downThreshPt = Offset(xOf(config.downThresholdDb), yOf(config.downThresholdDb))
                        val upThreshPt = Offset(xOf(config.upThresholdDb), yOf(config.upThresholdDb))
                        val transferPath = if (curvePoints.isNotEmpty()) {
                            Path().apply {
                                curvePoints.forEachIndexed { idx, (inDb, outDb) ->
                                    val px = xOf(inDb)
                                    val py = yOf(outDb)
                                    if (idx == 0) moveTo(px, py) else lineTo(px, py)
                                }
                            }
                        } else {
                            null
                        }

                        onDrawBehind {
                            for (db in gridDbs) {
                                val x = xOf(db)
                                val y = yOf(db)
                                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            }

                            // 1:1 Reference line
                            drawLine(
                                color = refColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.5f,
                                pathEffect = refDash
                            )

                            // Noise-floor gate vertical dashed boundary
                            drawLine(
                                color = gateColor,
                                start = Offset(floorX, 0f),
                                end = Offset(floorX, size.height),
                                strokeWidth = 1.5f,
                                pathEffect = gateDash
                            )

                            // Transfer curve path
                            if (transferPath != null) {
                                drawPath(
                                    path = transferPath,
                                    color = effectiveAccent,
                                    style = curveStroke
                                )
                            }

                            // Threshold markers
                            drawCircle(color = Color(0xFFFF7A59), radius = 4.5f, center = downThreshPt)
                            drawCircle(color = Color(0xFF00E676), radius = 4.5f, center = upThreshPt)
                        }
                    }
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TRANSFER CURVE (-72..0 dBFS)",
                    color = onSurfaceVar,
                    fontSize = 9.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "GATE ${config.floorThresholdDb.toInt()} dBFS",
                    color = Color(0xFFFFB74D),
                    fontSize = 9.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val effectiveUpMax = CompressorUiHelper.effectiveUpThresholdMax(config.downThresholdDb)
        val effectiveFloorMax = CompressorUiHelper.effectiveFloorThresholdMax(config.upThresholdDb)

        // Primary Dynamics Sliders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = sectionAlpha }
                .clip(RoundedCornerShape(14.dp))
                .background(m3SurfaceContainerLow())
                .border(1.dp, subtleBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompressorParamSlider(
                label = "Peak Control Threshold",
                valueText = String.format(Locale.US, "%.1f dBFS (%.1f:1)", config.downThresholdDb, config.downRatio),
                value = config.downThresholdDb,
                valueRange = CompressorUiHelper.MIN_DOWN_THRESHOLD_DB..CompressorUiHelper.MAX_DOWN_THRESHOLD_DB,
                helperText = if (config.downThresholdDb <= config.upThresholdDb + 0.5f) {
                    "Pulling lower also lowers Upward & Gate to keep threshold order"
                } else {
                    null
                },
                enabled = isEnabled,
                accent = effectiveAccent,
                onValueChange = { newVal ->
                    onConfigChange(config.copy(downThresholdDb = newVal, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped())
                }
            )
            CompressorParamSlider(
                label = "Upward Detail Max Boost",
                valueText = String.format(Locale.US, "+%.1f dB", config.maxUpwardBoostDb),
                value = config.maxUpwardBoostDb,
                valueRange = 0f..12f,
                enabled = isEnabled,
                accent = effectiveAccent,
                onValueChange = { newVal ->
                    onConfigChange(config.copy(maxUpwardBoostDb = newVal, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped())
                }
            )
            CompressorParamSlider(
                label = "Output Makeup Gain",
                valueText = String.format(Locale.US, "%+.1f dB", config.makeupGainDb),
                value = config.makeupGainDb,
                valueRange = -6f..12f,
                enabled = isEnabled,
                accent = effectiveAccent,
                onValueChange = { newVal ->
                    onConfigChange(config.copy(makeupGainDb = newVal, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped())
                }
            )
        }

        // Custom Profile Save / Reset Balanced Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = sectionAlpha },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val saveChipActive = showSaveCustomCard || isCustomDirty
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (saveChipActive && isEnabled) effectiveAccent.copy(alpha = 0.16f)
                        else m3SurfaceContainerLow()
                    )
                    .border(
                        width = if (saveChipActive && isEnabled) 1.3.dp else 1.dp,
                        color = if (saveChipActive && isEnabled) effectiveAccent else subtleBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = isEnabled) { showSaveCustomCard = !showSaveCustomCard }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlyphFloppyDisk(
                    color = if (saveChipActive && isEnabled) effectiveAccent else onSurfaceColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (showSaveCustomCard) "Close Save" else "Save Custom",
                    color = if (saveChipActive && isEnabled) effectiveAccent else onSurfaceColor,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(m3SurfaceContainerLow())
                    .border(1.dp, subtleBorder, RoundedCornerShape(10.dp))
                    .clickable(enabled = isEnabled) {
                        onConfigChange(CompressorPresets.BALANCED_DYNAMICS.config.copy(enabled = config.enabled))
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlyphRefresh(color = effectiveAccent, modifier = Modifier.size(14.dp))
                Text(
                    text = "Reset Balanced",
                    color = onSurfaceColor,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        AnimatedVisibility(
            visible = showSaveCustomCard && isEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CompressorCustomSaveCard(
                accentColor = effectiveAccent,
                onAccentColor = onAccent,
                customName = customNameInput,
                hasSavedCustom = hasSavedCustom,
                isCustomDirty = isCustomDirty,
                onNameChange = { customNameInput = it },
                onSave = {
                    val targetName = customNameInput.trim().ifBlank { "Custom" }
                    onSaveCustomProfile(targetName)
                    showSaveCustomCard = false
                },
                onRevertSaved = {
                    if (savedCustomProfile != null) {
                        onConfigChange(
                            savedCustomProfile.config.copy(
                                enabled = config.enabled,
                                selectedPresetId = CompressorPresets.CUSTOM_ID
                            )
                        )
                        customNameInput = savedCustomProfile.name
                        showSaveCustomCard = false
                    }
                },
                onClearSaved = {
                    onDeleteCustomProfile()
                    customNameInput = "Custom"
                    showSaveCustomCard = false
                },
                onClose = { showSaveCustomCard = false }
            )
        }

        // Advanced Timing & Sidechain Toggle Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = sectionAlpha }
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerLow())
                .border(1.dp, subtleBorder, RoundedCornerShape(10.dp))
                .clickable(enabled = isEnabled) { showAdvanced = !showAdvanced }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = if (showAdvanced) "Hide Advanced Parameters" else "Advanced Timing & Sidechain",
                color = onSurfaceColor,
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(
            visible = showAdvanced && isEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(m3SurfaceContainerLow())
                    .border(1.dp, subtleBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompressorParamSlider(
                    label = "Downward Ratio",
                    valueText = String.format(Locale.US, "%.1f : 1", config.downRatio),
                    value = config.downRatio,
                    valueRange = 1f..20f,
                    enabled = isEnabled,
                    accent = effectiveAccent,
                    onValueChange = { onConfigChange(config.copy(downRatio = it, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped()) }
                )
                CompressorParamSlider(
                    label = "Upward Threshold",
                    valueText = String.format(Locale.US, "%.1f dBFS (%.1f:1)", config.upThresholdDb, config.upRatio),
                    value = config.upThresholdDb,
                    valueRange = CompressorUiHelper.MIN_UP_THRESHOLD_DB..effectiveUpMax,
                    helperText = String.format(Locale.US, "Effective range: -54 .. %.1f dBFS (≤ Peak Threshold)", effectiveUpMax),
                    enabled = isEnabled,
                    accent = effectiveAccent,
                    onValueChange = { onConfigChange(config.copy(upThresholdDb = it, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped()) }
                )
                CompressorParamSlider(
                    label = "Upward Ratio",
                    valueText = String.format(Locale.US, "%.1f : 1", config.upRatio),
                    value = config.upRatio,
                    valueRange = 1f..10f,
                    enabled = isEnabled,
                    accent = effectiveAccent,
                    onValueChange = { onConfigChange(config.copy(upRatio = it, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped()) }
                )
                CompressorParamSlider(
                    label = "Noise-Floor Gate",
                    valueText = String.format(Locale.US, "%.0f dBFS", config.floorThresholdDb),
                    value = config.floorThresholdDb,
                    valueRange = CompressorUiHelper.MIN_FLOOR_THRESHOLD_DB..effectiveFloorMax,
                    helperText = String.format(Locale.US, "Effective range: -80 .. %.0f dBFS (≤ Upward − 6 dB)", effectiveFloorMax),
                    enabled = isEnabled,
                    accent = effectiveAccent,
                    onValueChange = { onConfigChange(config.copy(floorThresholdDb = it, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped()) }
                )
                CompressorParamSlider(
                    label = "Soft Knee",
                    valueText = String.format(Locale.US, "%.1f dB", config.kneeDb),
                    value = config.kneeDb,
                    valueRange = 0f..12f,
                    enabled = isEnabled,
                    accent = effectiveAccent,
                    onValueChange = { onConfigChange(config.copy(kneeDb = it, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped()) }
                )
                CompressorParamSlider(
                    label = "Attack",
                    valueText = String.format(Locale.US, "%.1f ms", config.attackMs),
                    value = config.attackMs,
                    valueRange = 0.5f..100f,
                    enabled = isEnabled,
                    accent = effectiveAccent,
                    onValueChange = { onConfigChange(config.copy(attackMs = it, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped()) }
                )
                CompressorParamSlider(
                    label = "Release",
                    valueText = String.format(Locale.US, "%.0f ms", config.releaseMs),
                    value = config.releaseMs,
                    valueRange = 20f..1000f,
                    enabled = isEnabled,
                    accent = effectiveAccent,
                    onValueChange = { onConfigChange(config.copy(releaseMs = it, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped()) }
                )
                val isHpfOn = CompressorUiHelper.isSidechainHpfEnabled(config.sidechainHpfHz)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sidechain HPF (Bass Shield)",
                                color = m3OnSurface(),
                                fontSize = 11.5.sp,
                                fontFamily = ModernSansFont,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isHpfOn) {
                                    "Shields deep sub-bass (${config.sidechainHpfHz.toInt()} Hz, 20–250 Hz)"
                                } else {
                                    "Disabled (full-band detector)"
                                },
                                color = m3OnSurfaceVariant(),
                                fontSize = 9.5.sp,
                                fontFamily = ModernSansFont
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isHpfOn) String.format(Locale.US, "%.0f Hz", config.sidechainHpfHz) else "OFF",
                                color = if (isHpfOn) effectiveAccent else onSurfaceVar,
                                fontSize = 11.sp,
                                fontFamily = TerminalFont,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isHpfOn) effectiveAccent else m3SurfaceContainerHigh())
                                    .clickable(enabled = isEnabled) {
                                        onConfigChange(
                                            CompressorUiHelper.toggleSidechainHpf(
                                                config = config,
                                                enable = !isHpfOn,
                                                lastActiveHpfHz = lastActiveHpfHz
                                            )
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isHpfOn) "ON" else "OFF",
                                    color = if (isHpfOn) onAccent else onSurfaceColor,
                                    fontSize = 10.5.sp,
                                    fontFamily = TerminalFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isHpfOn && isEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Slider(
                            value = config.sidechainHpfHz.coerceIn(
                                CompressorUiHelper.MIN_SIDECHAIN_HPF_HZ,
                                CompressorUiHelper.MAX_SIDECHAIN_HPF_HZ
                            ),
                            onValueChange = { newHz ->
                                lastActiveHpfHz = newHz
                                onConfigChange(
                                    config.copy(
                                        sidechainHpfHz = newHz,
                                        selectedPresetId = CompressorPresets.CUSTOM_ID
                                    ).clamped()
                                )
                            },
                            valueRange = CompressorUiHelper.MIN_SIDECHAIN_HPF_HZ..CompressorUiHelper.MAX_SIDECHAIN_HPF_HZ,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = effectiveAccent,
                                activeTrackColor = effectiveAccent,
                                inactiveTrackColor = m3SurfaceContainerHigh()
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detector Mode",
                        color = onSurfaceColor,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(true to "RMS (Warm)", false to "Peak (Fast)").forEach { (isRms, label) ->
                            val selected = config.useRmsDetector == isRms
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) effectiveAccent else m3SurfaceContainerHigh())
                                    .clickable(enabled = isEnabled) {
                                        onConfigChange(config.copy(useRmsDetector = isRms, selectedPresetId = CompressorPresets.CUSTOM_ID).clamped())
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) onAccent else onSurfaceColor,
                                    fontSize = 10.5.sp,
                                    fontFamily = TerminalFont,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CompressorParamSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    helperText: String? = null,
    enabled: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = m3OnSurface(),
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                color = accent,
                fontSize = 11.sp,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold
            )
        }
        if (helperText != null) {
            Text(
                text = helperText,
                color = m3OnSurfaceVariant(),
                fontSize = 9.5.sp,
                fontFamily = ModernSansFont
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = m3SurfaceContainerHigh()
            )
        )
    }
}

@Composable
private fun CompressorPresetGridCard(
    preset: CompressorPreset,
    isSelected: Boolean,
    effectiveAccent: Color,
    onSurfaceColor: Color,
    onSurfaceVar: Color,
    subtleBorder: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) effectiveAccent.copy(alpha = 0.16f)
                else m3SurfaceContainerLow()
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) effectiveAccent else subtleBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = preset.name,
            color = if (isSelected) effectiveAccent else onSurfaceColor,
            fontSize = 12.sp,
            fontFamily = ModernSansFont,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = preset.subtitle,
            color = onSurfaceVar,
            fontSize = 10.sp,
            fontFamily = ModernSansFont,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompressorCustomSaveCard(
    accentColor: Color,
    onAccentColor: Color,
    customName: String,
    hasSavedCustom: Boolean,
    isCustomDirty: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onRevertSaved: () -> Unit,
    onClearSaved: () -> Unit,
    onClose: () -> Unit
) {
    val deleteRed = Color(0xFFFF6B6B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(m3SurfaceContainerLow())
            .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                GlyphFloppyDisk(color = accentColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "Save Personal Custom Profile",
                    color = m3OnSurface(),
                    fontSize = 13.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GlyphClose(color = accentColor, modifier = Modifier.size(12.dp))
                Text(
                    text = "Close",
                    color = accentColor,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh())
                .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (customName.isEmpty()) {
                Text(
                    text = "Custom",
                    color = m3OnSurfaceVariant().copy(alpha = 0.6f),
                    fontSize = 12.5.sp,
                    fontFamily = ModernSansFont
                )
            }
            BasicTextField(
                value = customName,
                onValueChange = { newText ->
                    if (newText.length <= 28) onNameChange(newText)
                },
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor)
                .clickable(onClick = onSave)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GlyphFloppyDisk(color = onAccentColor, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Save Profile",
                color = onAccentColor,
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
        }

        if (hasSavedCustom) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCustomDirty) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(m3SurfaceContainerHigh())
                            .border(1.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onRevertSaved)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GlyphRefresh(color = accentColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Revert Saved",
                            color = accentColor,
                            fontSize = 11.5.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(deleteRed.copy(alpha = 0.12f))
                        .border(0.9.dp, deleteRed.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onClearSaved)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GlyphDelete(color = deleteRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Clear Saved",
                        color = deleteRed,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Isolated live gain reduction / boost meter scope so 10 Hz JNI polling
 * never recomposes preset cards, Transfer Curve Canvas, or parameter sliders.
 */
@Composable
private fun CompressorLiveGainBadge(
    visible: Boolean,
    isEnabled: Boolean,
    audioEngine: AudioEngine?,
    accent: Color,
    disabledGrey: Color
) {
    var quantizedGainDb by remember { mutableFloatStateOf(0.0f) }

    LaunchedEffect(visible, isEnabled, audioEngine) {
        if (!visible || !isEnabled || audioEngine == null) {
            quantizedGainDb = 0.0f
            return@LaunchedEffect
        }
        while (true) {
            val raw = try {
                audioEngine.getCompressorGainDb()
            } catch (_: UnsatisfiedLinkError) {
                0.0f
            }
            val quantized = kotlin.math.round(raw * 10f) / 10f
            if (quantized != quantizedGainDb) {
                quantizedGainDb = quantized
            }
            delay(100L)
        }
    }

    val (meterColor, meterText) = when {
        !isEnabled -> disabledGrey to "BYPASS 0.0 dB"
        quantizedGainDb < -0.15f -> Color(0xFFFF7A59) to String.format(Locale.US, "GR %.1f dB", quantizedGainDb)
        quantizedGainDb > 0.15f -> Color(0xFF00E676) to String.format(Locale.US, "BOOST +%.1f dB", quantizedGainDb)
        else -> accent to "UNITY 0.0 dB"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(meterColor.copy(alpha = 0.16f))
            .border(1.dp, meterColor.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = meterText,
            color = meterColor,
            fontSize = 10.5.sp,
            fontFamily = TerminalFont,
            fontWeight = FontWeight.Bold
        )
    }
}
