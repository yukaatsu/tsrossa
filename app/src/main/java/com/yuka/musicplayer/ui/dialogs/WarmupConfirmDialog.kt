package com.yuka.musicplayer.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.musicplayer.ui.components.CyberBottomSheet
import com.yuka.musicplayer.ui.components.ModalCloseButton
import com.yuka.musicplayer.ui.components.RetroButton
import com.yuka.musicplayer.ui.theme.LocalAccentColor
import com.yuka.musicplayer.ui.theme.LocalIsDarkTheme
import com.yuka.musicplayer.ui.theme.ModernSansFont
import com.yuka.musicplayer.ui.theme.m3OnSurface
import com.yuka.musicplayer.ui.theme.m3OnSurfaceVariant

@Composable
fun WarmupConfirmDialog(
    visible: Boolean,
    dacName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val accent = LocalAccentColor.current
    val isDark = LocalIsDarkTheme.current

    CyberBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        wrapHeight = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "USB DAC Warm-up",
                    color = accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModernSansFont
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Start a hardware warm-up cycle on '$dacName'?",
                color = m3OnSurface(),
                fontSize = 13.5.sp,
                fontFamily = ModernSansFont,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This plays a stepped frequency sweep to stabilize the DAC clock oscillators and analog output stage.",
                color = m3OnSurfaceVariant(),
                fontSize = 12.sp,
                fontFamily = ModernSansFont,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RetroButton(
                    text = "Cancel",
                    onClick = onDismissRequest,
                    color = m3OnSurface(),
                    modifier = Modifier.weight(1f)
                )

                RetroButton(
                    text = "Start",
                    onClick = {
                        onDismissRequest()
                        onConfirm()
                    },
                    isSelected = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
