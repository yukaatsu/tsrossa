package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlyphFolder(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M216,72H131.31L104,44.69A15.86,15.86,0,0,0,92.69,40H40A16,16,0,0,0,24,56V200.62A15.4,15.4,0,0,0,39.38,216H216.89A15.13,15.13,0,0,0,232,200.89V88A16,16,0,0,0,216,72ZM40,56H92.69l16,16H40ZM216,200H40V88H216Z",
        color = color,
        contentDescription = "Folder",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphFolderOpen(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M245,110.64A16,16,0,0,0,232,104H216V88a16,16,0,0,0-16-16H130.67L102.94,51.2a16.14,16.14,0,0,0-9.6-3.2H40A16,16,0,0,0,24,64V208h0a8,8,0,0,0,8,8H211.1a8,8,0,0,0,7.59-5.47l28.49-85.47A16.05,16.05,0,0,0,245,110.64ZM93.34,64,123.2,86.4A8,8,0,0,0,128,88h72v16H69.77a16,16,0,0,0-15.18,10.94L40,158.7V64Zm112,136H43.1l26.67-80H232Z",
        color = color,
        contentDescription = "Open Folder",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSort(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M117.66,170.34a8,8,0,0,1,0,11.32l-32,32a8,8,0,0,1-11.32,0l-32-32a8,8,0,0,1,11.32-11.32L72,188.69V48a8,8,0,0,1,16,0V188.69l18.34-18.35A8,8,0,0,1,117.66,170.34Zm96-96-32-32a8,8,0,0,0-11.32,0l-32,32a8,8,0,0,0,11.32,11.32L168,67.31V208a8,8,0,0,0,16,0V67.31l18.34,18.35a8,8,0,0,0,11.32-11.32Z",
        color = color,
        contentDescription = "Sort",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphQueue(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M32,64a8,8,0,0,1,8-8H216a8,8,0,0,1,0,16H40A8,8,0,0,1,32,64ZM136,120H40a8,8,0,0,0,0,16h96a8,8,0,0,0,0-16Zm0,64H40a8,8,0,0,0,0,16h96a8,8,0,0,0,0-16Zm107.66-29.34-48-32A8,8,0,0,0,184,128v64a8,8,0,0,0,11.66,7.11l48-32a8,8,0,0,0,0-14.22ZM200,177.23V142.77L225.85,160Z",
        color = color,
        contentDescription = "Queue",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphAdd(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M224,128a8,8,0,0,1-8,8H136v80a8,8,0,0,1-16,0V136H40a8,8,0,0,1,0-16h80V40a8,8,0,0,1,16,0v80h80A8,8,0,0,1,224,128Z",
        color = color,
        contentDescription = "Add",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphMinus(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M224,128a8,8,0,0,1-8,8H40a8,8,0,0,1,0-16H216A8,8,0,0,1,224,128Z",
        color = color,
        contentDescription = "Minus",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphDelete(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M216,48H176V40a24,24,0,0,0-24-24H104A24,24,0,0,0,80,40v8H40a8,8,0,0,0,0,16h8V208a16,16,0,0,0,16,16H192a16,16,0,0,0,16-16V64h8a8,8,0,0,0,0-16ZM96,40a8,8,0,0,1,8-8h48a8,8,0,0,1,8,8v8H96Zm96,168H64V64H192ZM112,104v64a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Zm48,0v64a8,8,0,0,1-16,0V104a8,8,0,0,1,16,0Z",
        color = color,
        contentDescription = "Delete",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphMusicNote(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M210.3,56.34l-80-24A8,8,0,0,0,120,40V148.26A47.92,47.92,0,1,0,136,184V98.75l69.7,20.91A8,8,0,0,0,216,112V64A8,8,0,0,0,210.3,56.34ZM88,216a32,32,0,1,1,32-32A32,32,0,0,1,88,216ZM200,101.25l-64-19.2V50.75l64,19.2Z",
        color = color,
        contentDescription = "Music",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphAlbum(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm0-152a64.07,64.07,0,0,0-64,64,8,8,0,0,0,16,0,48.05,48.05,0,0,1,48-48,8,8,0,0,0,0-16Zm64,64a8,8,0,0,0-16,0,48.05,48.05,0,0,1-48,48,8,8,0,0,0,0,16A64.07,64.07,0,0,0,192,128Zm-64-24a24,24,0,1,0,24,24A24,24,0,0,0,128,104Zm0,32a8,8,0,1,1,8-8A8,8,0,0,1,128,136Z",
        color = color,
        contentDescription = "Album",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphLibrary(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M231.65,194.55,198.46,36.75a16,16,0,0,0-19-12.39L132.65,34.42a16.08,16.08,0,0,0-12.3,19l33.19,157.8A16,16,0,0,0,169.16,224a16.25,16.25,0,0,0,3.38-.36l46.81-10.06A16.09,16.09,0,0,0,231.65,194.55ZM136,50.15l46.82-10.06,3.34,15.9L139.33,66Zm10,47.69,46.81-10.06,13.38,63.58L159.35,161.42Zm23.2,110.22-6.51-30.94,46.81-10.06,6.51,30.94ZM104,32H56A16,16,0,0,0,40,48V208a16,16,0,0,0,16,16h48a16,16,0,0,0,16-16V48A16,16,0,0,0,104,32ZM56,48h48V64H56Zm0,32h48v96H56Zm48,128H56V192h48v16Z",
        color = color,
        contentDescription = "Library",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPlaylists(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M32,64a8,8,0,0,1,8-8H216a8,8,0,0,1,0,16H40A8,8,0,0,1,32,64Zm128,56H40a8,8,0,0,0,0,16H160a8,8,0,0,0,0-16Zm-48,64H40a8,8,0,0,0,0,16h72a8,8,0,0,0,0-16Zm130.3-55.66-40-12A8,8,0,0,0,192,124v53.71A32,32,0,1,0,208,200V134.81l29.7,8.91a8,8,0,1,0,4.6-15.32ZM176,216a16,16,0,1,1,16-16A16,16,0,0,1,176,216Z",
        color = color,
        contentDescription = "Playlists",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphCheck(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M229.66,77.66l-128,128a8,8,0,0,1-11.32,0l-56-56a8,8,0,0,1,11.32-11.32L96,188.69,218.34,66.34a8,8,0,0,1,11.32,11.32Z",
        color = color,
        contentDescription = "Check",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphFilter(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M230.6,49.53A15.81,15.81,0,0,0,216,40H40A16,16,0,0,0,28.19,66.76l.08.09L96,139.17V216a16,16,0,0,0,24.87,13.32l32-21.34A16,16,0,0,0,160,194.66V139.17l67.74-72.32.08-.09A15.8,15.8,0,0,0,230.6,49.53ZM40,56h0Zm106.18,74.58A8,8,0,0,0,144,136v58.66L112,216V136a8,8,0,0,0-2.18-5.47L40,56H216Z",
        color = color,
        contentDescription = "Filter",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphEdit(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    PhosphorIconCanvas(
        "M227.31,73.37,182.63,28.68a16,16,0,0,0-22.63,0L36.69,152A15.86,15.86,0,0,0,32,163.31V208a16,16,0,0,0,16,16H92.69A15.86,15.86,0,0,0,104,219.31L227.31,96a16,16,0,0,0,0-22.63ZM92.69,208H48V163.31l88-88L180.69,120ZM192,108.68,147.31,64l24-24L216,84.68Z",
        color = color,
        contentDescription = "Edit",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSparkle(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M208,144a15.78,15.78,0,0,1-10.42,14.94l-51.65,19-19,51.61a15.92,15.92,0,0,1-29.88,0L78,178l-51.62-19a15.92,15.92,0,0,1,0-29.88l51.65-19,19-51.61a15.92,15.92,0,0,1,29.88,0l19,51.65,51.61,19A15.78,15.78,0,0,1,208,144ZM152,48h16V64a8,8,0,0,0,16,0V48h16a8,8,0,0,0,0-16H184V16a8,8,0,0,0-16,0V32H152a8,8,0,0,0,0,16Zm88,32h-8V72a8,8,0,0,0-16,0v8h-8a8,8,0,0,0,0,16h8v8a8,8,0,0,0,16,0V96h8a8,8,0,0,0,0-16Z",
        color = color,
        contentDescription = "Highlight",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphChatTeardropText(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M132,24A100.11,100.11,0,0,0,32,124v84a16,16,0,0,0,16,16h84a100,100,0,0,0,0-200Zm0,184H48V124a84,84,0,1,1,84,84Zm40-100a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h68A8,8,0,0,1,172,108Zm0,32a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h68A8,8,0,0,1,172,140Z",
        color = color,
        contentDescription = "Message",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphFloppyDisk(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M219.31,72,184,36.69A15.86,15.86,0,0,0,172.69,32H48A16,16,0,0,0,32,48V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V83.31A15.86,15.86,0,0,0,219.31,72ZM168,208H88V152h80Zm40,0H184V152a16,16,0,0,0-16-16H88a16,16,0,0,0-16,16v56H48V48H172.69L208,83.31ZM160,72a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h56A8,8,0,0,1,160,72Z",
        color = color,
        contentDescription = "Save",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphShareNetwork(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M176,160a39.89,39.89,0,0,0-28.62,12.09l-46.1-29.63a39.8,39.8,0,0,0,0-28.92l46.1-29.63a40,40,0,1,0-8.66-13.45l-46.1,29.63a40,40,0,1,0,0,55.82l46.1,29.63A40,40,0,1,0,176,160Zm0-128a24,24,0,1,1-24,24A24,24,0,0,1,176,32ZM64,152a24,24,0,1,1,24-24A24,24,0,0,1,64,152Zm112,72a24,24,0,1,1,24-24A24,24,0,0,1,176,224Z",
        color = color,
        contentDescription = "Share",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphDownloadSimple(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M224,144v64a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V144a8,8,0,0,1,16,0v56H208V144a8,8,0,0,1,16,0Zm-101.66,5.66a8,8,0,0,0,11.32,0l40-40a8,8,0,0,0-11.32-11.32L136,124.69V32a8,8,0,0,0-16,0v92.69L93.66,98.34a8,8,0,0,0-11.32,11.32Z",
        color = color,
        contentDescription = "Import",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphUploadSimple(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M224,144v64a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V144a8,8,0,0,1,16,0v56H208V144a8,8,0,0,1,16,0ZM88,80l32-32V144a8,8,0,0,0,16,0V48l32,32a8,8,0,0,0,11.32-11.32l-48-48a8,8,0,0,0-11.32,0l-48,48A8,8,0,0,0,88,80Z",
        color = color,
        contentDescription = "Export",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphBookmark(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M184,32H72A16,16,0,0,0,56,48V224a8,8,0,0,0,12.24,6.78L128,193.43l59.77,37.35A8,8,0,0,0,200,224V48A16,16,0,0,0,184,32Zm0,177.57-51.77-32.35a8,8,0,0,0-8.48,0L72,209.57V48H184Z",
        color = color,
        contentDescription = "Preset",
        modifier = modifier,
        filled = true
    )
}
