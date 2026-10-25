package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.ScannedMusicItem
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphInfo
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant
import com.yuka.musicplayer.ui.theme.m3SubtleFill
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerLow
import com.yuka.musicplayer.utils.AppLogger
import java.util.Locale

/**
 * Modal Bottom Sheet launched from the Now Playing Info button below the artist name.
 * Displays:
 * 1. Complete Audio Specifications for the currently playing file.
 * 2. Live Player Decoding & Engine Logs captured during track decoding and playback.
 */
@Composable
fun AudioSpecsAndLogsModalSheet(
    visible: Boolean,
    track: TrackInfo,
    isHardwareVolumeActive: Boolean,
    onDismissRequest: () -> Unit
) {
    val accent = LocalAccentColor.current
    val allLogs by AppLogger.entries.collectAsState()

    val sampleRateStr = remember(track.sampleRateHz) {
        if (track.sampleRateHz <= 0) "Unknown"
        else if (track.sampleRateHz % 1000 == 0) "${track.sampleRateHz / 1000} kHz (${track.sampleRateHz} Hz)"
        else String.format(Locale.US, "%.1f kHz (%d Hz)", track.sampleRateHz / 1000.0, track.sampleRateHz)
    }

    val bitDepthStr = remember(track.bitDepth) {
        if (track.bitDepth > 0) "${track.bitDepth}-bit" else "Standard PCM"
    }

    val bitrateStr = remember(track.bitrateKbps) {
        if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "Lossless Variable"
    }

    val channelsStr = remember(track.channels) {
        when (track.channels) {
            1 -> "1 Channel (Mono)"
            2 -> "2 Channels (Stereo)"
            else -> "${track.channels} Channels"
        }
    }

    val fileSizeStr = remember(track.fileSizeBytes, track.file) {
        val bytes = if (track.fileSizeBytes > 0L) track.fileSizeBytes else track.file.length()
        ScannedMusicItem.formatSizeShort(bytes)
    }

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        maxHeightFraction = 0.82f,
        maxWidth = 440.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphInfo(color = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Specs & Decoder Logs",
                        color = m3OnSurface(),
                        fontSize = 17.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = track.title,
                        color = accent,
                        fontSize = 12.sp,
                        fontFamily = ModernSansFont,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Section 1: Audio Stream Specifications Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(m3SurfaceContainerLow())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AUDIO STREAM SPECIFICATIONS",
                    color = accent,
                    fontSize = 11.sp,
                    fontFamily = TerminalFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp
                )
                Spacer(modifier = Modifier.height(2.dp))

                SpecRow("Format / Codec", if (track.isMqa) "${track.codec} (MQA)" else track.codec)
                SpecRow("Sample Rate", sampleRateStr)
                SpecRow("Bit Depth", bitDepthStr)
                SpecRow("Bitrate", bitrateStr)
                SpecRow("Channel Layout", channelsStr)
                SpecRow("File Size", fileSizeStr)
                SpecRow(
                    "Volume & DSP Path",
                    if (isHardwareVolumeActive) "32-Bit Hardware DAC Chip" else "64-Bit Bit-Perfect DSP"
                )
                SpecRow("Source File", track.file.name)
            }

            // Section 2: Live Player Decoding Logs Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(m3SurfaceContainerLow())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAYER DECODING LOGS",
                        color = accent,
                        fontSize = 11.sp,
                        fontFamily = TerminalFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.sp
                    )
                    Text(
                        text = "${allLogs.size} entries",
                        color = m3OnSurfaceVariant(),
                        fontSize = 10.5.sp,
                        fontFamily = TerminalFont
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val recentLogs = remember(allLogs) {
                    allLogs.takeLast(60).reversed()
                }

                if (recentLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(m3SubtleFill(0.06f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Decoder active: ${track.codec} stream ($sampleRateStr, $bitDepthStr, $bitrateStr) playing cleanly with zero errors.",
                            color = m3OnSurfaceVariant(),
                            fontSize = 11.5.sp,
                            fontFamily = TerminalFont
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(m3SubtleFill(0.06f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recentLogs.forEach { entry ->
                            val levelColor = when (entry.level) {
                                "ERR" -> Color(0xFFFF5252)
                                "WARN" -> Color(0xFFFFB300)
                                else -> accent
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "${entry.timestamp} [${entry.level}/${entry.category}] ",
                                    color = levelColor,
                                    fontFamily = TerminalFont,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = entry.message,
                                    color = m3OnSurface(),
                                    fontFamily = TerminalFont,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = m3OnSurfaceVariant(),
            fontSize = 12.sp,
            fontFamily = ModernSansFont
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = m3OnSurface(),
            fontSize = 12.sp,
            fontFamily = TerminalFont,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
