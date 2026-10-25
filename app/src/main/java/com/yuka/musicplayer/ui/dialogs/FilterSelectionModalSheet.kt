package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.*
import com.yuka.musicplayer.ui.theme.*

@Composable
fun FilterSelectionModalSheet(
    visible: Boolean,
    title: String,
    options: List<Pair<String, Int>>,
    selectedValue: String?,
    onDismissRequest: () -> Unit,
    onSelectOption: (String?) -> Unit
) {
    val accent = LocalAccentColor.current
    var searchQuery by remember(visible) { mutableStateOf("") }
    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) {
            options
        } else {
            val query = searchQuery.trim()
            options.filter { it.first.contains(query, ignoreCase = true) }
        }
    }

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        wrapHeight = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter: $title",
                    color = accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(m3SubtleFill(alpha = 0.08f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphSearch(color = accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search $title...",
                            color = m3OnSurfaceVariant().copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = m3OnSurface(),
                            fontSize = 13.sp,
                            fontFamily = ModernSansFont
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(accent)
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    TactileBox(
                        onClick = { searchQuery = "" },
                        pressedScale = 0.85f,
                        modifier = Modifier.size(26.dp).clip(CircleShape)
                    ) {
                        GlyphClose(color = m3OnSurfaceVariant(), modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedValue != null) {
                RetroButton(
                    text = "Clear Filter ($selectedValue)",
                    onClick = {
                        onSelectOption(null)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredOptions) { (name, count) ->
                    val isSelected = name == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable {
                                onSelectOption(name)
                                onDismissRequest()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) "✓  " else "    ",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = name,
                            color = if (isSelected) accent else m3OnSurface(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            fontFamily = ModernSansFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$count tracks",
                            color = m3OnSurfaceVariant(),
                            fontSize = 12.sp,
                            fontFamily = ModernSansFont
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
