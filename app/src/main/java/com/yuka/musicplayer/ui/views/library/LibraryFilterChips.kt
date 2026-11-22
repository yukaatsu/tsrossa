package com.yuka.musicplayer.ui.views

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.GlyphClose
import com.yuka.musicplayer.ui.components.GlyphRefresh
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.glassMorphic
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3SubtleFill

@Composable
internal fun FilterChipItem(
    label: String,
    activeValue: String?,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    val accent = LocalAccentColor.current
    val isActive = activeValue != null

    Row(
        modifier = Modifier
            .glassMorphic(
                backgroundColor = if (isActive) accent.copy(alpha = 0.18f) else m3SubtleFill(alpha = 0.08f),
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isActive) "$label: $activeValue" else label,
            color = if (isActive) accent else m3OnSurface(),
            fontSize = 11.sp,
            fontFamily = ModernSansFont,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isActive) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onClear() },
                contentAlignment = Alignment.Center
            ) {
                GlyphClose(color = accent, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
internal fun ScanningRefreshIcon(
    isScanning: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (isScanning) {
        val infiniteTransition = rememberInfiniteTransition(label = "rescanSpin")
        val rescanRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = LinearEasing)
            ),
            label = "rescanRotation"
        )
        GlyphRefresh(
            color = accent,
            modifier = modifier.graphicsLayer {
                rotationZ = rescanRotation
            }
        )
    } else {
        GlyphRefresh(
            color = accent,
            modifier = modifier
        )
    }
}

@Composable
internal fun LibraryFilterChipsRow(
    filterArtist: String?,
    filterGenre: String?,
    filterAlbum: String?,
    filterYear: String?,
    filterFormat: String?,
    onOpenCategory: (String) -> Unit,
    onClearArtist: () -> Unit,
    onClearGenre: () -> Unit,
    onClearAlbum: () -> Unit,
    onClearYear: () -> Unit,
    onClearFormat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChipItem(
            label = "Artist",
            activeValue = filterArtist,
            onClick = { onOpenCategory("Artist") },
            onClear = onClearArtist
        )
        FilterChipItem(
            label = "Genre",
            activeValue = filterGenre,
            onClick = { onOpenCategory("Genre") },
            onClear = onClearGenre
        )
        FilterChipItem(
            label = "Album",
            activeValue = filterAlbum,
            onClick = { onOpenCategory("Album") },
            onClear = onClearAlbum
        )
        FilterChipItem(
            label = "Year",
            activeValue = filterYear,
            onClick = { onOpenCategory("Year") },
            onClear = onClearYear
        )
        FilterChipItem(
            label = "Format",
            activeValue = filterFormat,
            onClick = { onOpenCategory("Format") },
            onClear = onClearFormat
        )
    }
}
