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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
        maxHeightFraction = 0.92f,
        maxWidth = 480.dp
    ) {
        val accent = LocalAccentColor.current
        val isEqEnabled = config.enabled
        val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
        val effectiveAccent = if (isEqEnabled) accent else disabledGrey
        val sectionAlpha = if (isEqEnabled) 1.0f else 0.50f

        var selectedBandIndex by remember { mutableIntStateOf(5) } // Default: 1 kHz vocal core
        var showGuideCard by remember { mutableStateOf(false) }
        var showSavePresetCard by remember { mutableStateOf(false) }
        var showExportImportCard by remember { mutableStateOf(false) }
        var presetNameInput by remember { mutableStateOf("") }

        // Tutup kartu panduan, simpan, dan export/import secara otomatis bila PEQ dimatikan
        LaunchedEffect(isEqEnabled) {
            if (!isEqEnabled) {
                showGuideCard = false
                showSavePresetCard = false
                showExportImportCard = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header & Master Bypass Toggle
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
                            color = m3OnSurface(),
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

            // 2. Preset Selector Chips Row + Panduan + Simpan Preset + Export/Import + Reset Flat (#NOTE 1: Tanpa preset bawaan)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = sectionAlpha }
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Guide Toggle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showGuideCard) accent else m3SurfaceContainerHigh())
                        .clickable(enabled = isEqEnabled) {
                            showGuideCard = !showGuideCard
                            if (showGuideCard) {
                                showSavePresetCard = false
                                showExportImportCard = false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (showGuideCard) "✕ Tutup Panduan" else "💡 Panduan EQ",
                        color = if (showGuideCard) Color.White else effectiveAccent,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Save Preset Toggle Button (#NOTE 2)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showSavePresetCard) effectiveAccent else m3SurfaceContainerHigh())
                        .clickable(enabled = isEqEnabled) {
                            showSavePresetCard = !showSavePresetCard
                            if (showSavePresetCard) {
                                showGuideCard = false
                                showExportImportCard = false
                                val currentPreset = userPresets.find { it.id == config.selectedPresetId }
                                if (currentPreset != null) {
                                    presetNameInput = currentPreset.name
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (showSavePresetCard) "✕ Batal Simpan" else "💾 Simpan Preset",
                        color = if (showSavePresetCard) Color.White else effectiveAccent,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Export / Import Preset Button (#NOTE 1)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showExportImportCard) effectiveAccent else m3SurfaceContainerHigh())
                        .clickable(enabled = isEqEnabled) {
                            showExportImportCard = !showExportImportCard
                            if (showExportImportCard) {
                                showGuideCard = false
                                showSavePresetCard = false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (showExportImportCard) "✕ Tutup Bagikan" else "⇄ Export / Import",
                        color = if (showExportImportCard) Color.White else effectiveAccent,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Reset Flat Action Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(m3SurfaceContainerLow())
                        .border(
                            1.dp,
                            Color.White.copy(alpha = if (isEqEnabled) 0.12f else 0.05f),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = isEqEnabled) {
                            val flatBands = PeqConfiguration.createDefaultBands()
                            onConfigChange(
                                config.copy(
                                    selectedPresetId = "flat",
                                    preampDb = 0.0f,
                                    bands = flatBands
                                )
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Reset Flat",
                        color = if (isEqEnabled) m3OnSurfaceVariant() else disabledGrey,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }

                // User Saved Presets Chips (#NOTE 2)
                userPresets.forEach { userPreset ->
                    val isSelected = config.selectedPresetId == userPreset.id
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) {
                                    if (isEqEnabled) accent.copy(alpha = 0.22f)
                                    else disabledGrey.copy(alpha = 0.15f)
                                } else m3SurfaceContainerLow()
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) effectiveAccent else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
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
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "★ ${userPreset.name}",
                            color = if (isSelected) effectiveAccent else if (isEqEnabled) m3OnSurface() else disabledGrey,
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        if (isSelected && isEqEnabled) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(m3SurfaceContainerHigh())
                                    .clickable {
                                        onDeleteUserPreset(userPreset.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    color = m3OnSurfaceVariant(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 3. Expandable Save Preset Card (#NOTE 2)
            AnimatedVisibility(
                visible = showSavePresetCard && isEqEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val activeUserPreset = userPresets.find { it.id == config.selectedPresetId }
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
                    onClose = { showSavePresetCard = false }
                )
            }

            // 3b. Expandable Export / Import Preset Card (#NOTE 1)
            AnimatedVisibility(
                visible = showExportImportCard && isEqEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val activeUserPreset = userPresets.find { it.id == config.selectedPresetId }
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

            // 4. Expandable In-Sheet Tutorial Guide Card
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

            // 4. Interactive Frequency Response Graph Canvas
            PeqFrequencyResponseGraph(
                bands = config.bands,
                preampDb = config.effectivePreampDb,
                accentColor = accent,
                isEnabled = isEqEnabled,
                selectedBandIndex = selectedBandIndex,
                onSelectBand = { selectedBandIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(m3SurfaceContainerLow())
                    .border(
                        0.8.dp,
                        if (isEqEnabled) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
            )

            // 5. Preamp & Auto-Preamp Anti-Clipping Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = sectionAlpha }
                    .clip(RoundedCornerShape(14.dp))
                    .background(m3SurfaceContainerLow())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                            color = if (isEqEnabled) m3OnSurface() else disabledGrey,
                            fontSize = 12.5.sp,
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (config.autoPreampEnabled && maxBoost > 0.05f) {
                            Text(
                                text = "(Auto-Clip: ${String.format(Locale.US, "%+.1f", displayPreamp)} dB)",
                                color = effectiveAccent,
                                fontSize = 10.sp,
                                fontFamily = TerminalFont
                            )
                        }
                    }

                    if (!config.autoPreampEnabled) {
                        Slider(
                            value = config.preampDb,
                            enabled = isEqEnabled,
                            onValueChange = { newPre ->
                                if (isEqEnabled) {
                                    onConfigChange(
                                        config.copy(
                                            preampDb = (round(newPre * 2f) / 2f),
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
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Auto-Preamp Toggle Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (config.autoPreampEnabled) effectiveAccent.copy(alpha = 0.18f)
                            else m3SurfaceContainerHigh()
                        )
                        .clickable(enabled = isEqEnabled) {
                            onConfigChange(
                                config.copy(
                                    autoPreampEnabled = !config.autoPreampEnabled
                                )
                            )
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (config.autoPreampEnabled) "Auto-Clip ON" else "Manual",
                        color = if (config.autoPreampEnabled) effectiveAccent else if (isEqEnabled) m3OnSurfaceVariant() else disabledGrey,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 6. 10-Band Horizontal Faders Rack
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = sectionAlpha },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "10-Band Sliders (Geser fader / ketuk 2x reset 0 dB)",
                    color = if (isEqEnabled) m3OnSurfaceVariant() else disabledGrey,
                    fontSize = 11.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = sectionAlpha }
                    .clip(RoundedCornerShape(16.dp))
                    .background(m3SurfaceContainerLow())
                    .padding(vertical = 12.dp, horizontal = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            // 7. Selected Band Fine-Tune Inspector
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

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

/**
 * 60 FPS Logarithmic Frequency Response Graph with Canvas curve and interactive touch node selector.
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
    val samplePoints = remember {
        // Logarithmic frequency distribution from 20 Hz to 20,000 Hz (96 points)
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
                if (minDiff < 45.dp.toPx()) {
                    onSelectBand(nearestIdx)
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

        // Draw horizontal grid lines (+12, +6, 0, -6, -12 dB)
        val gridDbs = floatArrayOf(12f, 6f, 0f, -6f, -12f)
        for (gDb in gridDbs) {
            val y = dbToY(gDb)
            val isZero = gDb == 0f
            drawLine(
                color = if (isZero) Color.White.copy(alpha = if (isEnabled) 0.22f else 0.10f) else Color.White.copy(alpha = if (isEnabled) 0.07f else 0.03f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (isZero) 1.2f else 0.8f
            )
        }

        // Draw vertical frequency grid lines (31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k)
        val gridFreqs = doubleArrayOf(31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        for (gFreq in gridFreqs) {
            val x = freqToX(gFreq)
            drawLine(
                color = Color.White.copy(alpha = if (isEnabled) 0.08f else 0.03f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 0.8f
            )
        }

        val curveColor = if (isEnabled) accentColor else Color.Gray.copy(alpha = 0.55f)

        // Construct composite frequency response curve Path
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

        // Draw gradient fill under curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    curveColor.copy(alpha = if (isEnabled) 0.25f else 0.08f),
                    curveColor.copy(alpha = 0.01f)
                ),
                startY = 0f,
                endY = h
            ),
            style = Fill
        )

        // Draw curve outline
        drawPath(
            path = curvePath,
            color = curveColor,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw dots for each of the 10 bands
        val nodeColor = if (isEnabled) accentColor else Color.Gray.copy(alpha = 0.60f)
        bands.forEachIndexed { idx, band ->
            val bx = freqToX(band.freqHz.toDouble())
            val by = dbToY(band.gainDb + preampDb)
            val isSelected = selectedBandIndex == idx

            if (isSelected) {
                drawCircle(
                    color = nodeColor.copy(alpha = 0.35f),
                    radius = 8.dp.toPx(),
                    center = Offset(bx, by)
                )
            }

            drawCircle(
                color = if (isSelected) (if (isEnabled) Color.White else Color.LightGray) else nodeColor,
                radius = if (isSelected) 4.5.dp.toPx() else 3.0.dp.toPx(),
                center = Offset(bx, by)
            )
        }
    }
}

/**
 * Interactive vertical EQ band fader with pointer touch/drag gesture, double-tap reset,
 * gain text, tactile steppers, frequency label, acoustic role badge, and filter type tag.
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
    val freqText = when {
        band.freqHz >= 1000f -> "${(band.freqHz / 1000f).toInt().takeIf { band.freqHz % 1000 == 0f } ?: String.format(Locale.US, "%.1f", band.freqHz / 1000f)}k"
        else -> "${band.freqHz.toInt()}"
    }

    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val effectiveAccent = if (isEnabled) accentColor else disabledGrey

    var sliderHeightPx by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier
            .width(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) effectiveAccent.copy(alpha = if (isEnabled) 0.14f else 0.08f) else Color.Transparent)
            .clickable(enabled = isEnabled) { onSelect() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Gain text
        val gainStr = String.format(Locale.US, "%+.1f", band.gainDb)
        Text(
            text = gainStr,
            color = if (!isEnabled) disabledGrey else if (isSelected) accentColor else if (abs(band.gainDb) > 0.05f) m3OnSurface() else m3OnSurfaceVariant(),
            fontSize = 9.5.sp,
            fontFamily = TerminalFont,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        // Vertical Slider Canvas with Pointer Touch/Drag and Double-Tap Reset
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(112.dp)
                .onGloballyPositioned { coords ->
                    sliderHeightPx = coords.size.height.toFloat()
                }
                .pointerInput(band.index, isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            onGainChange(0.0f)
                        },
                        onTap = { offset ->
                            val topPad = 6.dp.toPx()
                            val botPad = 6.dp.toPx()
                            val trackLen = sliderHeightPx - topPad - botPad
                            if (trackLen > 0f) {
                                val clampedY = offset.y.coerceIn(topPad, sliderHeightPx - botPad)
                                val frac = (sliderHeightPx - botPad - clampedY) / trackLen
                                val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)
                                val stepped = (round(newGain.toDouble() * 2.0) / 2.0).toFloat()
                                onGainChange(stepped)
                            }
                        }
                    )
                }
                .pointerInput(band.index, isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val topPad = 6.dp.toPx()
                            val botPad = 6.dp.toPx()
                            val trackLen = sliderHeightPx - topPad - botPad
                            if (trackLen > 0f) {
                                val clampedY = change.position.y.coerceIn(topPad, sliderHeightPx - botPad)
                                val frac = (sliderHeightPx - botPad - clampedY) / trackLen
                                val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)
                                val stepped = (round(newGain.toDouble() * 2.0) / 2.0).toFloat()
                                onGainChange(stepped)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val topY = 6.dp.toPx()
                val botY = size.height - 6.dp.toPx()
                val trackLen = botY - topY
                val midY = topY + trackLen / 2f

                // Inactive rail
                drawLine(
                    color = Color.White.copy(alpha = if (isEnabled) 0.12f else 0.06f),
                    start = Offset(cx, topY),
                    end = Offset(cx, botY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Zero line center tick
                drawLine(
                    color = Color.White.copy(alpha = if (isEnabled) 0.35f else 0.15f),
                    start = Offset(cx - 5.dp.toPx(), midY),
                    end = Offset(cx + 5.dp.toPx(), midY),
                    strokeWidth = 1.dp.toPx()
                )

                // Active fill from midY to thumbY
                val frac = ((band.gainDb.coerceIn(-12f, 12f) + 12f) / 24f)
                val thumbY = botY - (frac * trackLen)

                if (abs(thumbY - midY) > 1.dp.toPx()) {
                    drawLine(
                        color = effectiveAccent,
                        start = Offset(cx, midY),
                        end = Offset(cx, thumbY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Thumb circle
                drawCircle(
                    color = if (isSelected) (if (isEnabled) Color.White else disabledGrey) else effectiveAccent,
                    radius = if (isSelected) 6.dp.toPx() else 4.5.dp.toPx(),
                    center = Offset(cx, thumbY)
                )
            }
        }

        // Stepper buttons - / +
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TactileBox(
                onClick = { if (isEnabled) onGainChange((band.gainDb - 0.5f).coerceIn(-12.0f, 12.0f)) },
                enabled = isEnabled,
                pressedScale = 0.85f,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f))
            ) {
                Text(
                    text = "-",
                    color = if (isEnabled) m3OnSurface() else disabledGrey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TactileBox(
                onClick = { if (isEnabled) onGainChange((band.gainDb + 0.5f).coerceIn(-12.0f, 12.0f)) },
                enabled = isEnabled,
                pressedScale = 0.85f,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f))
            ) {
                Text(
                    text = "+",
                    color = if (isEnabled) m3OnSurface() else disabledGrey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Frequency Label
        Text(
            text = freqText,
            color = if (!isEnabled) disabledGrey else if (isSelected) accentColor else m3OnSurface(),
            fontSize = 11.sp,
            fontFamily = ModernSansFont,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )

        // Acoustic Role Badge (SUB, BASS, WARM, BODY, MID, VOCAL, CLEAR, PRES, BRIL, AIR)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (isSelected) effectiveAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = if (isEnabled) 0.08f else 0.04f))
                .padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
            Text(
                text = band.shortLabel,
                color = if (isSelected) effectiveAccent else if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                fontSize = 7.5.sp,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold
            )
        }

        // Type Tag (LS / PK / HS)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isSelected) effectiveAccent.copy(alpha = 0.22f)
                    else m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f)
                )
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = band.filterType.label,
                color = if (isSelected) effectiveAccent else if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                fontSize = 8.5.sp,
                fontFamily = TerminalFont,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Detailed parameter inspector for the selected band with horizontal sliders for Gain & Q-Factor,
 * acoustic role zone description, frequency steppers, and filter type selector.
 */
@Composable
private fun PeqBandFineTuneInspector(
    band: PeqBand,
    accentColor: Color,
    isEnabled: Boolean,
    onBandChange: (PeqBand) -> Unit
) {
    val disabledGrey = m3OnSurfaceVariant().copy(alpha = 0.42f)
    val effectiveAccent = if (isEnabled) accentColor else disabledGrey

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isEnabled) 1.0f else 0.50f }
            .clip(RoundedCornerShape(16.dp))
            .background(m3SurfaceContainerLow())
            .border(0.8.dp, effectiveAccent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: Band Index, Zone Name & Type Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Band ${band.index + 1} • ${band.zoneName}",
                    color = if (isEnabled) m3OnSurface() else disabledGrey,
                    fontSize = 13.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold
                )
                if (band.acousticDescription.isNotBlank()) {
                    Text(
                        text = band.acousticDescription,
                        color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey.copy(alpha = 0.7f),
                        fontSize = 10.5.sp,
                        fontFamily = ModernSansFont,
                        lineHeight = 14.sp
                    )
                }
            }

            // Filter Type Segmented Control
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PeqFilterType.values().forEach { type ->
                    val isTypeActive = band.filterType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTypeActive) effectiveAccent else Color.Transparent)
                            .clickable(enabled = isEnabled) { onBandChange(band.copy(filterType = type)) }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = type.label,
                            color = if (isTypeActive) Color.White else if (isEnabled) m3OnSurfaceVariant() else disabledGrey,
                            fontSize = 10.5.sp,
                            fontFamily = TerminalFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 1. Gain Slider
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "Gain: %+.1f dB", band.gainDb),
                    color = if (isEnabled) m3OnSurface() else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ketuk 2x slider untuk reset 0 dB",
                    color = if (isEnabled) m3OnSurfaceVariant() else disabledGrey.copy(alpha = 0.6f),
                    fontSize = 9.5.sp,
                    fontFamily = ModernSansFont
                )
            }
            Slider(
                value = band.gainDb,
                enabled = isEnabled,
                onValueChange = { newG ->
                    if (isEnabled) {
                        val stepped = round(newG * 2f) / 2f
                        onBandChange(band.copy(gainDb = stepped))
                    }
                },
                valueRange = -12.0f..12.0f,
                colors = SliderDefaults.colors(
                    thumbColor = effectiveAccent,
                    activeTrackColor = effectiveAccent,
                    inactiveTrackColor = m3SurfaceContainerHigh(),
                    disabledThumbColor = disabledGrey,
                    disabledActiveTrackColor = disabledGrey.copy(alpha = 0.6f),
                    disabledInactiveTrackColor = m3SurfaceContainerHigh().copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().height(26.dp)
            )
        }

        // 2. Frequency Control
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frekuensi: ${band.freqHz.toInt()} Hz",
                    color = if (isEnabled) m3OnSurface() else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TactileBox(
                        onClick = {
                            if (isEnabled) {
                                val step = if (band.freqHz > 1000f) 100f else 20f
                                onBandChange(band.copy(freqHz = (band.freqHz - step).coerceAtLeast(20f)))
                            }
                        },
                        enabled = isEnabled,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f))
                    ) {
                        Text("-", color = if (isEnabled) m3OnSurface() else disabledGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TactileBox(
                        onClick = {
                            if (isEnabled) {
                                val step = if (band.freqHz >= 1000f) 100f else 20f
                                onBandChange(band.copy(freqHz = (band.freqHz + step).coerceAtMost(20000f)))
                            }
                        },
                        enabled = isEnabled,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f))
                    ) {
                        Text("+", color = if (isEnabled) m3OnSurface() else disabledGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Q-Factor Control (Lebar / Musikal ↔ Sempit / Presisi)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val qDesc = if (band.q <= 0.9f) "Lebar / Musikal" else if (band.q <= 1.8f) "Normal" else "Sempit / Tajam"
                Text(
                    text = String.format(Locale.US, "Q-Factor: %.2f (%s)", band.q, qDesc),
                    color = if (isEnabled) m3OnSurface() else disabledGrey,
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TactileBox(
                        onClick = {
                            if (isEnabled) {
                                onBandChange(band.copy(q = ((round((band.q - 0.05f) * 20f) / 20f)).coerceIn(0.3f, 5.0f)))
                            }
                        },
                        enabled = isEnabled,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f))
                    ) {
                        Text("-", color = if (isEnabled) m3OnSurface() else disabledGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TactileBox(
                        onClick = {
                            if (isEnabled) {
                                onBandChange(band.copy(q = ((round((band.q + 0.05f) * 20f) / 20f)).coerceIn(0.3f, 5.0f)))
                            }
                        },
                        enabled = isEnabled,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(m3SurfaceContainerHigh().copy(alpha = if (isEnabled) 1.0f else 0.40f))
                    ) {
                        Text("+", color = if (isEnabled) m3OnSurface() else disabledGrey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Slider(
                value = band.q,
                enabled = isEnabled,
                onValueChange = { newQ ->
                    if (isEnabled) {
                        val stepped = round(newQ * 20f) / 20f
                        onBandChange(band.copy(q = stepped.coerceIn(0.3f, 5.0f)))
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
                modifier = Modifier.fillMaxWidth().height(26.dp)
            )
        }
    }
}

/**
 * In-Sheet Interactive Guide Card: Step-by-step instructions, parameter glossary,
 * 10-band acoustic sound zone map, and quick troubleshooting recipes.
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
            Text(
                text = "💡 Panduan Pintar 10-Band Parametric EQ",
                color = m3OnSurface(),
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tutup",
                color = accentColor,
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onClose() }
            )
        }

        // Section 1: Cara Pakai Cepat
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "1. Cara Pakai Cepat (Untuk Pemula)",
                color = accentColor,
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• Langkah 1: Geser 10 Slider Vertikal ke atas (+dB) atau bawah (-dB) dengan jari untuk membentuk karakter suara idamanmu. Ketuk 2x (double-tap) pada slider untuk reset instan ke 0 dB.\n" +
                       "• Langkah 2: Tekan '💾 Simpan Preset' untuk menyimpan racikanmu, atau gunakan tombol '⇄ Export / Import' untuk menyalin kode preset dan membagikannya ke teman/komunitas Discord!\n" +
                       "• Langkah 3: Matikan tombol sakelar ON/OFF di pojok kanan atas kapan saja jika ingin kembali ke mode 100% Bit-Perfect Murni (Direct USB bypass).",
                color = m3OnSurface(),
                fontSize = 10.5.sp,
                fontFamily = ModernSansFont,
                lineHeight = 15.sp
            )
        }

        // Section 2: Kamus Parameter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "2. Kamus Singkat Parameter EQ",
                color = accentColor,
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• Gain (dB): Kekuatan frekuensi diangkat (+) atau dipotong (-). Perubahan ±1.5 s.d. ±4 dB memberikan hasil paling natural.\n" +
                       "• Q-Factor: Lebar lengkungan kurva. Q Rendah (0.5–0.9) = lebar & musikal (soundstage luas). Q Tinggi (1.5–5.0) = sempit & presisi (menghilangkan frekuensi mengganggu).\n" +
                       "• Tipe Filter: LS (Low Shelf: mengangkat semua bass ke bawah), PK (Peaking Bell: bukit/lembah di frekuensi tengah), HS (High Shelf: mengangkat semua treble ke atas).\n" +
                       "• Pre-Amp & Auto-Clip: Volume dasar sebelum filter. Biarkan di 0.0 dB untuk tenaga suara maksimal, atau nyalakan Auto-Clip jika menaikkan bass tinggi agar terhindar dari distorsi digital.",
                color = m3OnSurface(),
                fontSize = 10.5.sp,
                fontFamily = ModernSansFont,
                lineHeight = 15.sp
            )
        }

        // Section 3: Peta Suara 10-Band
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "3. Peta Suara 10-Band (31 Hz – 16 kHz)",
                color = accentColor,
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• 31 Hz – 125 Hz (Zona Bass): Getaran sub-bass bioskop, hentakan kick drum, dan kehangatan bodi musik.\n" +
                       "• 250 Hz – 1 kHz (Zona Vokal & Inti): Ketebalan instrumen (low-mid) dan fokus maju-mundurnya vokal penyanyi.\n" +
                       "• 2 kHz – 4 kHz (Zona Clarity & Attack): Artikulasi vokal, petikan senar gitar, dan ketajaman serangan instrumen.\n" +
                       "• 8 kHz – 16 kHz (Zona Soundstage & Air): Kilau simbal, udara gema panggung (soundstage luas), dan micro-detail halus.",
                color = m3OnSurface(),
                fontSize = 10.5.sp,
                fontFamily = ModernSansFont,
                lineHeight = 15.sp
            )
        }

        // Section 4: Resep Cepat Solusi Audio
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "4. Resep Cepat Sesuai Keluhan Telinga",
                color = accentColor,
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "• Ingin Suara Lebih Powerful & Nendang? → Naikkan 62 Hz (+3.0 dB) dan 31 Hz (+2.5 dB), pastikan Pre-Amp di 0 dB.\n" +
                       "• Ingin Soundstage Luas & Micro-Detail Keluar? → Naikkan 16 kHz (+2.5 dB) dan 8 kHz (+1.5 dB), potong sedikit 250 Hz (-1.5 dB) agar kabut hilang.\n" +
                       "• Ingin Vokal Lebih Jernih & Maju ke Depan? → Naikkan 1 kHz (+2.0 dB) dan 2 kHz (+1.5 dB).\n" +
                       "• Suara Terlalu Pedas / Menusuk Telinga? → Turunkan 4 kHz (-2.0 dB) atau 8 kHz (-2.5 dB).",
                color = m3OnSurface(),
                fontSize = 10.5.sp,
                fontFamily = ModernSansFont,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Inline card for saving or updating custom user PEQ presets (#NOTE 2).
 */
@Composable
private fun PeqSavePresetCard(
    accentColor: Color,
    presetName: String,
    activeUserPreset: UserPeqPreset?,
    onNameChange: (String) -> Unit,
    onSaveNew: () -> Unit,
    onUpdateExisting: () -> Unit,
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
            Text(
                text = "💾 Simpan Racikan Parametric EQ",
                color = m3OnSurface(),
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tutup",
                color = accentColor,
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onClose() }
            )
        }

        Text(
            text = "Beri nama preset kustom Anda untuk disimpan dan di-load kembali kapan saja saat bereksperimen:",
            color = m3OnSurfaceVariant(),
            fontSize = 11.sp,
            fontFamily = ModernSansFont,
            lineHeight = 15.sp
        )

        // Text input field box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh())
                .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (presetName.isEmpty()) {
                Text(
                    text = "Ketik nama preset (misal: IEM Bass, Warm Vocal)...",
                    color = m3OnSurfaceVariant().copy(alpha = 0.6f),
                    fontSize = 12.5.sp,
                    fontFamily = ModernSansFont
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = presetName,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = m3OnSurface(),
                    fontSize = 12.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Save as New Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (presetName.isNotBlank()) accentColor else m3SurfaceContainerHigh())
                    .clickable(enabled = presetName.isNotBlank()) { onSaveNew() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "＋ Simpan Preset Baru",
                    color = if (presetName.isNotBlank()) Color.White else m3OnSurfaceVariant(),
                    fontSize = 11.5.sp,
                    fontFamily = ModernSansFont,
                    fontWeight = FontWeight.Bold
                )
            }

            // Update existing button if user is currently editing an existing user preset
            if (activeUserPreset != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { onUpdateExisting() }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻ Update '${activeUserPreset.name}'",
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Inline card for exporting and importing custom PEQ presets (#NOTE 1).
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
            Text(
                text = "⇄ Export & Import Preset PEQ",
                color = m3OnSurface(),
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tutup",
                color = accentColor,
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onClose() }
            )
        }

        // --- SECTION 1: EXPORT / BAGIKAN ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "📤 Export / Bagikan Preset",
                color = accentColor,
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Preset aktif: \"${presetToExport.name}\" (${String.format(Locale.US, "%+.1f dB", presetToExport.preampDb)})",
                color = m3OnSurface(),
                fontSize = 10.5.sp,
                fontFamily = TerminalFont
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Copy Code
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .clickable {
                            val code = PeqPreferencesHelper.exportPresetToShareCode(presetToExport)
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, "Kode '${presetToExport.name}' disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📋 Salin Kode",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Share Intent (Discord/WA)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            val code = PeqPreferencesHelper.exportPresetToShareCode(presetToExport)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, code)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Bagikan Preset PEQ (${presetToExport.name})")
                            context.startActivity(shareIntent)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📤 Bagikan",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. Export File .json
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable {
                            val jsonStr = PeqPreferencesHelper.exportPresetsToJsonString(listOf(presetToExport))
                            val safeName = presetToExport.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                            onExportJsonFile(jsonStr, "peq_${safeName}.json")
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💾 File .json",
                        color = m3OnSurface(),
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (allUserPresets.size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(m3SurfaceContainerLow())
                        .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .clickable {
                            val jsonStr = PeqPreferencesHelper.exportPresetsToJsonString(allUserPresets)
                            onExportJsonFile(jsonStr, "peq_all_presets.json")
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📦 Backup Semua Preset (${allUserPresets.size} preset) ke .json",
                        color = m3OnSurfaceVariant(),
                        fontSize = 10.5.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- SECTION 2: IMPORT / TERIMA PRESET ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(m3SurfaceContainerHigh().copy(alpha = 0.5f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "📥 Import Preset dari Teman / Komunitas",
                color = accentColor,
                fontSize = 11.5.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Salin kode dari Discord/WA, lalu tekan tombol di bawah untuk langsung menerapkan:",
                color = m3OnSurfaceVariant(),
                fontSize = 10.5.sp,
                fontFamily = ModernSansFont
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. One-click Paste & Import from Clipboard
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .clickable {
                            val clipData = clipboardManager.getText()?.text ?: ""
                            if (clipData.isNotBlank()) {
                                onImportText(clipData)
                            } else {
                                Toast.makeText(context, "Clipboard kosong! Salin kode preset terlebih dahulu.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📋 Tempel dari Clipboard",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Import File .json
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(m3SurfaceContainerHigh())
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { onImportJsonFile() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📂 File .json",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Optional Manual text field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(m3SurfaceContainerHigh())
                    .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                if (manualInputText.isEmpty()) {
                    Text(
                        text = "Atau ketik/tempel kode TSROSSA-PEQ1:... di sini",
                        color = m3OnSurfaceVariant().copy(alpha = 0.5f),
                        fontSize = 10.5.sp,
                        fontFamily = ModernSansFont
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = manualInputText,
                    onValueChange = { manualInputText = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = m3OnSurface(),
                        fontSize = 11.sp,
                        fontFamily = TerminalFont
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (manualInputText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .clickable { onImportText(manualInputText.trim()) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋ Import Kode yang Diketik",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
