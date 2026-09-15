package com.mijia4k.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.CameraSession
import kotlinx.coroutines.launch

private data class SettingField(val label: String, val key: String, val isToggle: Boolean = false)

// Confirmed real, from the stock app's Time Lapse Video settings screen.
private val TIME_LAPSE_VIDEO_FIELDS = listOf(
    SettingField("Interval", "interval"),
    SettingField("Video Length", "video_length"),
    SettingField("Color", "color_mode"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Stamp", "stamp"),
    SettingField("Auto Record", "auto_record", isToggle = true),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)

// Everything below is inferred from that one confirmed set, not verified —
// if you can screenshot the settings screen for these modes from the stock
// app too, these can be made exact instead of inferred.
private val VIDEO_FIELDS = listOf(
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Color", "color_mode"),
    SettingField("Stamp", "stamp"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val SLOW_MOTION_FIELDS = listOf(
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Color", "color_mode"),
    SettingField("Stamp", "stamp"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val LOOP_RECORD_FIELDS = listOf(
    SettingField("Loop Duration", "loop_duration"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Color", "color_mode"),
    SettingField("Stamp", "stamp"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val PHOTO_FIELDS = listOf(
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Color", "color_mode"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val TIMER_FIELDS = listOf(
    SettingField("Countdown", "timer_delay"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Color", "color_mode"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val BURST_FIELDS = listOf(
    SettingField("Burst Count", "burst_count"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Color", "color_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val TIME_LAPSE_PHOTO_FIELDS = listOf(
    SettingField("Interval", "interval"),
    SettingField("Color", "color_mode"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Stamp", "stamp"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)

private val MODE_LABELS = mapOf(
    "normal_record" to "Video",
    "time_lapse_record" to "Time Lapse Video",
    "slow_motion_record" to "Slow Motion",
    "loop_record" to "Loop Record",
    "video_photo" to "Video+Photo",
    "photo" to "Photo",
    "self_timer" to "Timer",
    "burst" to "Burst",
    "time_lapse_photo" to "Time Lapse Photo",
)

private fun fieldsFor(modeValue: String): List<SettingField> = when (modeValue) {
    "time_lapse_record" -> TIME_LAPSE_VIDEO_FIELDS
    "normal_record" -> VIDEO_FIELDS
    "slow_motion_record" -> SLOW_MOTION_FIELDS
    "loop_record" -> LOOP_RECORD_FIELDS
    "photo" -> PHOTO_FIELDS
    "self_timer" -> TIMER_FIELDS
    "burst" -> BURST_FIELDS
    "time_lapse_photo" -> TIME_LAPSE_PHOTO_FIELDS
    "video_photo" -> VIDEO_FIELDS
    else -> PHOTO_FIELDS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()
    val modeValue = CameraSession.currentModeValue
    val fields = remember(modeValue) { fieldsFor(modeValue) }

    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pickerField by remember { mutableStateOf<SettingField?>(null) }
    var showRawDump by remember { mutableStateOf(false) }
    var rawDump by remember { mutableStateOf("") }

    fun refreshValues() {
        scope.launch {
            val map = mutableMapOf<String, String>()
            for (f in fields) {
                CameraSession.client.getSetting(f.key).getOrNull()?.optString("param")?.let { map[f.key] = it }
            }
            values = map
        }
    }

    LaunchedEffect(modeValue) { refreshValues() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Camera Settings") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { showRawDump = !showRawDump },
                )
                HorizontalDivider()
            }
            item {
                // This camera has no gimbal — shown (disabled) purely to
                // match the stock app's layout.
                ListItem(
                    headlineContent = { Text("Handheld Gimbal Settings", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                )
                HorizontalDivider(thickness = 8.dp)
            }

            if (showRawDump) {
                item { RawDumpSection() }
            }

            item {
                Text(
                    "${MODE_LABELS[modeValue] ?: modeValue} Parameter Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(fields) { field ->
                val value = values[field.key] ?: "..."
                if (field.isToggle) {
                    ListItem(
                        headlineContent = { Text(field.label) },
                        trailingContent = {
                            Switch(
                                checked = value == "on" || value == "1" || value == "true",
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        CameraSession.client.setSetting(field.key, if (checked) "on" else "off")
                                        refreshValues()
                                    }
                                },
                            )
                        },
                    )
                } else {
                    ListItem(
                        headlineContent = { Text(field.label) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable { pickerField = field },
                    )
                }
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Diagnostics (advanced/debug)") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenDiagnostics).padding(top = 24.dp),
                )
            }
        }
    }

    pickerField?.let { field ->
        OptionPickerDialog(
            field = field,
            onDismiss = { pickerField = null },
            onPicked = { picked ->
                scope.launch {
                    CameraSession.client.setSetting(field.key, picked)
                    pickerField = null
                    refreshValues()
                }
            },
        )
    }
}

@Composable
private fun OptionPickerDialog(field: SettingField, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    var options by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(field.key) {
        options = CameraSession.client.getSettingOptions(field.key).getOrNull()?.let { json ->
            json.optJSONArray("param")?.let { arr -> (0 until arr.length()).mapNotNull { arr.opt(it)?.toString() } }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(field.label) },
        text = {
            when {
                options == null -> CircularProgressIndicator()
                options!!.isEmpty() -> Text("Camera reported no options for \"${field.key}\" (guessed key name — may not match this firmware).")
                else -> Column {
                    for (opt in options!!) {
                        Text(
                            opt,
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(opt) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RawDumpSection() {
    val scope = rememberCoroutineScope()
    var allSettings by remember { mutableStateOf("") }
    var deviceInfo by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allSettings = CameraSession.client.getAllCurrentSettings().fold({ it.toString(2) }, { "error: ${it.message}" })
        deviceInfo = CameraSession.client.getDeviceInfo().fold({ it.toString(2) }, { "error: ${it.message}" })
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Device info", style = MaterialTheme.typography.labelLarge)
        Text(deviceInfo, style = MaterialTheme.typography.bodySmall)
        Text("All current settings (raw)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(allSettings, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            scope.launch {
                allSettings = CameraSession.client.getAllCurrentSettings().fold({ it.toString(2) }, { "error: ${it.message}" })
            }
        }) { Text("Refresh") }
        HorizontalDivider(thickness = 8.dp, modifier = Modifier.padding(top = 16.dp))
    }
}
