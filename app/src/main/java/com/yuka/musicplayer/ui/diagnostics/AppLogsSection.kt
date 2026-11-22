package com.yuka.musicplayer.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.TactileBox
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.TerminalFont
import com.yuka.musicplayer.ui.theme.TerminalGray
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3SurfaceContainerHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.yuka.musicplayer.utils.AppLogger
import kotlinx.coroutines.launch

@Composable
fun AppLogsSection(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val allLogs by AppLogger.entries.collectAsState()
    var selectedCategory by remember { mutableStateOf("ALL") }
    val categories = listOf("ALL", "AUDIO", "USB", "PLAYBACK", "STORAGE", "UI", "SYSTEM", "LOGCAT")
    
    var logcatLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingLogcat by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategory) {
        // Defer 220ms on initial mount so sheet entrance animation finishes without CPU contention from logcat process fork
        delay(220L)
        if (selectedCategory == "LOGCAT") {
            isLoadingLogcat = true
            logcatLines = withContext(Dispatchers.IO) { AppLogger.readProcessLogcat(250) }
            isLoadingLogcat = false
        } else {
            isRefreshing = true
            withContext(Dispatchers.IO) { AppLogger.syncFromProcessLogcat(350) }
            isRefreshing = false
        }
    }

    val filteredLogs = remember(allLogs, selectedCategory) {
        if (selectedCategory == "ALL") allLogs
        else allLogs.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Horizontal filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSel = selectedCategory == cat
                TactileBox(
                    onClick = { selectedCategory = cat },
                    pressedScale = 0.94f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) LocalAccentColor.current.copy(alpha = 0.22f) else m3SurfaceContainerHigh())
                        .border(
                            width = 1.dp,
                            color = if (isSel) LocalAccentColor.current.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSel) LocalAccentColor.current else TerminalGray,
                        fontFamily = ModernSansFont,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Sub-toolbar: Copy & Clear / Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedCategory == "LOGCAT") "${logcatLines.size} logcat entries" else "${filteredLogs.size} events",
                color = TerminalGray,
                fontSize = 10.5.sp,
                fontFamily = TerminalFont
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TactileBox(
                    onClick = {
                        coroutineScope.launch {
                            if (selectedCategory == "LOGCAT") {
                                isLoadingLogcat = true
                                logcatLines = AppLogger.readProcessLogcat(250)
                                isLoadingLogcat = false
                            } else {
                                isRefreshing = true
                                AppLogger.syncFromProcessLogcat(350)
                                isRefreshing = false
                            }
                        }
                    },
                    pressedScale = 0.94f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(m3SurfaceContainerHigh())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isLoadingLogcat || isRefreshing) "..." else "Refresh",
                        color = LocalAccentColor.current,
                        fontFamily = ModernSansFont,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (selectedCategory != "LOGCAT") {
                    TactileBox(
                        onClick = { AppLogger.clear() },
                        pressedScale = 0.94f,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Red.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Clear",
                            color = Color(0xFFFF5252),
                            fontFamily = ModernSansFont,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                TactileBox(
                    onClick = {
                        val text = if (selectedCategory == "LOGCAT") {
                            logcatLines.joinToString("\n")
                        } else {
                            filteredLogs.joinToString("\n") { it.formattedLine() }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("tsrossa_logs", text))
                        Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                    },
                    pressedScale = 0.94f,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LocalAccentColor.current.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Copy",
                        color = LocalAccentColor.current,
                        fontFamily = ModernSansFont,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Log content box with fixed height (280.dp) so it embeds smoothly into the parent scrollable list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070A0F))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (selectedCategory == "LOGCAT") {
                if (isLoadingLogcat) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Reading process logcat...", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
                    }
                } else if (logcatLines.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logcat output found.", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logcatLines) { line ->
                            val color = when {
                                line.contains(" E/") || line.contains(" E ") -> Color(0xFFFF5252)
                                line.contains(" W/") || line.contains(" W ") -> Color(0xFFFFD700)
                                line.contains("Tsrossa") || line.contains("AudioEngine") -> LocalAccentColor.current
                                else -> m3OnSurface().copy(alpha = 0.85f)
                            }
                            Text(
                                text = line,
                                color = color,
                                fontFamily = TerminalFont,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            } else {
                if (filteredLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No log events in '$selectedCategory'.", color = TerminalGray, fontFamily = TerminalFont, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            val levelColor = when (entry.level) {
                                "ERR" -> Color(0xFFFF5252)
                                "WARN" -> Color(0xFFFFD700)
                                "DEBUG" -> Color(0xFF888888)
                                else -> LocalAccentColor.current
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.timestamp,
                                        color = TerminalGray,
                                        fontFamily = TerminalFont,
                                        fontSize = 9.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "[${entry.level}/${entry.category}]",
                                        color = levelColor,
                                        fontFamily = TerminalFont,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
        }
    }
}
