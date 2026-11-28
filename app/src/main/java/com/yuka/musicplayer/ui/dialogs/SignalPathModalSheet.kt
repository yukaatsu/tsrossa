package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.audio.PeqConfiguration
import com.yuka.musicplayer.audio.UserPeqPreset
import com.yuka.musicplayer.audio.compressor.CompressorConfig
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphAudioFile
import com.yuka.musicplayer.ui.components.GlyphDecoderCpu
import com.yuka.musicplayer.ui.components.GlyphDirectUsbLink
import com.yuka.musicplayer.ui.components.GlyphDitherSparkle
import com.yuka.musicplayer.ui.components.GlyphDspStack
import com.yuka.musicplayer.ui.components.GlyphDynamicsWave
import com.yuka.musicplayer.ui.components.GlyphEqualizer
import com.yuka.musicplayer.ui.components.GlyphInfo
import com.yuka.musicplayer.ui.components.GlyphSealCheck
import com.yuka.musicplayer.ui.components.GlyphSignalPulse
import com.yuka.musicplayer.ui.components.GlyphUsbDac
import com.yuka.musicplayer.ui.components.GlyphVolume
import com.yuka.musicplayer.ui.components.GlyphVolumeMute
import com.yuka.musicplayer.ui.components.GlyphWarning
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.components.resolveSignalPathAccentColor
import com.yuka.musicplayer.ui.diagnostics.ConnectedDacIdentity
import com.yuka.musicplayer.ui.diagnostics.DacTelemetryState
import com.yuka.musicplayer.ui.diagnostics.LiveSignalPathSnapshot
import com.yuka.musicplayer.ui.diagnostics.SampleRateTransmissionState
import com.yuka.musicplayer.ui.diagnostics.SignalPathStatus
import com.yuka.musicplayer.ui.diagnostics.buildActiveDspSummaryTags
import com.yuka.musicplayer.ui.diagnostics.evaluateSampleRateTransmission
import com.yuka.musicplayer.ui.diagnostics.formatDbReadout
import com.yuka.musicplayer.ui.diagnostics.formatSampleRateKhz
import com.yuka.musicplayer.ui.diagnostics.formatUsbBufferProfileLabel
import com.yuka.musicplayer.ui.diagnostics.resolveSignalPathStatus
import com.yuka.musicplayer.ui.theme.AudiophileAmber
import com.yuka.musicplayer.ui.theme.AudiophileCyan
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3GlassBorder
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive Poweramp-style vertical Signal Path modal sheet using Material You (M3) tokens.
 * Polls the lock-free batched JNI snapshot at 10 Hz strictly while visible & DAC connected,
 * isolated into small leaf composables to avoid full-sheet recomposition.
 */
