package com.yuka.musicplayer.ui.views

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.RepeatMode
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.dialogs.VolumeModalSheet
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.m3SurfaceContainer
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun TrackControlsSection(
    track: TrackInfo,
    playbackPosition: Double = 0.0,
    isPlaying: Boolean,
    currentVolume: Float,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    priorityQueueSize: Int,
    isHardwareVolumeActive: Boolean,
    isHardwareVolumeSupported: Boolean = false,
    isHardwareVolumeEnabled: Boolean = false,
    onToggleHardwareVolume: ((Boolean) -> Unit)? = null,
    dynamicColor: Color,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    positionProvider: () -> Double = { playbackPosition },
    onTogglePlay: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onSeekTo: (Double) -> Unit,
    outputBitDepth: Int = 0,
    outputSampleRate: Int = 0,
    isDacConnected: Boolean = false,
    isPeqActive: Boolean = false,
    onOpenPeq: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showVolumeModal by remember { mutableStateOf(false) }
    var showAudioSpecsModal by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val volumePulseAnim = remember { Animatable(0f) }
    val volumeScope = rememberCoroutineScope()
    val triggerVolumePulse: (Float) -> Unit = { direction ->
        volumeScope.launch {
            volumePulseAnim.snapTo(direction)
            volumePulseAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f)
            )
        }
    }
    val animatedVolumeFill by animateFloatAsState(
        targetValue = currentVolume.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 600f),
        label = "animatedVolumeFill"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 2. Left-Aligned Track Metadata (Bold Title with Inline Audio Info Button & Subtle Artist/Album)
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                Text(
                    text = track.title,
                    color = com.yuka.musicplayer.ui.theme.m3OnSurface(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            delayMillis = 1500,
                            velocity = 28.dp,
                            spacing = androidx.compose.foundation.MarqueeSpacing(36.dp)
                        )
                )

                TactileBox(
                    onClick = { showAudioSpecsModal = true },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(dynamicColor.copy(alpha = 0.16f))
                ) {
                    GlyphInfo(
                        color = dynamicColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val artistText = if (track.album.isNotEmpty() && track.album != "Unknown Album") {
                "${track.artist}  •  ${track.album}"
            } else {
                track.artist
            }

            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            Text(
                text = artistText,
                color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                fontSize = 15.sp,
                fontFamily = ModernSansFont,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        delayMillis = 1500,
                        velocity = 24.dp,
                        spacing = androidx.compose.foundation.MarqueeSpacing(36.dp)
                    )
            )
        }

        // 3. M3 Precision Scrubber & Timecodes (direct beneath title/artist)
        IsolatedPrecisionScrubber(
            positionProvider = positionProvider,
            durationSeconds = track.durationSeconds,
            isPlaying = isPlaying,
            dynamicColor = dynamicColor,
            onSeekTo = onSeekTo,
            trackKey = track.file.absolutePath,
            modifier = Modifier.fillMaxWidth()
        )

        // 4. Centered Audiophile Telemetry String (Single-Line Dynamic Engine Readout with Tap-to-Cycle)
        var telemetryCycleIndex by remember { mutableIntStateOf(0) }
        val engineArchReadout = if (isHardwareVolumeActive) "32-BIT HARDWARE CHIP" else "64-BIT BIT-PERFECT DSP"
        val outKhzStr = if (outputSampleRate > 0) {
            if (outputSampleRate % 1000 == 0) "${outputSampleRate / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", outputSampleRate / 1000.0)
        } else null
        val inKhzStr = if (track.sampleRateHz > 0) {
            if (track.sampleRateHz % 1000 == 0) "${track.sampleRateHz / 1000} kHz" else String.format(java.util.Locale.US, "%.1f kHz", track.sampleRateHz / 1000.0)
        } else null

        val line1 = buildString {
            if (isDacConnected && outputBitDepth > 0 && outKhzStr != null) {
                val inBit = if (track.bitDepth > 0) "${track.bitDepth}-BIT / " else ""
                val inRate = inKhzStr ?: ""
                append("IN: $inBit$inRate  →  OUT: $outputBitDepth-BIT / $outKhzStr (DIRECT USB)")
            } else {
                append(track.codec.uppercase())
                if (track.bitDepth > 0) append("  •  ${track.bitDepth}-BIT")
                if (track.bitrateKbps > 0) append("  •  ${track.bitrateKbps} KBPS")
                append("  •  $engineArchReadout")
                if (track.isMqa) append("  •  MQA")
            }
        }
        val line2 = buildString {
            append(engineArchReadout)
            if (isPeqActive) {
                append("  •  PEQ: ON (10-BAND)")
            } else {
                append("  •  PEQ: OFF  •  DIRECT USB  •  BIT-PERFECT")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { telemetryCycleIndex++ }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = if (telemetryCycleIndex % 2 == 0) line1 else line2,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "telemetryCycle"
            ) { textToDisplay ->
                Text(
                    text = textToDisplay,
                    color = com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                    fontFamily = TerminalFont,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.05.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 5. M3 Horizontal Transport Controls Row (Shuffle, Prev, Play/Pause Hero 68dp, Next, Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle Button
            TactileBox(
                onClick = onToggleShuffle,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isShuffleEnabled) dynamicColor.copy(alpha = 0.22f) else Color.Transparent)
            ) {
                GlyphShuffle(
                    color = if (isShuffleEnabled) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant().copy(alpha = 0.70f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Prev Button (permanently circular 48dp)
            TactileBox(
                onClick = onPlayPrev,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(m3SurfaceContainerHigh())
            ) {
                GlyphPrev(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(20.dp))
            }

            // Hero Play/Pause Button (permanently circular 68dp)
            TactileBox(
                onClick = onTogglePlay,
                pressedScale = 0.92f,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(dynamicColor)
            ) {
                val isDarkTheme = com.yuka.musicplayer.ui.theme.LocalIsDarkTheme.current
                val iconColor = if (!isDarkTheme || com.yuka.musicplayer.util.ColorContrastHelper.calculateLuminance(dynamicColor) <= 0.45f) {
                    Color.White
                } else {
                    Color(0xFF0E0D13)
                }
                if (isPlaying) {
                    GlyphPause(color = iconColor, modifier = Modifier.size(26.dp))
                } else {
                    Box(modifier = Modifier.padding(start = 3.dp)) {
                        GlyphPlay(color = iconColor, modifier = Modifier.size(26.dp))
                    }
                }
            }

            // Next Button (permanently circular 48dp)
            TactileBox(
                onClick = onPlayNext,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(m3SurfaceContainerHigh())
            ) {
                GlyphNext(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(20.dp))
            }

            // Repeat Button
            val isRepeatActive = repeatMode != RepeatMode.OFF
            TactileBox(
                onClick = onCycleRepeat,
                pressedScale = 0.88f,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isRepeatActive) dynamicColor.copy(alpha = 0.22f) else Color.Transparent)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (repeatMode == RepeatMode.ONE) {
                        GlyphRepeatOne(
                            color = dynamicColor,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        GlyphRepeat(
                            color = if (isRepeatActive) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant().copy(alpha = 0.70f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 6. Symmetrical Bottom Utility Action Row (Interactive Volume Fill Capsule & Queue Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Volume Pill with dynamic fill bar
            val isBitPerfect = currentVolume >= 0.999f
            val volPercent = (currentVolume * 100).roundToInt()
            val pulseMag = kotlin.math.abs(volumePulseAnim.value)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .graphicsLayer {
                        scaleX = 1f + (0.028f * pulseMag)
                        scaleY = 1f + (0.045f * pulseMag)
                        translationX = volumePulseAnim.value * 4.dp.toPx()
                    }
                    .clip(RoundedCornerShape(25.dp))
                    .background(m3SurfaceContainer())
            ) {
                // Background subtle fill tracking volume level
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedVolumeFill)
                        .fillMaxHeight()
                        .background(dynamicColor.copy(alpha = 0.16f + 0.12f * pulseMag))
                )

                // Foreground content with Steppers and Center Level
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TactileBox(
                        onClick = {
                            val newVol = ((volPercent - 1).coerceIn(0, 100)) / 100f
                            onVolumeChange(newVol)
                            triggerVolumePulse(-1f)
                        },
                        pressedScale = 0.85f,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(0.08f))
                    ) {
                        GlyphMinus(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(16.dp))
                    }

                    TactileBox(
                        onClick = {
                            showVolumeModal = true
                        },
                        onLongClick = {
                            showVolumeModal = true
                        },
                        pressedScale = 0.92f,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (volPercent == 0 && !isBitPerfect) {
                                GlyphVolumeMute(color = com.yuka.musicplayer.ui.theme.m3OnSurface().copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            } else {
                                GlyphVolume(color = if (isBitPerfect) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBitPerfect) "0 dBFS" else "$volPercent%",
                                color = if (isBitPerfect) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurface(),
                                fontFamily = TerminalFont,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TactileBox(
                        onClick = {
                            val newVol = ((volPercent + 1).coerceIn(0, 100)) / 100f
                            onVolumeChange(newVol)
                            triggerVolumePulse(1f)
                        },
                        pressedScale = 0.85f,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(com.yuka.musicplayer.ui.theme.m3SubtleFill(0.08f))
                    ) {
                        GlyphAdd(color = com.yuka.musicplayer.ui.theme.m3OnSurface(), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Lyrics Toggle Button (50dp height aligned with Volume Pill & Queue Button)
            TactileBox(
                onClick = onToggleLyrics,
                pressedScale = 0.90f,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        if (showLyrics) dynamicColor.copy(alpha = 0.25f)
                        else m3SurfaceContainer()
                    )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphLyrics(
                        color = if (showLyrics) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // PEQ Button (50dp height aligned with Volume Pill, Lyrics, and Queue)
            if (onOpenPeq != null) {
                TactileBox(
                    onClick = onOpenPeq,
                    pressedScale = 0.90f,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            if (isPeqActive) dynamicColor.copy(alpha = 0.25f)
                            else m3SurfaceContainer()
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphEqualizer(
                            color = if (isPeqActive) dynamicColor else com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Queue Button (50dp height aligned with Volume Pill)
            TactileBox(
                onClick = onOpenQueue,
                pressedScale = 0.90f,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(m3SurfaceContainer())
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GlyphQueue(color = dynamicColor, modifier = Modifier.size(20.dp))
                    if (priorityQueueSize > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(dynamicColor)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$priorityQueueSize",
                                color = Color(0xFF0E0D13),
                                fontSize = 9.sp,
                                fontFamily = TerminalFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    VolumeModalSheet(
        visible = showVolumeModal,
        currentVolume = currentVolume,
        isHardwareVolumeActive = isHardwareVolumeActive,
        isHardwareVolumeSupported = isHardwareVolumeSupported,
        isHardwareVolumeEnabled = isHardwareVolumeEnabled,
        onToggleHardwareVolume = onToggleHardwareVolume,
        onVolumeChange = onVolumeChange,
        onDismissRequest = { showVolumeModal = false }
    )

    com.yuka.musicplayer.ui.dialogs.AudioSpecsAndLogsModalSheet(
        visible = showAudioSpecsModal,
        track = track,
        isHardwareVolumeActive = isHardwareVolumeActive,
        onDismissRequest = { showAudioSpecsModal = false }
    )
}

@Composable
private fun IsolatedPrecisionScrubber(
    positionProvider: () -> Double,
    durationSeconds: Double,
    isPlaying: Boolean,
    dynamicColor: Color,
    onSeekTo: (Double) -> Unit,
    trackKey: String,
    modifier: Modifier = Modifier
) {
    PrecisionScrubber(
        playbackPosition = positionProvider(),
        durationSeconds = durationSeconds,
        isPlaying = isPlaying,
        dynamicColor = dynamicColor,
        onSeekTo = onSeekTo,
        trackKey = trackKey,
        modifier = modifier
    )
}
