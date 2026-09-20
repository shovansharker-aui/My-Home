package com.mijia4k.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.AmbaSocketClient
import com.mijia4k.app.ui.theme.MijiaTeal

/** The camera's battery (not the phone's), drawn over the live preview. */
@Composable
fun BatteryBadge(status: AmbaSocketClient.BatteryStatus, modifier: Modifier = Modifier) {
    val icon = when {
        status.charging -> Icons.Filled.BatteryChargingFull
        status.level < 12 -> Icons.Filled.BatteryAlert
        status.level < 25 -> Icons.Filled.Battery1Bar
        status.level < 38 -> Icons.Filled.Battery2Bar
        status.level < 52 -> Icons.Filled.Battery3Bar
        status.level < 66 -> Icons.Filled.Battery4Bar
        status.level < 80 -> Icons.Filled.Battery5Bar
        status.level < 93 -> Icons.Filled.Battery6Bar
        else -> Icons.Filled.BatteryFull
    }
    val tint = when {
        status.charging -> MijiaTeal
        status.level < 15 -> Color(0xFFFF6B5E)
        else -> Color.White
    }
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = "Camera battery", tint = tint, modifier = Modifier.size(20.dp))
        Text("${status.level}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
