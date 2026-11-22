package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlyphPlay(color: Color, modifier: Modifier = Modifier.size(24.dp), filled: Boolean = false) {
    val path = if (filled) {
        "M240,128a15.74,15.74,0,0,1-7.6,13.51L88.32,229.65a16,16,0,0,1-16.2.3A15.86,15.86,0,0,1,64,216.13V39.87a15.86,15.86,0,0,1,8.12-13.82,16,16,0,0,1,16.2.3L232.4,114.49A15.74,15.74,0,0,1,240,128Z"
    } else {
        "M232.4,114.49,88.32,26.35a16,16,0,0,0-16.2-.3A15.86,15.86,0,0,0,64,39.87V216.13A15.94,15.94,0,0,0,80,232a16.07,16.07,0,0,0,8.36-2.35L232.4,141.51a15.81,15.81,0,0,0,0-27ZM80,215.94V40l143.83,88Z"
    }
    PhosphorIconCanvas(
        path,
        color = color,
        contentDescription = "Play",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPause(color: Color, modifier: Modifier = Modifier.size(24.dp), filled: Boolean = false) {
    val path = if (filled) {
        "M216,48V208a16,16,0,0,1-16,16H160a16,16,0,0,1-16-16V48a16,16,0,0,1,16-16h40A16,16,0,0,1,216,48ZM96,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16H96a16,16,0,0,0,16-16V48A16,16,0,0,0,96,32Z"
    } else {
        "M200,32H160a16,16,0,0,0-16,16V208a16,16,0,0,0,16,16h40a16,16,0,0,0,16-16V48A16,16,0,0,0,200,32Zm0,176H160V48h40ZM96,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16H96a16,16,0,0,0,16-16V48A16,16,0,0,0,96,32Zm0,176H56V48H96Z"
    }
    PhosphorIconCanvas(
        path,
        color = color,
        contentDescription = "Pause",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPrev(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    PhosphorIconCanvas(
        "M199.81,34a16,16,0,0,0-16.24.43L64,109.23V40a8,8,0,0,0-16,0V216a8,8,0,0,0,16,0V146.77l119.57,74.78A15.95,15.95,0,0,0,208,208.12V47.88A15.86,15.86,0,0,0,199.81,34ZM192,208,64.16,128,192,48.07Z",
        color = color,
        contentDescription = "Previous Track",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphNext(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    PhosphorIconCanvas(
        "M200,32a8,8,0,0,0-8,8v69.23L72.43,34.45A15.95,15.95,0,0,0,48,47.88V208.12a16,16,0,0,0,24.43,13.43L192,146.77V216a8,8,0,0,0,16,0V40A8,8,0,0,0,200,32ZM64,207.93V48.07L191.84,128Z",
        color = color,
        contentDescription = "Next Track",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphShuffle(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M237.66,178.34a8,8,0,0,1,0,11.32l-24,24a8,8,0,0,1-11.32-11.32L212.69,192H200.94a72.12,72.12,0,0,1-58.59-30.15l-41.72-58.4A56.1,56.1,0,0,0,55.06,80H32a8,8,0,0,1,0-16H55.06a72.12,72.12,0,0,1,58.59,30.15l41.72,58.4A56.1,56.1,0,0,0,200.94,176h11.75l-10.35-10.34a8,8,0,0,1,11.32-11.32ZM143,107a8,8,0,0,0,11.16-1.86l1.2-1.67A56.1,56.1,0,0,1,200.94,80h11.75L202.34,90.34a8,8,0,0,0,11.32,11.32l24-24a8,8,0,0,0,0-11.32l-24-24a8,8,0,0,0-11.32,11.32L212.69,64H200.94a72.12,72.12,0,0,0-58.59,30.15l-1.2,1.67A8,8,0,0,0,143,107Zm-30,42a8,8,0,0,0-11.16,1.86l-1.2,1.67A56.1,56.1,0,0,1,55.06,176H32a8,8,0,0,0,0,16H55.06a72.12,72.12,0,0,0,58.59-30.15l1.2-1.67A8,8,0,0,0,113,149Z",
        color = color,
        contentDescription = "Shuffle",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphRepeat(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M24,128A72.08,72.08,0,0,1,96,56H204.69L194.34,45.66a8,8,0,0,1,11.32-11.32l24,24a8,8,0,0,1,0,11.32l-24,24a8,8,0,0,1-11.32-11.32L204.69,72H96a56.06,56.06,0,0,0-56,56,8,8,0,0,1-16,0Zm200-8a8,8,0,0,0-8,8,56.06,56.06,0,0,1-56,56H51.31l10.35-10.34a8,8,0,0,0-11.32-11.32l-24,24a8,8,0,0,0,0,11.32l24,24a8,8,0,0,1,11.32-11.32L51.31,200H160a72.08,72.08,0,0,0,72-72A8,8,0,0,0,224,120Z",
        color = color,
        contentDescription = "Toggle Repeat",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphRepeatOne(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M24,128A72.08,72.08,0,0,1,96,56H204.69L194.34,45.66a8,8,0,0,1,11.32-11.32l24,24a8,8,0,0,1,0,11.32l-24,24a8,8,0,0,1-11.32-11.32L204.69,72H96a56.06,56.06,0,0,0-56,56,8,8,0,0,1-16,0Zm200-8a8,8,0,0,0-8,8,56.06,56.06,0,0,1-56,56H51.31l10.35-10.34a8,8,0,0,0-11.32-11.32l-24,24a8,8,0,0,0,0,11.32l24,24a8,8,0,0,0,11.32-11.32L51.31,200H160a72.08,72.08,0,0,0,72-72A8,8,0,0,0,224,120Zm-80,32V104a8,8,0,0,0-11.58-7.16l-16,8a8,8,0,0,0,7.16,14.32L128,116.94V152a8,8,0,0,0,16,0Z",
        color = color,
        contentDescription = "Repeat One",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphVolume(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M155.51,24.81a8,8,0,0,0-8.42.88L77.25,80H32A16,16,0,0,0,16,96v64a16,16,0,0,0,16,16H77.25l69.84,54.31A8,8,0,0,0,160,224V32A8,8,0,0,0,155.51,24.81ZM32,96H72v64H32ZM144,207.64,88,164.09V91.91l56-43.55Zm54-106.08a40,40,0,0,1,0,52.88,8,8,0,0,1-12-10.58,24,24,0,0,0,0-31.72,8,8,0,0,1,12-10.58ZM248,128a79.9,79.9,0,0,1-20.37,53.34,8,8,0,0,1-11.92-10.67,64,64,0,0,0,0-85.33,8,8,0,1,1,11.92-10.67A79.83,79.83,0,0,1,248,128Z",
        color = color,
        contentDescription = "Volume",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphVolumeMute(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M53.92,34.62A8,8,0,1,0,42.08,45.38L73.55,80H32A16,16,0,0,0,16,96v64a16,16,0,0,0,16,16H77.25l69.84,54.31A8,8,0,0,0,160,224V175.09l42.08,46.29a8,8,0,1,0,11.84-10.76ZM32,96H72v64H32ZM144,207.64,88,164.09V95.9l56,61.6Zm-30.34-135a8,8,0,0,1,1.65-11.21l31.78-24.72A8,8,0,0,1,160,32v74.59a8,8,0,0,1-16,0V48.36L124.87,63.24A8,8,0,0,1,113.66,72.64Zm84.33,28.92a40,40,0,0,1,0,52.88,8,8,0,0,1-12-10.58,24,24,0,0,0,0-31.72,8,8,0,0,1,12-10.58ZM248,128a79.9,79.9,0,0,1-20.37,53.34,8,8,0,0,1-11.92-10.67,64,64,0,0,0,0-85.33,8,8,0,1,1,11.92-10.67A79.83,79.83,0,0,1,248,128Z",
        color = color,
        contentDescription = "Volume Muted",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphBitPerfect(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M208,40H48A16,16,0,0,0,32,56v58.78c0,89.61,75.82,119.34,91,124.39a15.53,15.53,0,0,0,10,0c15.2-5.05,91-34.78,91-124.39V56A16,16,0,0,0,208,40Zm0,74.79c0,78.42-66.35,104.62-80,109.18-13.53-4.51-80-30.69-80-109.18V56H208ZM82.34,141.66a8,8,0,0,1,11.32-11.32L112,148.68l50.34-50.34a8,8,0,0,1,11.32,11.32l-56,56a8,8,0,0,1-11.32,0Z",
        color = color,
        contentDescription = "Bit Perfect",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphLyrics(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M168,16a63.84,63.84,0,0,0-60.92,44.76l-.15.46L30.22,191.91a16,16,0,0,0,2.47,19.38l12,12a16,16,0,0,0,19.38,2.47L194.78,149l.46-.15A64,64,0,1,0,168,16Zm48,64a47.82,47.82,0,0,1-9.22,28.31L139.69,41.22A48,48,0,0,1,216,80ZM56,212,44,200l73.57-73.57,12,12Zm100.34-44.34-12-12,51.09-29.93a48.29,48.29,0,0,1-9.16,9.16ZM105.66,150.34a8,8,0,0,1-11.32,11.32l-16-16a8,8,0,0,1,11.32-11.32Z",
        color = color,
        contentDescription = "Lyrics",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphNowPlaying(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M56,96v64a8,8,0,0,1-16,0V96a8,8,0,0,1,16,0ZM88,24a8,8,0,0,0-8,8V224a8,8,0,0,0,16,0V32A8,8,0,0,0,88,24Zm40,32a8,8,0,0,0-8,8V192a8,8,0,0,0,16,0V64A8,8,0,0,0,128,56Zm40,32a8,8,0,0,0-8,8v64a8,8,0,0,0,16,0V96A8,8,0,0,0,168,88Zm40-16a8,8,0,0,0-8,8v96a8,8,0,0,0,16,0V80A8,8,0,0,0,208,72Z",
        color = color,
        contentDescription = "Now Playing",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphEqualizer(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M120,40v16H40a8,8,0,0,0,0,16h80V88a8,8,0,0,0,16,0V40a8,8,0,0,0-16,0Zm96,24H152a8,8,0,0,0,0,16h64a8,8,0,0,0,0-16ZM40,136h64a8,8,0,0,0,0-16H40a8,8,0,0,0,0,16Zm176-16H120v-16a8,8,0,0,0-16,0v48a8,8,0,0,0,16,0V136h96a8,8,0,0,0,0-16ZM80,168v-16a8,8,0,0,0-16,0v16H40a8,8,0,0,0,0,16H64v16a8,8,0,0,0,16,0V168Zm136,8H96a8,8,0,0,0,0,16H216a8,8,0,0,0,0-16Z",
        color = color,
        contentDescription = "Parametric Equalizer",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphFilterLowShelf(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M28,88 H92 C124,88 132,168 164,168 H228",
        color = color,
        contentDescription = "Low Shelf Filter",
        modifier = modifier,
        filled = false,
        strokeWidth = 20f
    )
}

@Composable
fun GlyphFilterPeaking(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M28,168 H64 C94,168 98,80 128,80 C158,80 162,168 192,168 H228",
        color = color,
        contentDescription = "Peaking Filter",
        modifier = modifier,
        filled = false,
        strokeWidth = 20f
    )
}

@Composable
fun GlyphFilterHighShelf(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M28,168 H92 C124,168 132,88 164,88 H228",
        color = color,
        contentDescription = "High Shelf Filter",
        modifier = modifier,
        filled = false,
        strokeWidth = 20f
    )
}