@Composable
fun SignalPathModalSheet(
    visible: Boolean,
    track: TrackInfo?,
    audioEngine: AudioEngine,
    telemetry: DacTelemetryState,
    dacIdentity: ConnectedDacIdentity,
    isDacConnected: Boolean,
    isPlaying: Boolean,
    currentVolume: Float,
    isHardwareVolumeActive: Boolean,
    peqConfig: PeqConfiguration,
    userPeqPresets: List<UserPeqPreset>,
    compressorConfig: CompressorConfig,
    usbBufferProfile: Int,
    onOpenVolumeModal: () -> Unit,
    onInspectRawLogs: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val liveSnapshotState = remember { mutableStateOf<LiveSignalPathSnapshot?>(null) }

    LaunchedEffect(visible, isDacConnected, isPlaying, track?.file?.absolutePath) {
        if (!visible) {
            liveSnapshotState.value = null
            return@LaunchedEffect
        }
        if (!isDacConnected) {
            liveSnapshotState.value = null
            return@LaunchedEffect
        }
        val fBuf = FloatArray(LiveSignalPathSnapshot.FLOAT_ARRAY_SIZE)
        val iBuf = IntArray(LiveSignalPathSnapshot.INT_ARRAY_SIZE)
        while (isActive) {
            val snap = withContext(Dispatchers.IO) {
                if (audioEngine.readLiveSignalPathSnapshot(fBuf, iBuf)) {
                    LiveSignalPathSnapshot.fromRawArrays(fBuf, iBuf)
                } else {
                    null
                }
            }
            if (snap != null && snap != liveSnapshotState.value) {
                liveSnapshotState.value = snap
            }
            delay(100L)
        }
    }

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.88f,
        maxWidth = 440.dp
    ) {
        if (track == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No track loaded",
                    color = m3OnSurfaceVariant(),
                    fontFamily = ModernSansFont,
                    fontSize = 14.sp
                )
            }
        } else {
            val snapshotProvider = remember { { liveSnapshotState.value ?: LiveSignalPathSnapshot.EMPTY } }
            val hasLiveSnapshotProvider = remember { { liveSnapshotState.value != null } }
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with dynamic mode badge pill (isolated leaf reader)
                SignalPathHeader(
                    track = track,
                    telemetry = telemetry,
                    isDacConnected = isDacConnected,
                    isPlaying = isPlaying,
                    fallbackHwActive = isHardwareVolumeActive,
                    snapshotProvider = snapshotProvider,
                    hasLiveSnapshotProvider = hasLiveSnapshotProvider
                )

                // Node 1 — Source (Audio Info)
                SignalPathSourceNode(
                    track = track,
                    telemetry = telemetry,
                    isDacConnected = isDacConnected,
                    snapshotProvider = snapshotProvider
                )

                // Node 2 — Decoder & Buffer
                SignalPathDecoderNode(
                    track = track,
                    usbBufferProfile = usbBufferProfile,
                    isDacConnected = isDacConnected,
                    connectorColor = resolveSignalPathAccentColor(
                        rememberPathStatus(
                            track = track,
                            telemetry = telemetry,
                            isDacConnected = isDacConnected,
                            isPlaying = isPlaying,
                            fallbackHwActive = isHardwareVolumeActive,
                            snapshot = snapshotProvider(),
                            hasLiveSnapshot = hasLiveSnapshotProvider()
                        )
                    ),
                    snapshotProvider = snapshotProvider
                )

                if (!isDacConnected) {
                    // Disconnected fallback status card (zero fabricated speaker/Bluetooth path)
                    SignalPathDisconnectedCard()
                } else {
                    // Node 3 — Processing & 64-Bit DSP Chain (Dynamic 3 branches)
                    SignalPathProcessingSection(
                        telemetry = telemetry,
                        fallbackHwActive = isHardwareVolumeActive,
                        peqConfig = peqConfig,
                        userPeqPresets = userPeqPresets,
                        compressorConfig = compressorConfig,
                        snapshotProvider = snapshotProvider,
                        hasLiveSnapshotProvider = hasLiveSnapshotProvider
                    )

                    // Node 4 — Volume Control (Leaf live gain readout + Adjust shortcut)
                    SignalPathVolumeNode(
                        currentVolume = currentVolume,
                        telemetry = telemetry,
                        fallbackHwActive = isHardwareVolumeActive,
                        onAdjustClick = onOpenVolumeModal,
                        snapshotProvider = snapshotProvider,
                        hasLiveSnapshotProvider = hasLiveSnapshotProvider
                    )

                    // Node 5 — Output Transport (Direct Kernel USB + Clock/SRC + Latency)
                    SignalPathTransportNode(
                        track = track,
                        telemetry = telemetry,
                        isPlaying = isPlaying,
                        fallbackHwActive = isHardwareVolumeActive,
                        snapshotProvider = snapshotProvider,
                        hasLiveSnapshotProvider = hasLiveSnapshotProvider
                    )

                    // Node 6 — Output Device (USB DAC Identity & Bit Resolution)
                    SignalPathOutputDeviceNode(
                        dacIdentity = dacIdentity,
                        telemetry = telemetry,
                        fallbackHwActive = isHardwareVolumeActive,
                        snapshotProvider = snapshotProvider,
                        hasLiveSnapshotProvider = hasLiveSnapshotProvider
                    )
                }

                // Bottom 2-Column Factual M3 Summary Cards
                SignalPathBottomSummaryCards(
                    track = track,
                    telemetry = telemetry,
                    isDacConnected = isDacConnected,
                    isPlaying = isPlaying,
                    currentVolume = currentVolume,
                    fallbackHwActive = isHardwareVolumeActive,
                    snapshotProvider = snapshotProvider,
                    hasLiveSnapshotProvider = hasLiveSnapshotProvider
                )

                // Lightweight Footer Action -> Jump to System Logs / Raw Audit
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TactileBox(
                        onClick = onInspectRawLogs,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(m3SurfaceContainer())
                            .border(1.dp, m3GlassBorder(), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlyphInfo(
                                color = m3OnSurfaceVariant(),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Inspect Raw Logs",
                                color = m3OnSurfaceVariant(),
                                fontFamily = ModernSansFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPathStatus(
    track: TrackInfo,
    telemetry: DacTelemetryState,
    isDacConnected: Boolean,
    isPlaying: Boolean,
    fallbackHwActive: Boolean,
    snapshot: LiveSignalPathSnapshot,
    hasLiveSnapshot: Boolean
): SignalPathStatus {
    val srState = evaluateSampleRateTransmission(
        isPlaying = isPlaying,
        sourceSampleRate = track.sampleRateHz,
        outputSampleRate = telemetry.outputSampleRate,
        isSampleRateUnverified = telemetry.isSampleRateUnverified
    )
    val effectiveBitPerfect = if (isDacConnected && isPlaying && hasLiveSnapshot) {
        snapshot.bitPerfectDirectPass
    } else {
        telemetry.isBitPerfectDirectPass
    }
    val effectiveHwActive = if (isDacConnected && isPlaying && hasLiveSnapshot) {
        snapshot.hwVolumeActive
    } else {
        fallbackHwActive
    }
    return resolveSignalPathStatus(
        isDacConnected = isDacConnected,
        isPlaying = isPlaying,
        isBitPerfectDirectPass = effectiveBitPerfect,
        isHardwareVolumeActive = effectiveHwActive,
        sampleRateState = srState,
        isDeviceWedged = telemetry.isDeviceWedged,
        isHardwareVolumeLockedBySystem = telemetry.isHardwareVolumeLockedBySystem
    )
}

@Composable
private fun SignalPathHeader(
    track: TrackInfo,
    telemetry: DacTelemetryState,
    isDacConnected: Boolean,
    isPlaying: Boolean,
    fallbackHwActive: Boolean,
    snapshotProvider: () -> LiveSignalPathSnapshot,
    hasLiveSnapshotProvider: () -> Boolean
) {
    val snap = snapshotProvider()
    val status = rememberPathStatus(
        track = track,
        telemetry = telemetry,
        isDacConnected = isDacConnected,
        isPlaying = isPlaying,
        fallbackHwActive = fallbackHwActive,
        snapshot = snap,
        hasLiveSnapshot = hasLiveSnapshotProvider()
    )
    val statusColor = resolveSignalPathAccentColor(status)
    val pillLabel = when (status) {
        SignalPathStatus.BIT_PERFECT -> "BIT-PERFECT"
        SignalPathStatus.HYBRID_UAC_TRIM -> "HYBRID HW UAC"
        SignalPathStatus.UNIVERSAL_DSP -> "64-BIT DSP"
        SignalPathStatus.WARNING -> "CLOCK WARNING"
        SignalPathStatus.NO_DAC -> "NO USB DAC"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Audio Info",
                color = m3OnSurface(),
                fontFamily = ModernSansFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Signal Path & Engine Audit",
                color = m3OnSurfaceVariant(),
                fontFamily = ModernSansFont,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(statusColor.copy(alpha = 0.16f).compositeOver(m3SurfaceContainer()))
                .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = pillLabel,
                color = statusColor,
                fontFamily = TerminalFont,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalPathSourceNode(
    track: TrackInfo,
    telemetry: DacTelemetryState,
    isDacConnected: Boolean,
    snapshotProvider: () -> LiveSignalPathSnapshot
) {
    val snap = snapshotProvider()
    val channels = if (isDacConnected && snap.liveChannels > 0) snap.liveChannels else track.channels.coerceAtLeast(1)
    val srcSr = if (isDacConnected && telemetry.sourceSampleRate > 0) telemetry.sourceSampleRate else track.sampleRateHz
    val srcBits = if (isDacConnected && telemetry.sourceBitDepth > 0) telemetry.sourceBitDepth else track.bitDepth
    val isFloat = track.isFloatPcm || (isDacConnected && snap.sourceIsFloatPcm)
    val bitrateText = if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps (avg)" else "Lossless PCM"
    val bitDepthLabel = when {
        srcBits > 0 && isFloat -> "${srcBits}-bit Float"
        srcBits > 0 -> "${srcBits}-bit"
        else -> "Standard PCM"
    }

    SignalPathNodeCard(
        title = "Source (Audio Info)",
        icon = { GlyphAudioFile(color = m3OnSurface(), modifier = Modifier.size(20.dp)) },
        connectorActive = true,
        connectorColor = m3OnSurfaceVariant().copy(alpha = 0.35f),
        isLast = false
    ) {
        Text(
            text = "${track.codec.uppercase()} • ${channels}ch • $bitrateText",
            color = m3OnSurface(),
            fontFamily = TerminalFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${formatSampleRateKhz(srcSr)} • $bitDepthLabel",
            color = m3OnSurfaceVariant(),
            fontFamily = TerminalFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isDacConnected) {
                val gaplessActive = snap.hasNextTrack
                val chipAccent = if (gaplessActive) {
                    resolveSignalPathAccentColor(SignalPathStatus.BIT_PERFECT)
                } else {
                    m3OnSurfaceVariant()
                }
                MicroStatusChip(
                    label = if (gaplessActive) "Gapless Ready" else "Gapless: Idle",
                    accentColor = chipAccent,
                    highlighted = gaplessActive
                )
            }
            if (track.isMqa) {
                MicroStatusChip(
                    label = "MQA Flagged (Passthrough)",
                    accentColor = m3OnSurfaceVariant(),
                    highlighted = false
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalPathDecoderNode(
    track: TrackInfo,
    usbBufferProfile: Int,
    isDacConnected: Boolean,
    connectorColor: Color,
    snapshotProvider: () -> LiveSignalPathSnapshot
) {
    val snap = snapshotProvider()
    val decoderName = when (track.codec.uppercase()) {
        "FLAC" -> "dr_flac (Native C++17)"
        "WAV", "WAVE" -> "dr_wav (Native C++17)"
        else -> "${track.codec.uppercase()} (Native C++17)"
    }
    val bufferBaseLabel = formatUsbBufferProfileLabel(usbBufferProfile)
    val dmaDetail = if (isDacConnected && snap.activeIsoTransfers > 0 && snap.activeIsoPacketsPerTransfer > 0) {
        "$bufferBaseLabel (${snap.activeIsoTransfers}×${snap.activeIsoPacketsPerTransfer} URBs)"
    } else {
        bufferBaseLabel
    }

    SignalPathNodeCard(
        title = "Decoder & Buffer",
        icon = { GlyphDecoderCpu(color = m3OnSurface(), modifier = Modifier.size(20.dp)) },
        connectorActive = isDacConnected,
        connectorColor = connectorColor,
        isLast = false
    ) {
        Text(
            text = "Decoder: $decoderName",
            color = m3OnSurface(),
            fontFamily = TerminalFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "64 MB Sliding PCM Ring Buffer • ISO DMA: $dmaDetail",
            color = m3OnSurfaceVariant(),
            fontFamily = TerminalFont,
            fontSize = 11.sp
        )
        if (isDacConnected) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val green = resolveSignalPathAccentColor(SignalPathStatus.BIT_PERFECT)
                val cyan = resolveSignalPathAccentColor(SignalPathStatus.UNIVERSAL_DSP)
                if (snap.isMlockActive) {
                    MicroStatusChip(label = "mlock() Pinned", accentColor = green, highlighted = true)
                }
                if (snap.isRtSchedFifoActive) {
                    MicroStatusChip(label = "SCHED_FIFO RT", accentColor = cyan, highlighted = true)
                }
                if (!snap.isMlockActive && !snap.isRtSchedFifoActive) {
                    MicroStatusChip(
                        label = "OS Threading (Nice -20 / Urgent Audio)",
                        accentColor = m3OnSurfaceVariant(),
                        highlighted = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalPathDisconnectedCard() {
    val warnColor = resolveSignalPathAccentColor(SignalPathStatus.WARNING)
    SignalPathNodeCard(
        title = "USB DAC Disconnected",
        titleColor = warnColor,
        icon = { GlyphWarning(color = warnColor, modifier = Modifier.size(20.dp)) },
        connectorActive = false,
        connectorColor = warnColor,
        isLast = true,
        accentTint = warnColor.copy(alpha = 0.08f)
    ) {
        Text(
            text = "Connect an external USB DAC for Exclusive Direct USB playback. Tsrossa bypasses Android AudioFlinger with zero OS mixing.",
            color = m3OnSurfaceVariant(),
            fontFamily = ModernSansFont,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun SignalPathProcessingSection(
    telemetry: DacTelemetryState,
    fallbackHwActive: Boolean,
    peqConfig: PeqConfiguration,
    userPeqPresets: List<UserPeqPreset>,
    compressorConfig: CompressorConfig,
    snapshotProvider: () -> LiveSignalPathSnapshot,
    hasLiveSnapshotProvider: () -> Boolean
) {
    val snap = snapshotProvider()
    val hasLive = hasLiveSnapshotProvider()
    val hwActive = if (hasLive) snap.hwVolumeActive else fallbackHwActive
    val bitPerfect = if (hasLive) snap.bitPerfectDirectPass else telemetry.isBitPerfectDirectPass

    val green = resolveSignalPathAccentColor(SignalPathStatus.BIT_PERFECT)
    val amber = resolveSignalPathAccentColor(SignalPathStatus.HYBRID_UAC_TRIM)
    val cyan = resolveSignalPathAccentColor(SignalPathStatus.UNIVERSAL_DSP)

    when {
        bitPerfect -> {
            SignalPathNodeCard(
                title = "Bit-Perfect Integer Direct Pass",
                titleColor = green,
                icon = { GlyphSealCheck(color = green, modifier = Modifier.size(20.dp)) },
                connectorActive = true,
                connectorColor = green,
                isLast = false,
                accentTint = green.copy(alpha = 0.08f)
            ) {
                Text(
                    text = "0.0 dBFS Unity • Zero Float Conversion • No Dither",
                    color = m3OnSurface(),
                    fontFamily = TerminalFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "SRC: 0% (Native Rate)",
                    color = green,
                    fontFamily = TerminalFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (peqConfig.enabled && !snap.peqEffective) {
                    Text(
                        text = "PEQ: Flat True-Bypass (All Bands 0.0 dB)",
                        color = m3OnSurfaceVariant(),
                        fontFamily = TerminalFont,
                        fontSize = 10.5.sp
                    )
                }
                if (compressorConfig.enabled && !snap.compEffective) {
                    Text(
                        text = "Dynamics: Neutral True-Bypass (1.0:1 / 0.0 dB)",
                        color = m3OnSurfaceVariant(),
                        fontFamily = TerminalFont,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        hwActive -> {
            SignalPathNodeCard(
                title = "Hybrid Fine-Trim (64-Bit Float)",
                titleColor = amber,
                icon = { GlyphDitherSparkle(color = amber, modifier = Modifier.size(20.dp)) },
                connectorActive = true,
                connectorColor = amber,
                isLast = false,
                accentTint = amber.copy(alpha = 0.08f)
            ) {
                Text(
                    text = "Residual Trim: ${formatDbReadout(snap.dspGainDb)} • 15 ms Linear Ramp",
                    color = m3OnSurface(),
                    fontFamily = TerminalFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "SplitMix64 8-Ch TPDF Dither • std::llrint",
                    color = m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.sp
                )
                Text(
                    text = "PEQ & Dynamics: Bypassed (Hardware UAC Mode)",
                    color = m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 10.5.sp
                )
            }
        }

        else -> {
            // Sub-node 3a: Parametric EQ (only if toggle is enabled)
            if (peqConfig.enabled) {
                val activeBandCount = peqConfig.bands.count { it.enabled && abs(it.gainDb) >= 0.05f }
                val presetName = resolvePeqPresetLabel(peqConfig, userPeqPresets)
                val preampMode = if (peqConfig.autoPreampEnabled) "Auto" else "Manual"

                SignalPathNodeCard(
                    title = "Parametric EQ (10-Band RBJ)",
                    titleColor = if (snap.peqEffective) cyan else m3OnSurface(),
                    icon = { GlyphEqualizer(color = if (snap.peqEffective) cyan else m3OnSurfaceVariant(), modifier = Modifier.size(20.dp)) },
                    connectorActive = true,
                    connectorColor = cyan,
                    isLast = false,
                    accentTint = if (snap.peqEffective) cyan.copy(alpha = 0.07f) else null
                ) {
                    if (snap.peqEffective) {
                        Text(
                            text = "$activeBandCount/10 Bands Active • Preset: $presetName",
                            color = m3OnSurface(),
                            fontFamily = TerminalFont,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Preamp: ${formatDbReadout(peqConfig.effectivePreampDb)} ($preampMode) • 64-bit RBJ TDF-II",
                            color = m3OnSurfaceVariant(),
                            fontFamily = TerminalFont,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Enabled — True Bypass (All Bands 0.0 dB)",
                            color = m3OnSurfaceVariant(),
                            fontFamily = TerminalFont,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Sub-node 3b: Dynamics (Upward / Downward Compressor)
            if (compressorConfig.enabled) {
                val clamped = compressorConfig.clamped()
                val isDark = LocalIsDarkTheme.current
                val boostColor = if (isDark) AudiophileCyan else Color(0xFF007A8C)
                val grColor = if (isDark) AudiophileAmber else Color(0xFFB27B00)

                SignalPathNodeCard(
                    title = "Dynamics (Upward / Downward)",
                    titleColor = if (snap.compEffective) cyan else m3OnSurface(),
                    icon = { GlyphDynamicsWave(color = if (snap.compEffective) cyan else m3OnSurfaceVariant(), modifier = Modifier.size(20.dp)) },
                    connectorActive = true,
                    connectorColor = cyan,
                    isLast = false,
                    accentTint = if (snap.compEffective) cyan.copy(alpha = 0.07f) else null,
                    trailingAction = if (snap.compEffective) {
                        {
                            val gainDb = snap.compGainDb
                            val meterText = when {
                                gainDb <= -0.05f -> String.format(Locale.US, "%.1f dB GR", gainDb)
                                gainDb >= 0.05f -> String.format(Locale.US, "+%.1f dB Boost", gainDb)
                                else -> "0.0 dB Unity"
                            }
                            val meterColor = when {
                                gainDb <= -0.05f -> grColor
                                gainDb >= 0.05f -> boostColor
                                else -> m3OnSurfaceVariant()
                            }
                            MicroStatusChip(
                                label = meterText,
                                accentColor = meterColor,
                                highlighted = abs(gainDb) >= 0.05f
                            )
                        }
                    } else null
                ) {
                    if (snap.compEffective) {
                        val regions = buildList {
                            if (clamped.downRatio > 1.001f) {
                                add(
                                    String.format(
                                        Locale.US,
                                        "Down %.1f:1 @ %.0f dB",
                                        clamped.downRatio,
                                        clamped.downThresholdDb
                                    )
                                )
                            }
                            if (clamped.upRatio > 1.001f && clamped.maxUpwardBoostDb > 0.01f) {
                                add(
                                    String.format(
                                        Locale.US,
                                        "Up %.1f:1 @ %.0f dB (Max +%.1f dB)",
                                        clamped.upRatio,
                                        clamped.upThresholdDb,
                                        clamped.maxUpwardBoostDb
                                    )
                                )
                            }
                        }
                        if (regions.isNotEmpty()) {
                            Text(
                                text = regions.joinToString(" • "),
                                color = m3OnSurface(),
                                fontFamily = TerminalFont,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val detectorLabel = if (clamped.useRmsDetector) "RMS" else "Peak"
                        Text(
                            text = "$detectorLabel Detector • Makeup: ${formatDbReadout(clamped.makeupGainDb)}",
                            color = m3OnSurfaceVariant(),
                            fontFamily = TerminalFont,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Enabled — True Bypass (Neutral Ratios)",
                            color = m3OnSurfaceVariant(),
                            fontFamily = TerminalFont,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Sub-node 3c: Lookahead Limiter & 64-Bit TPDF Quantizer
            val limiterActive = snap.peqEffective || snap.compEffective
            SignalPathNodeCard(
                title = if (limiterActive) "Lookahead Limiter & TPDF Quantizer" else "64-Bit Float Gain & TPDF Quantizer",
                titleColor = cyan,
                icon = { GlyphDitherSparkle(color = cyan, modifier = Modifier.size(20.dp)) },
                connectorActive = true,
                connectorColor = cyan,
                isLast = false,
                accentTint = cyan.copy(alpha = 0.06f)
            ) {
                if (limiterActive) {
                    val limiterLine = if (snap.limiterGrDb <= -0.05f) {
                        "Stereo-Linked Peak Limiter (-0.10 dBFS • ~1.45 ms) • GR: ${formatDbReadout(snap.limiterGrDb, showPositivePlus = false)}"
                    } else {
                        "Stereo-Linked Peak Limiter (-0.10 dBFS • ~1.45 ms) • Clean"
                    }
                    Text(
                        text = limiterLine,
                        color = m3OnSurface(),
                        fontFamily = TerminalFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "64-Bit IEEE-754 Float Stage • 15 ms Linear Volume Ramp",
                        color = m3OnSurface(),
                        fontFamily = TerminalFont,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "SplitMix64 8-Ch TPDF Dither (64-Frame Silence Gate) • std::llrint",
                    color = m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SignalPathVolumeNode(
    currentVolume: Float,
    telemetry: DacTelemetryState,
    fallbackHwActive: Boolean,
    onAdjustClick: () -> Unit,
    snapshotProvider: () -> LiveSignalPathSnapshot,
    hasLiveSnapshotProvider: () -> Boolean
) {
    val snap = snapshotProvider()
    val hwActive = if (hasLiveSnapshotProvider()) snap.hwVolumeActive else fallbackHwActive
    val pct = (currentVolume.coerceIn(0f, 1f) * 100f).roundToInt()
    val targetDb = AudioEngine.sliderToPerceptualDb(currentVolume.coerceIn(0f, 1f))
    val targetDbStr = if (targetDb.isInfinite() || currentVolume <= 0f) "-∞ dB" else formatDbReadout(targetDb, showPositivePlus = false)

    val green = resolveSignalPathAccentColor(SignalPathStatus.BIT_PERFECT)
    val amber = resolveSignalPathAccentColor(SignalPathStatus.HYBRID_UAC_TRIM)
    val cyan = resolveSignalPathAccentColor(SignalPathStatus.UNIVERSAL_DSP)
    val warn = resolveSignalPathAccentColor(SignalPathStatus.WARNING)
    val nodeAccent = if (hwActive) amber else cyan

    SignalPathNodeCard(
        title = "Volume Control",
        icon = {
            if (currentVolume <= 0.001f) {
                GlyphVolumeMute(color = nodeAccent, modifier = Modifier.size(20.dp))
            } else {
                GlyphVolume(color = nodeAccent, modifier = Modifier.size(20.dp))
            }
        },
        connectorActive = true,
        connectorColor = nodeAccent,
        isLast = false,
        trailingAction = {
            TactileBox(
                onClick = onAdjustClick,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(nodeAccent.copy(alpha = 0.16f).compositeOver(m3SurfaceContainerHigh()))
                    .border(1.dp, nodeAccent.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Adjust",
                    color = nodeAccent,
                    fontFamily = ModernSansFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) {
        Text(
            text = if (hwActive) "Hardware UAC (FU_VOLUME_CONTROL)" else "64-Bit Studio-Taper DSP",
            color = m3OnSurface(),
            fontFamily = ModernSansFont,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Master: $pct% ($targetDbStr)",
            color = m3OnSurface(),
            fontFamily = TerminalFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        if (hwActive) {
            val stepText = String.format(Locale.US, "%.2f dB", abs(snap.resVolumeDb))
            val trimText = if (abs(snap.dspGainDb) < 0.05f) "0.0 dB Unity" else formatDbReadout(snap.dspGainDb)
            Text(
                text = "HW DAC: ${formatDbReadout(snap.appliedHwDb)} (Step $stepText) • DSP Trim: $trimText",
                color = amber,
                fontFamily = TerminalFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            val hwLockStr = "DAC HW: Locked Full-Scale (${formatDbReadout(snap.maxVolumeDb)})"
            val dspLine = if (abs(snap.dspGainDb) < 0.05f && currentVolume >= 0.999f) {
                "DSP Gain: 0.0 dBFS Unity Bypass"
            } else {
                "DSP Gain: ${formatDbReadout(snap.dspGainDb)}"
            }
            Text(
                text = "$hwLockStr • $dspLine",
                color = if (snap.bitPerfectDirectPass) green else cyan,
                fontFamily = TerminalFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (telemetry.isHardwareVolumeLockedBySystem) {
            Text(
                text = "Notice: Hardware AudioControl interface locked by Android OS — routed via 64-bit DSP.",
                color = warn,
                fontFamily = TerminalFont,
                fontSize = 10.5.sp
            )
        } else if (telemetry.isForceSoftwareVolume && !hwActive) {
            Text(
                text = "Notice: DAC lacks UAC Feature Unit — active via 64-bit Software DSP.",
                color = m3OnSurfaceVariant(),
                fontFamily = TerminalFont,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun SignalPathTransportNode(
    track: TrackInfo,
    telemetry: DacTelemetryState,
    isPlaying: Boolean,
    fallbackHwActive: Boolean,
    snapshotProvider: () -> LiveSignalPathSnapshot,
    hasLiveSnapshotProvider: () -> Boolean
) {
    val snap = snapshotProvider()
    val srState = evaluateSampleRateTransmission(
        isPlaying = isPlaying,
        sourceSampleRate = track.sampleRateHz,
        outputSampleRate = telemetry.outputSampleRate,
        isSampleRateUnverified = telemetry.isSampleRateUnverified
    )
    val pathStatus = rememberPathStatus(
        track = track,
        telemetry = telemetry,
        isDacConnected = true,
        isPlaying = isPlaying,
        fallbackHwActive = fallbackHwActive,
        snapshot = snap,
        hasLiveSnapshot = hasLiveSnapshotProvider()
    )
    val accent = resolveSignalPathAccentColor(pathStatus)

    val srcText = when (srState) {
        SampleRateTransmissionState.BIT_PERFECT -> "No Resampling (Native Clock)"
        SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK -> "Unverified Clock"
        SampleRateTransmissionState.RESAMPLED -> "Clock Mismatch"
        SampleRateTransmissionState.NO_STREAM -> "Standby (No Active Stream)"
        SampleRateTransmissionState.UNKNOWN_INSUFFICIENT_DATA -> "Clock Pending"
    }
    val transferMode = if (snap.isAsyncIso) "Async Isochronous" else "Adaptive/Sync Isochronous"
    val ifaceSuffix = if (telemetry.claimedInterfaces.isNotBlank()) " [IF: ${telemetry.claimedInterfaces}]" else ""
    val latencyText = if (snap.estimatedLatencyMs > 0f) {
        String.format(Locale.US, "~%.1f ms", snap.estimatedLatencyMs)
    } else {
        "Standby"
    }

    SignalPathNodeCard(
        title = "Output Transport",
        icon = { GlyphDirectUsbLink(color = accent, modifier = Modifier.size(20.dp)) },
        connectorActive = true,
        connectorColor = accent,
        isLast = false
    ) {
        Text(
            text = "Direct Kernel USB (usbfs / libusb) • Exclusive Mode$ifaceSuffix",
            color = m3OnSurface(),
            fontFamily = TerminalFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "UAC v${telemetry.uacVersion.coerceAtLeast(1)} • $transferMode",
            color = m3OnSurfaceVariant(),
            fontFamily = TerminalFont,
            fontSize = 11.sp
        )
        Text(
            text = "SRC: $srcText • Latency: $latencyText",
            color = if (srState == SampleRateTransmissionState.RESAMPLED || srState == SampleRateTransmissionState.UNKNOWN_UNVERIFIED_CLOCK) {
                resolveSignalPathAccentColor(SignalPathStatus.WARNING)
            } else {
                m3OnSurfaceVariant()
            },
            fontFamily = TerminalFont,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SignalPathOutputDeviceNode(
    dacIdentity: ConnectedDacIdentity,
    telemetry: DacTelemetryState,
    fallbackHwActive: Boolean,
    snapshotProvider: () -> LiveSignalPathSnapshot,
    hasLiveSnapshotProvider: () -> Boolean
) {
    val snap = snapshotProvider()
    val hasLive = hasLiveSnapshotProvider()
    val green = resolveSignalPathAccentColor(SignalPathStatus.BIT_PERFECT)
    val amber = resolveSignalPathAccentColor(SignalPathStatus.HYBRID_UAC_TRIM)
    val hwActive = if (hasLive) snap.hwVolumeActive else fallbackHwActive
    val bitPerfect = if (hasLive) snap.bitPerfectDirectPass else telemetry.isBitPerfectDirectPass
    val deviceTint = if (bitPerfect) green else if (hwActive) amber else m3OnSurface()

    val containerBits = telemetry.outputBitDepth
    val bitRes = snap.negotiatedBitResolution
    val bitText = when {
        containerBits > 0 && bitRes > 0 && bitRes != containerBits ->
            "${containerBits}-bit Container (${bitRes}-bit DAC Resolution)"
        containerBits > 0 ->
            "${containerBits}-bit Container"
        else -> "Negotiating"
    }

    SignalPathNodeCard(
        title = "Output Device (DAC)",
        icon = { GlyphUsbDac(color = deviceTint, modifier = Modifier.size(20.dp)) },
        connectorActive = false,
        connectorColor = deviceTint,
        isLast = true
    ) {
        Text(
            text = "${dacIdentity.displayName} (VID:PID ${dacIdentity.vidPidHex})",
            color = m3OnSurface(),
            fontFamily = ModernSansFont,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${formatSampleRateKhz(telemetry.outputSampleRate)} • $bitText",
            color = m3OnSurfaceVariant(),
            fontFamily = TerminalFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SignalPathBottomSummaryCards(
    track: TrackInfo,
    telemetry: DacTelemetryState,
    isDacConnected: Boolean,
    isPlaying: Boolean,
    currentVolume: Float,
    fallbackHwActive: Boolean,
    snapshotProvider: () -> LiveSignalPathSnapshot,
    hasLiveSnapshotProvider: () -> Boolean
) {
    val snap = snapshotProvider()
    val hasLive = hasLiveSnapshotProvider()
    val status = rememberPathStatus(
        track = track,
        telemetry = telemetry,
        isDacConnected = isDacConnected,
        isPlaying = isPlaying,
        fallbackHwActive = fallbackHwActive,
        snapshot = snap,
        hasLiveSnapshot = hasLive
    )
    val statusColor = resolveSignalPathAccentColor(status)
    val leftTitle = when (status) {
        SignalPathStatus.BIT_PERFECT -> "Bit-Perfect"
        SignalPathStatus.HYBRID_UAC_TRIM -> "Hybrid UAC"
        SignalPathStatus.UNIVERSAL_DSP -> "64-Bit DSP"
        SignalPathStatus.WARNING -> "Clock Warn"
        SignalPathStatus.NO_DAC -> "No DAC"
    }
    val leftSub = when (status) {
        SignalPathStatus.BIT_PERFECT -> "Integer Direct"
        SignalPathStatus.HYBRID_UAC_TRIM -> "HW + Fine-Trim"
        SignalPathStatus.UNIVERSAL_DSP -> "Float64 Pipeline"
        SignalPathStatus.WARNING -> "Check USB Clock"
        SignalPathStatus.NO_DAC -> "Standby"
    }

    val bitPerfectNow = if (hasLive) snap.bitPerfectDirectPass else telemetry.isBitPerfectDirectPass
    val hwActiveNow = if (hasLive) snap.hwVolumeActive else fallbackHwActive
    val volAttenuating = !hwActiveNow && (abs(snap.dspGainDb) >= 0.05f || currentVolume < 0.999f)
    val rightValue = buildActiveDspSummaryTags(
        isDacConnected = isDacConnected,
        isBitPerfectDirectPass = bitPerfectNow,
        isHardwareVolumeActive = hwActiveNow,
        isPeqEffective = snap.peqEffective,
        isCompEffective = snap.compEffective,
        isVolumeAttenuating = volAttenuating
    )
    val rightSub = when {
        !isDacConnected -> "Standby"
        bitPerfectNow -> "Direct Pass"
        else -> "64-Bit Float"
    }
    val rightIconTint = if (!isDacConnected || bitPerfectNow) {
        m3OnSurfaceVariant()
    } else {
        resolveSignalPathAccentColor(SignalPathStatus.UNIVERSAL_DSP)
    }

    val containerHigh = m3SurfaceContainerHigh()
    val leftBg = if (status == SignalPathStatus.NO_DAC) {
        containerHigh
    } else {
        statusColor.copy(alpha = 0.14f).compositeOver(containerHigh)
    }
    val leftBorder = if (status == SignalPathStatus.NO_DAC) {
        m3GlassBorder()
    } else {
        statusColor.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Summary Card: Signal Path
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(leftBg)
                .border(1.dp, leftBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlyphSignalPulse(color = statusColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "SIGNAL PATH",
                    color = m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = leftTitle,
                color = if (status == SignalPathStatus.NO_DAC) m3OnSurfaceVariant() else statusColor,
                fontFamily = ModernSansFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = leftSub,
                color = m3OnSurfaceVariant(),
                fontFamily = TerminalFont,
                fontSize = 10.5.sp
            )
        }

        // Right Summary Card: Active DSP
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(containerHigh)
                .border(1.dp, m3GlassBorder(), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlyphDspStack(color = rightIconTint, modifier = Modifier.size(16.dp))
                Text(
                    text = "ACTIVE DSP",
                    color = m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = rightValue,
                color = m3OnSurface(),
                fontFamily = ModernSansFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = rightSub,
                color = m3OnSurfaceVariant(),
                fontFamily = TerminalFont,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun SignalPathNodeCard(
    title: String,
    icon: @Composable () -> Unit,
    connectorActive: Boolean,
    connectorColor: Color,
    isLast: Boolean,
    titleColor: Color = m3OnSurface(),
    accentTint: Color? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val lineColor = if (connectorActive) {
        connectorColor.copy(alpha = 0.50f)
    } else {
        m3OnSurfaceVariant().copy(alpha = 0.25f)
    }
    val cardBase = m3SurfaceContainerLow()
    val cardBg = if (accentTint != null) accentTint.compositeOver(cardBase) else cardBase

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // Left timeline track (28.dp column: 20.dp glyph + vertical connector arrow)
        Column(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            if (!isLast) {
                Canvas(
                    modifier = Modifier
                        .width(12.dp)
                        .weight(1f)
                        .padding(top = 6.dp)
                ) {
                    val cx = size.width / 2f
                    val topY = 0f
                    val botY = (size.height - 2.dp.toPx()).coerceAtLeast(8f)
                    val strokeW = 1.5.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, topY),
                        end = Offset(cx, botY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    if (botY > 12f) {
                        val arrowW = 3.2.dp.toPx()
                        val arrowH = 4.5.dp.toPx()
                        val arrowPath = Path().apply {
                            moveTo(cx - arrowW, botY - arrowH)
                            lineTo(cx, botY)
                            lineTo(cx + arrowW, botY - arrowH)
                        }
                        drawPath(
                            path = arrowPath,
                            color = lineColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeW,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Node Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.dp, m3GlassBorder(), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontFamily = ModernSansFont,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (trailingAction != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingAction()
                }
            }
            content()
        }
    }
}

@Composable
private fun MicroStatusChip(
    label: String,
    accentColor: Color,
    highlighted: Boolean
) {
    val bg = if (highlighted) {
        accentColor.copy(alpha = 0.14f).compositeOver(m3SurfaceContainerHigh())
    } else {
        m3SubtleFill(0.06f)
    }
    val borderColor = if (highlighted) accentColor.copy(alpha = 0.35f) else m3GlassBorder()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = if (highlighted) accentColor else m3OnSurfaceVariant(),
            fontFamily = TerminalFont,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun resolvePeqPresetLabel(
    peqConfig: PeqConfiguration,
    userPeqPresets: List<UserPeqPreset>
): String {
    val id = peqConfig.selectedPresetId
    if (id == "flat") return "Flat"
    if (id == "custom") return "Custom"
    return userPeqPresets.firstOrNull { it.id == id }?.name ?: id.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }
}
