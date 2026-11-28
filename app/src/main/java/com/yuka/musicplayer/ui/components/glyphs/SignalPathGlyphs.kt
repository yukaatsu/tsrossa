package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Custom Canvas Phosphor-style glyphs for the Now Playing Quality Badge & Signal Path Modal.
 * Built on the official 256x256 Phosphor viewBox grid via [PhosphorIconCanvas].
 * Existing glyphs ([GlyphChevronRight], [GlyphEqualizer], [GlyphVolume], [GlyphVolumeMute],
 * [GlyphWarning], [GlyphInfo]) are reused directly instead of duplicated.
 */

@Composable
fun GlyphAudioFile(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M56,224 H200 A8,8 0 0 0 208,216 V88 L152,32 H56 A8,8 0 0 0 48,40 V216 A8,8 0 0 0 56,224 Z",
        "M152,32 V88 H208",
        "M96,152 V176 M124,132 V188 M152,140 V180 M176,154 V166",
        color = color,
        contentDescription = "Source Audio File",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphDecoderCpu(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M64,56 H192 A8,8 0 0 1 200,64 V192 A8,8 0 0 1 192,200 H64 A8,8 0 0 1 56,192 V64 A8,8 0 0 1 64,56 Z",
        "M96,96 H160 V160 H96 Z",
        "M96,24 V56 M128,24 V56 M160,24 V56 M96,200 V232 M128,200 V232 M160,200 V232 M24,96 H56 M24,128 H56 M24,160 H56 M200,96 H232 M200,128 H232 M200,160 H232",
        color = color,
        contentDescription = "Decoder and Buffer",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphSealCheck(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M96,50 Q128,28 160,50 Q199,57 206,96 Q228,128 206,160 Q199,199 160,206 Q128,228 96,206 Q57,199 50,160 Q28,128 50,96 Q57,57 96,50 Z",
        "M92,132 L116,156 L168,104",
        color = color,
        contentDescription = "Bit-Perfect Verified",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphDynamicsWave(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M24,128 C60,44 92,44 128,128 C164,212 196,212 232,128",
        color = color,
        contentDescription = "Dynamics Compressor",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphDitherSparkle(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M112,32 Q112,112 192,112 Q112,112 112,192 Q112,112 32,112 Q112,112 112,32 Z",
        "M192,160 V224 M160,192 H224",
        "M192,40 V72 M176,56 H208",
        color = color,
        contentDescription = "Limiter and Dither",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphDirectUsbLink(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M224,32 L180,76 M156,52 L204,100 M164,60 L136,88 A24,24 0 0 0 168,120 L196,92",
        "M112,144 L144,112",
        "M32,224 L76,180 M52,156 L100,204 M60,164 L88,136 A24,24 0 0 1 120,168 L92,196",
        color = color,
        contentDescription = "Direct USB Transport",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphUsbDac(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M128,204 V36 M106,58 L128,34 L150,58",
        "M128,160 L80,126 V96",
        "M80,82 m-14,0 a14,14 0 1,0 28,0 a14,14 0 1,0 -28,0",
        "M128,136 L176,102 V76",
        "M164,52 H188 V76 H164 Z",
        "M128,218 m-14,0 a14,14 0 1,0 28,0 a14,14 0 1,0 -28,0",
        color = color,
        contentDescription = "USB DAC Output Device",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphSignalPulse(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M24,128 H64 L96,48 L152,208 L184,128 H232",
        color = color,
        contentDescription = "Signal Path",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}

@Composable
fun GlyphDspStack(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M32,80 L128,32 L224,80 L128,128 Z",
        "M32,128 L128,176 L224,128",
        "M32,176 L128,224 L224,176",
        color = color,
        contentDescription = "Active DSP Stack",
        modifier = modifier,
        filled = false,
        strokeWidth = 16f
    )
}
