package com.mijia4k.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Iso
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.net.SettingField
import com.mijia4k.app.net.displayValue
import com.mijia4k.app.net.shortValue
import com.mijia4k.app.net.isToggleOn
import com.mijia4k.app.ui.theme.MijiaTeal

/** The glyph for a camera setting, so the live screen can show icons instead of parameter names. */
fun iconFor(field: SettingField, on: Boolean = false): ImageVector = when (field.key) {
    "video_color", "photo_digital_effect" -> Icons.Filled.Palette
    "video_resolution" -> Icons.Filled.Hd
    "video_quality" -> Icons.Filled.HighQuality
    "video_mute" -> if (on) Icons.Filled.MicOff else Icons.Filled.Mic
    "video_stamp", "photo_stamp" -> Icons.Filled.Today
    "video_record_startup" -> Icons.Filled.FiberManualRecord
    "video_metering_mode", "photo_metering_mode" -> Icons.Filled.CenterFocusStrong
    "video_ev_bias", "photo_ev_bias" -> Icons.Filled.Exposure
    "video_white_balance", "photo_wb" -> Icons.Filled.WbSunny
    "video_iso", "photo_iso" -> Icons.Filled.Iso
    "video_time_lapse", "video_piv_time_lapse", "photo_time_lapse" -> Icons.Filled.Timelapse
    "video_time_lapse_length" -> Icons.Filled.HourglassBottom
    "video_loop_length" -> Icons.Filled.Loop
    "video_rate" -> Icons.Filled.Speed
    "photo_size" -> Icons.Filled.AspectRatio
    "photo_shutter" -> Icons.Filled.PhotoCamera
    "photo_raw" -> Icons.Filled.Tune
    "photo_selftimer" -> Icons.Filled.Timer
    "photo_burst_frequence" -> Icons.Filled.BurstMode
    "auto_rotate" -> Icons.Filled.ScreenRotation
    else -> Icons.Filled.Tune
}

/**
 * Icon strip for the current mode's settings, shown under the shutter row.
 * Each chip is an icon with the current value under it; toggles flip on tap,
 * everything else opens a quick option sheet.
 */
@Composable
fun ModeSettingsBar(
    fields: List<SettingField>,
    settings: Map<String, String>,
    enabled: Boolean,
    onToggle: (SettingField, Boolean) -> Unit,
    onOpen: (SettingField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (rowFields in fields.chunked(5)) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
        for (field in rowFields) {
            val value = settings[field.key]
            val on = field.isToggle && isToggleOn(field, value)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable(enabled = enabled) { if (field.isToggle) onToggle(field, !on) else onOpen(field) },
            ) {
                Box(
                    Modifier.size(48.dp).background(
                        if (on) MijiaTeal else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        iconFor(field, on),
                        contentDescription = field.label,
                        tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (field.isToggle) (if (on) "On" else "Off") else shortValue(field.key, value),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        }
    }
}

/** Bottom sheet listing the camera's own choices for [field]; picking one applies it at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingOptionSheet(
    field: SettingField,
    currentValue: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val allOptions by CameraSession.options.collectAsState()
    val mode by CameraSession.currentMode.collectAsState()
    val entry = allOptions[field.key]
    var failed by remember(field.key) { mutableStateOf(false) }

    LaunchedEffect(field.key, failed) {
        if (CameraSession.options.value[field.key] == null && !failed) {
            failed = CameraSession.fetchOptions(field.key) == null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Icon(iconFor(field), contentDescription = null, tint = MijiaTeal)
                Spacer(Modifier.width(12.dp))
                Text(field.label, style = MaterialTheme.typography.titleMedium)
            }
            when {
                entry == null && failed -> Column(Modifier.padding(24.dp)) {
                    Text("Couldn't load the choices from the camera.")
                    TextButton(onClick = { failed = false }) { Text("Retry") }
                }

                entry == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> {
                    val locked = !entry.settable && entry.fetchedInMode == mode
                    if (locked) {
                        Text(
                            "Can't be changed in this shooting mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    LazyColumn {
                        items(entry.values) { option ->
                            val selected = option.equals(currentValue, ignoreCase = true)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !locked) { onPick(option) }
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                            ) {
                                Box(Modifier.size(28.dp)) {
                                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MijiaTeal)
                                }
                                Text(
                                    displayValue(field.key, option),
                                    color = when {
                                        locked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        selected -> MijiaTeal
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
