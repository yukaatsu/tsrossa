package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.model.SortField
import com.yuka.musicplayer.model.SortOrder
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.GlyphCheck
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.RetroButton
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant

@Composable
fun LibrarySortModalSheet(
    visible: Boolean,
    currentField: SortField,
    currentOrder: SortOrder,
    onDismissRequest: () -> Unit,
    onApplySort: (SortField, SortOrder) -> Unit
) {
    val accent = LocalAccentColor.current
    var selectedOrder by remember(currentOrder) { mutableStateOf(currentOrder) }

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
                    text = "Sort Tracks",
                    color = accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sort Order Selector (Ascending / Descending) — clean without wrapper background
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isAsc = selectedOrder == SortOrder.ASCENDING
                RetroButton(
                    text = "Ascending (Default)",
                    onClick = {
                        selectedOrder = SortOrder.ASCENDING
                        onApplySort(currentField, SortOrder.ASCENDING)
                    },
                    isSelected = isAsc,
                    modifier = Modifier.weight(1f)
                )

                RetroButton(
                    text = "Descending",
                    onClick = {
                        selectedOrder = SortOrder.DESCENDING
                        onApplySort(currentField, SortOrder.DESCENDING)
                    },
                    isSelected = !isAsc,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Sort By",
                color = m3OnSurfaceVariant(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = ModernSansFont
            )

            Spacer(modifier = Modifier.height(8.dp))

            // List of 11 SortField options
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SortField.values().forEach { field ->
                    val isSelected = field == currentField
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable {
                                onApplySort(field, selectedOrder)
                                onDismissRequest()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            GlyphCheck(color = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Spacer(modifier = Modifier.width(24.dp))
                        }
                        Text(
                            text = field.displayName,
                            color = if (isSelected) accent else m3OnSurface(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            fontFamily = ModernSansFont,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
