package com.yuka.musicplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlyphBolt(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    PhosphorIconCanvas(
        "M215.79,118.17a8,8,0,0,0-5-5.66L153.18,90.9l14.66-73.33a8,8,0,0,0-13.69-7l-112,120a8,8,0,0,0,3,13l57.63,21.61L88.16,238.43a8,8,0,0,0,13.69,7l112-120A8,8,0,0,0,215.79,118.17ZM109.37,214l10.47-52.38a8,8,0,0,0-5-9.06L62,132.71l84.62-90.66L136.16,94.43a8,8,0,0,0,5,9.06l52.8,19.8Z",
        color = color,
        contentDescription = "Bolt",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphLock(color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    PhosphorIconCanvas(
        "M208,80H176V56a48,48,0,0,0-96,0V80H48A16,16,0,0,0,32,96V208a16,16,0,0,0,16,16H208a16,16,0,0,0,16-16V96A16,16,0,0,0,208,80ZM96,56a32,32,0,0,1,64,0V80H96ZM208,208H48V96H208V208Zm-68-56a12,12,0,1,1-12-12A12,12,0,0,1,140,152Z",
        color = color,
        contentDescription = "Hardware Volume Locked",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphClipboard(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M200,32H163.74a47.92,47.92,0,0,0-71.48,0H56A16,16,0,0,0,40,48V216a16,16,0,0,0,16,16H200a16,16,0,0,0,16-16V48A16,16,0,0,0,200,32Zm-72,0a32,32,0,0,1,32,32H96A32,32,0,0,1,128,32Zm72,184H56V48H82.75A47.93,47.93,0,0,0,80,64v8a8,8,0,0,0,8,8h80a8,8,0,0,0,8-8V64a47.93,47.93,0,0,0-2.75-16H200ZM160,152a8,8,0,0,1-8,8H104a8,8,0,0,1,0-16h48A8,8,0,0,1,160,152Zm0-32a8,8,0,0,1-8,8H104a8,8,0,0,1,0-16h48A8,8,0,0,1,160,120Z",
        color = color,
        contentDescription = "Clipboard",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphWarning(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M236.8,188.09,149.35,36.22h0a24.76,24.76,0,0,0-42.7,0L19.2,188.09a23.51,23.51,0,0,0,0,23.72A24.35,24.35,0,0,0,40.55,224h174.9a24.35,24.35,0,0,0,21.33-12.19A23.51,23.51,0,0,0,236.8,188.09ZM222.93,203.8a8.5,8.5,0,0,1-7.48,4.2H40.55a8.5,8.5,0,0,1-7.48-4.2,7.59,7.59,0,0,1,0-7.72L120.52,44.21a8.75,8.75,0,0,1,15,0l87.45,151.87A7.59,7.59,0,0,1,222.93,203.8ZM120,144V104a8,8,0,0,1,16,0v40a8,8,0,0,1-16,0Zm20,36a12,12,0,1,1-12-12A12,12,0,0,1,140,180Z",
        color = color,
        contentDescription = "Warning",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSearch(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M229.66,218.34l-50.07-50.06a88.11,88.11,0,1,0-11.31,11.31l50.06,50.07a8,8,0,0,0,11.32-11.32ZM40,112a72,72,0,1,1,72,72A72.08,72.08,0,0,1,40,112Z",
        color = color,
        contentDescription = "Search",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphMoreVert(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M112,60a16,16,0,1,1,16,16A16,16,0,0,1,112,60Zm16,52a16,16,0,1,0,16,16A16,16,0,0,0,128,112Zm0,68a16,16,0,1,0,16,16A16,16,0,0,0,128,180Z",
        color = color,
        contentDescription = "Options",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphRefresh(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M224,48V96a8,8,0,0,1-8,8H168a8,8,0,0,1,0-16h28.69L182.06,73.37a79.56,79.56,0,0,0-56.13-22.43h-.45A79.52,79.52,0,0,0,69.59,72.71,8,8,0,0,1,58.41,61.27a96,96,0,0,1,135,.79L208,76.69V48a8,8,0,0,1,16,0ZM186.41,183.29a80,80,0,0,1-112.47-.66L59.31,168H88a8,8,0,0,0,0-16H40a8,8,0,0,0-8,8v48a8,8,0,0,0,16,0V179.31l14.63,14.63A95.43,95.43,0,0,0,130,222.06h.53a95.36,95.36,0,0,0,67.07-27.33,8,8,0,0,0-11.18-11.44Z",
        color = color,
        contentDescription = "Refresh",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphChevronDown(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M213.66,101.66l-80,80a8,8,0,0,1-11.32,0l-80-80A8,8,0,0,1,53.66,90.34L128,164.69l74.34-74.35a8,8,0,0,1,11.32,11.32Z",
        color = color,
        contentDescription = "Collapse",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphChevronRight(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M181.66,133.66l-80,80a8,8,0,0,1-11.32-11.32L164.69,128,90.34,53.66a8,8,0,0,1,11.32-11.32l80,80A8,8,0,0,1,181.66,133.66Z",
        color = color,
        contentDescription = "Next",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphClose(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M205.66,194.34a8,8,0,0,1-11.32,11.32L128,139.31,61.66,205.66a8,8,0,0,1-11.32-11.32L116.69,128,50.34,61.66A8,8,0,0,1,61.66,50.34L128,116.69l66.34-66.35a8,8,0,0,1,11.32,11.32L139.31,128Z",
        color = color,
        contentDescription = "Close",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphArrowBack(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M224,128a8,8,0,0,1-8,8H59.31l58.35,58.34a8,8,0,0,1-11.32,11.32l-72-72a8,8,0,0,1,0-11.32l72-72a8,8,0,0,1,11.32,11.32L59.31,120H216A8,8,0,0,1,224,128Z",
        color = color,
        contentDescription = "Back",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphPin(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M235.33,104,152,20.69a16,16,0,0,0-22.63,0L98.37,51.72c-10.66-3.34-35-7.37-60.4,13.14a16,16,0,0,0-1.29,23.78L85,136.71,42.34,179.34a8,8,0,0,0,11.32,11.32L96.29,148l48.07,48.07A16,16,0,0,0,155.66,200c.38,0,.75,0,1.13,0a15.93,15.93,0,0,0,11.64-6.33c19.64-26.1,17.75-47.32,13.19-60L212.7,102.6,224,113.94a16,16,0,0,0,11.33-9.94ZM156,184,48,76c19.47-14.51,38.49-9.72,46.63-6.11a8,8,0,0,0,8.87-1.69L140.69,31,225,115.31,187.8,152.5a8,8,0,0,0-1.69,8.87C189.65,169.39,192.09,180.52,156,184Z",
        color = color,
        contentDescription = "Pin",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphSettings(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    PhosphorIconCanvas(
        "M128,80a48,48,0,1,0,48,48A48.05,48.05,0,0,0,128,80Zm0,80a32,32,0,1,1,32-32A32,32,0,0,1,128,160Zm109.94-52.79a8,8,0,0,0-3.89-5.4l-29.83-17-.12-33.62a8,8,0,0,0-2.83-6.08,111.91,111.91,0,0,0-36.72-20.67,8,8,0,0,0-6.46.59L128,41.85,97.88,25a8,8,0,0,0-6.47-.6A111.92,111.92,0,0,0,54.73,45.15a8,8,0,0,0-2.83,6.07l-.15,33.65-29.83,17a8,8,0,0,0-3.89,5.4,106.47,106.47,0,0,0,0,41.56,8,8,0,0,0,3.89,5.4l29.83,17,.12,33.62a8,8,0,0,0,2.83,6.08,111.91,111.91,0,0,0,36.72,20.67,8,8,0,0,0,6.46-.59L128,214.15,158.12,231a7.91,7.91,0,0,0,3.9,1,8.09,8.09,0,0,0,2.57-.42,112.1,112.1,0,0,0,36.68-20.73,8,8,0,0,0,2.83-6.07l.15-33.65,29.83-17a8,8,0,0,0,3.89-5.4A106.47,106.47,0,0,0,237.94,107.21Zm-15,34.91-28.57,16.25a8,8,0,0,0-3,3c-.58,1-1.19,2.06-1.81,3.06a7.94,7.94,0,0,0-1.22,4.21l-.15,32.25a95.89,95.89,0,0,1-25.37,14.3L134,199.36a8,8,0,0,0-3.91-1h-.19c-1.21,0-2.43,0-3.64,0a8.1,8.1,0,0,0-4.1,1l-28.84,16.1A96,96,0,0,1,67.88,201l-.11-32.2a8,8,0,0,0-1.22-4.22c-.62-1-1.23-2-1.8-3.06a8.09,8.09,0,0,0-3-3.06l-28.6-16.29a90.49,90.49,0,0,1,0-28.26L61.72,97.63a8,8,0,0,0,3-3c.58-1,1.19-2.06,1.81-3.06a7.94,7.94,0,0,0,1.22-4.21l.15-32.25a95.89,95.89,0,0,1,25.37-14.3L122,56.64a8,8,0,0,0,4.1,1c1.21,0,2.43,0,3.64,0a8,8,0,0,0,4.1-1l28.84-16.1A96,96,0,0,1,188.12,55l.11,32.2a8,8,0,0,0,1.22,4.22c.62,1,1.23,2,1.8,3.06a8.09,8.09,0,0,0,3,3.06l28.6,16.29A90.49,90.49,0,0,1,222.9,142.12Z",
        color = color,
        contentDescription = "Settings",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphUpdate(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M224,144v64a8,8,0,0,1-8,8H40a8,8,0,0,1-8-8V144a8,8,0,0,1,16,0v56H208V144a8,8,0,0,1,16,0Zm-101.66,5.66a8,8,0,0,0,11.32,0l40-40a8,8,0,0,0-11.32-11.32L136,124.69V32a8,8,0,0,0-16,0v92.69L93.66,98.34a8,8,0,0,0-11.32,11.32Z",
        color = color,
        contentDescription = "Update",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphNotification(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M221.8,175.94C216.25,166.38,208,139.33,208,104a80,80,0,1,0-160,0c0,35.34-8.26,62.38-13.81,71.94A16,16,0,0,0,48,200H88.81a40,40,0,0,0,78.38,0H208a16,16,0,0,0,13.8-24.06ZM128,216a24,24,0,0,1-22.62-16h45.24A24,24,0,0,1,128,216ZM48,184c7.7-13.24,16-43.92,16-80a64,64,0,1,1,128,0c0,36.05,8.28,66.73,16,80Z",
        color = color,
        contentDescription = "Notification",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphInfo(color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    PhosphorIconCanvas(
        "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm16-40a8,8,0,0,1-8,8,16,16,0,0,1-16-16V128a8,8,0,0,1,0-16,16,16,0,0,1,16,16v40A8,8,0,0,1,144,176ZM112,84a12,12,0,1,1,12,12A12,12,0,0,1,112,84Z",
        color = color,
        contentDescription = "Info",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphGitHub(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M208.31,75.68A59.78,59.78,0,0,0,202.93,28,8,8,0,0,0,196,24a59.75,59.75,0,0,0-48,24H124A59.75,59.75,0,0,0,76,24a8,8,0,0,0-6.93,4,59.78,59.78,0,0,0-5.38,47.68A58.14,58.14,0,0,0,56,104v8a56.06,56.06,0,0,0,48.44,55.47A39.8,39.8,0,0,0,96,192v8H72a24,24,0,0,1-24-24A40,40,0,0,0,8,136a8,8,0,0,0,0,16,24,24,0,0,1,24,24,40,40,0,0,0,40,40H96v16a8,8,0,0,0,16,0V192a24,24,0,0,1,48,0v40a8,8,0,0,0,16,0V192a39.8,39.8,0,0,0-8.44-24.53A56.06,56.06,0,0,0,216,112v-8A58.14,58.14,0,0,0,208.31,75.68ZM200,112a40,40,0,0,1-40,40H112a40,40,0,0,1-40-40v-8a41.74,41.74,0,0,1,6.9-22.48A8,8,0,0,0,80,73.83a43.81,43.81,0,0,1,.79-33.58,43.88,43.88,0,0,1,32.32,20.06A8,8,0,0,0,119.82,64h32.35a8,8,0,0,0,6.74-3.69,43.87,43.87,0,0,1,32.32-20.06A43.81,43.81,0,0,1,192,73.83a8.09,8.09,0,0,0,1,7.65A41.72,41.72,0,0,1,200,104Z",
        color = color,
        contentDescription = "GitHub",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphDiscord(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M247.51,174.93,216.83,55.59A16.08,16.08,0,0,0,201.7,43.82l-36.5-5.22a8.1,8.1,0,0,0-9,6.22l-4.29,17.18a118.69,118.69,0,0,0-47.82,0L99.8,44.82a8.1,8.1,0,0,0-9-6.22L54.3,43.82A16.08,16.08,0,0,0,39.17,55.59L8.49,174.93a15.94,15.94,0,0,0,9.56,18.71l67.3,26.92a8,8,0,0,0,10.17-4.51l8.63-21.58a116.51,116.51,0,0,0,47.7,0l8.63,21.58a8,8,0,0,0,10.17,4.51l67.3-26.92A15.94,15.94,0,0,0,247.51,174.93ZM92,160a16,16,0,1,1,16-16A16,16,0,0,1,92,160Zm72,0a16,16,0,1,1,16-16A16,16,0,0,1,164,160Z",
        color = color,
        contentDescription = "Discord Server",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphBagiBagi(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M240,104a32.06,32.06,0,0,0-31,24H176a32,32,0,0,0-29.88-20.47L117,97.81A32,32,0,0,0,96,90.15H48a16,16,0,0,0-16,16v64a16,16,0,0,0,16,16H188.69a31.82,31.82,0,0,0,22.62-9.37l32-32A16,16,0,0,0,240,104ZM199.31,165.31a15.89,15.89,0,0,1-11.31,4.69H48v-64H96a16,16,0,0,1,10.49,3.91l29.11,9.7a16,16,0,0,1,10.94,15.18H112a8,8,0,0,0,0,16h56a8,8,0,0,0,8-8,16,16,0,0,1,32,0,8,8,0,0,0,8,8h1.37ZM125.13,86.17a8,8,0,0,0,5.74,0C136.4,84,184,64.42,184,36a28,28,0,0,0-52-14.34A28,28,0,0,0,80,36C80,64.42,127.6,84,125.13,86.17ZM108,24a12,12,0,0,1,12,12,8,8,0,0,0,16,0,12,12,0,0,1,24,0c0,14.12-21.57,28.72-32,34.43C117.57,64.72,96,50.12,96,36A12,12,0,0,1,108,24Z",
        color = color,
        contentDescription = "Bagi-Bagi Donation",
        modifier = modifier,
        filled = true
    )
}

@Composable
fun GlyphQuestion(color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    PhosphorIconCanvas(
        "M140,180a12,12,0,1,1-12-12A12,12,0,0,1,140,180ZM128,72c-22.06,0-40,16.15-40,36v4a8,8,0,0,0,16,0v-4c0-11,10.77-20,24-20s24,9,24,20-10.77,20-24,20a8,8,0,0,0-8,8v8a8,8,0,0,0,16,0v-.72c18.24-3.35,32-17.9,32-35.28C168,88.15,150.06,72,128,72Zm104,56A104,104,0,1,1,128,24,104.11,104.11,0,0,1,232,128Zm-16,0a88,88,0,1,0-88,88A88.1,88.1,0,0,0,216,128Z",
        color = color,
        contentDescription = "Guide",
        modifier = modifier,
        filled = true
    )
}
