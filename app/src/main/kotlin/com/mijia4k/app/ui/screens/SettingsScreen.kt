package com.mijia4k.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// Every field set below, including field order and current default values, is
// transcribed directly from screen recordings of the real Mi Home app for
// this camera (all 9 modes + the general Camera Settings page were
// captured). The *labels* and *order* are exact. The *key* strings sent to
// the camera's GET_SETTING/SET_SETTING commands are still our own guesses —
// the real protocol doesn't document its key names anywhere public — so a
// field showing "camera reported no options" means the guessed key doesn't
// match this firmware's actual name for it, not that the setting is fake.

private val VIDEO_FIELDS = listOf(
    SettingField("Color", "color_mode"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Mic Mute", "mic_mute", isToggle = true),
    SettingField("Stamp", "stamp"),
    SettingField("Auto Record", "auto_record", isToggle = true),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
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
private val SLOW_MOTION_FIELDS = listOf(
    SettingField("Speed", "speed"),
    SettingField("Color", "color_mode"),
    SettingField("Quality", "quality"),
    SettingField("Auto Record", "auto_record", isToggle = true),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val LOOP_RECORD_FIELDS = listOf(
    SettingField("Video Length", "video_length"),
    SettingField("Color", "color_mode"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Mic Mute", "mic_mute", isToggle = true),
    SettingField("Stamp", "stamp"),
    SettingField("Auto Record", "auto_record", isToggle = true),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val VIDEO_PHOTO_FIELDS = listOf(
    SettingField("Interval", "interval"),
    SettingField("Color", "color_mode"),
    SettingField("Resolution", "resolution"),
    SettingField("Quality", "quality"),
    SettingField("Mic Mute", "mic_mute", isToggle = true),
    SettingField("Stamp", "stamp"),
    SettingField("Auto Record", "auto_record", isToggle = true),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("WB", "wb"),
)
private val PHOTO_FIELDS = listOf(
    SettingField("Aspect Ratio", "aspect_ratio"),
    SettingField("Stamp", "stamp"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("Shutter", "shutter"),
    SettingField("ISO", "iso"),
    SettingField("RAW", "raw", isToggle = true),
    SettingField("WB", "wb"),
    SettingField("Color", "color_mode"),
)
private val TIMER_FIELDS = listOf(
    SettingField("Countdown", "countdown"),
    SettingField("Aspect Ratio", "aspect_ratio"),
    SettingField("Stamp", "stamp"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("ISO", "iso"),
    SettingField("WB", "wb"),
    SettingField("Color", "color_mode"),
)
private val BURST_FIELDS = listOf(
    SettingField("Rate", "burst_rate"),
    SettingField("Aspect Ratio", "aspect_ratio"),
    SettingField("Stamp", "stamp"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("ISO", "iso"),
    SettingField("WB", "wb"),
    SettingField("Color", "color_mode"),
)
private val TIME_LAPSE_PHOTO_FIELDS = listOf(
    SettingField("Interval", "interval"),
    SettingField("Aspect Ratio", "aspect_ratio"),
    SettingField("Stamp", "stamp"),
    SettingField("Metering Mode", "metering_mode"),
    SettingField("EV", "ev"),
    SettingField("ISO", "iso"),
    SettingField("WB", "wb"),
    SettingField("Color", "color_mode"),
)

// General device settings ("Camera Settings" from the Settings hub) — not
// tied to any shooting mode.
private val GENERAL_FIELDS = listOf(
    SettingField("Video Standard", "video_standard"),
    SettingField("Beep Volume", "beep_volume"),
    SettingField("Default Mode", "default_mode"),
    SettingField("Rotate", "rotate", isToggle = true),
    SettingField("Auto Screen Lock", "auto_screen_lock"),
    SettingField("Auto Power Off", "auto_power_off"),
    SettingField("Wi-Fi Auto On", "wifi_auto_on", isToggle = true),
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
    "normal_record" -> VIDEO_FIELDS
    "time_lapse_record" -> TIME_LAPSE_VIDEO_FIELDS
    "slow_motion_record" -> SLOW_MOTION_FIELDS
    "loop_record" -> LOOP_RECORD_FIELDS
    "video_photo" -> VIDEO_PHOTO_FIELDS
    "photo" -> PHOTO_FIELDS
    "self_timer" -> TIMER_FIELDS
    "burst" -> BURST_FIELDS
    "time_lapse_photo" -> TIME_LAPSE_PHOTO_FIELDS
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
    var showGeneral by remember { mutableStateOf(false) }

    // Each guessed key that the camera doesn't recognize costs a full
    // ~5s read timeout, so a batch of 10 fields fetched one after another
    // (the control socket only allows one in-flight command) could block
    // the whole list from appearing for the better part of a minute.
    // Update `values` after each field resolves instead of waiting for the
    // whole batch, so rows populate as their answers come in.
    fun refreshValues(forFields: List<SettingField>) {
        scope.launch {
            for (f in forFields) {
                val v = CameraSession.client.getSetting(f.key).getOrNull()?.optString("param")
                if (!v.isNullOrEmpty()) values = values + (f.key to v)
            }
        }
    }

    LaunchedEffect(modeValue, showGeneral) {
        values = emptyMap()
        refreshValues(if (showGeneral) GENERAL_FIELDS else fields)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showGeneral) "Camera Settings" else "Settings") },
                navigationIcon = {
                    IconButton(onClick = { if (showGeneral) showGeneral = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (showGeneral) {
            GeneralSettingsList(
                padding = padding,
                values = values,
                onFieldTap = { pickerField = it },
                onToggle = { field, checked ->
                    scope.launch {
                        CameraSession.client.setSetting(field.key, if (checked) "on" else "off")
                        refreshValues(GENERAL_FIELDS)
                    }
                },
                onOpenDeviceInfo = { /* shown inline below the list */ },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        headlineContent = { Text("Camera Settings") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { showGeneral = true },
                    )
                    HorizontalDivider(thickness = 8.dp)
                }

                item {
                    Text(
                        "${MODE_LABELS[modeValue] ?: modeValue} Parameter Settings",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(fields) { field -> SettingRow(field, values[field.key], onTap = { pickerField = field }, onToggle = { checked ->
                    scope.launch {
                        CameraSession.client.setSetting(field.key, if (checked) "on" else "off")
                        refreshValues(fields)
                    }
                }) }

                item {
                    ListItem(
                        headlineContent = { Text("Diagnostics (advanced/debug)") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenDiagnostics).padding(top = 24.dp),
                    )
                }
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
                    refreshValues(if (showGeneral) GENERAL_FIELDS else fields)
                }
            },
        )
    }
}

@Composable
private fun SettingRow(field: SettingField, value: String?, onTap: () -> Unit, onToggle: (Boolean) -> Unit) {
    val displayValue = value ?: "..."
    if (field.isToggle) {
        ListItem(
            headlineContent = { Text(field.label) },
            trailingContent = {
                Switch(checked = displayValue == "on" || displayValue == "1" || displayValue == "true", onCheckedChange = onToggle)
            },
        )
    } else {
        ListItem(
            headlineContent = { Text(field.label) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            },
            modifier = Modifier.clickable(onClick = onTap),
        )
    }
    HorizontalDivider()
}

@Composable
private fun GeneralSettingsList(
    padding: androidx.compose.foundation.layout.PaddingValues,
    values: Map<String, String>,
    onFieldTap: (SettingField) -> Unit,
    onToggle: (SettingField, Boolean) -> Unit,
    onOpenDeviceInfo: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var deviceInfo by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf("") }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        deviceInfo = CameraSession.client.getDeviceInfo().fold({ it.toString(2) }, { "error: ${it.message}" })
        CameraSession.client.getStorageSpaceBytes().getOrNull()?.let {
            storage = "%.1fG total".format(it / 1024.0 / 1024.0 / 1024.0)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        item {
            Text(
                "Camera Parameter",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(GENERAL_FIELDS) { field ->
            SettingRow(field, values[field.key], onTap = { onFieldTap(field) }, onToggle = { onToggle(field, it) })
        }
        item {
            ListItem(
                headlineContent = { Text("SD card") },
                trailingContent = { Text(storage.ifBlank { "..." }, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            HorizontalDivider()
        }

        item {
            Text(
                "Device Info",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            Text(
                deviceInfo.ifBlank { "Loading..." },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            // Deliberately not wired to a real command: there's no
            // confirmed protocol message for "restore defaults", and
            // guessing at one on a real device that resets/reboots the
            // camera isn't a safe thing to gamble on.
            ListItem(
                headlineContent = { Text("Restore camera settings") },
                modifier = Modifier.clickable { showRestoreConfirm = true }.padding(top = 8.dp),
            )
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            confirmButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("OK") } },
            title = { Text("Not available yet") },
            text = { Text("This app doesn't know the camera's real \"restore defaults\" command yet, so this button is intentionally inert rather than guessing at one on your actual hardware.") },
        )
    }
}

@Composable
private fun OptionPickerDialog(field: SettingField, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    // loading = null, resolved (success or failure) = non-null list. A
    // previous version reassigned this back to null on failure, which is
    // indistinguishable from "still loading" — the dialog spun forever on
    // any timeout/error instead of ever showing the "no options" message.
    var options by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Every field reporting "no options" (not a timeout/error) points at
    // something systemic — wrong request shape, wrong msg_id, or this
    // firmware just not implementing per-field option enumeration — rather
    // than individually wrong key guesses. Surface the raw camera responses
    // for both the options query and the current-value query so that's
    // visible directly in the dialog instead of needing another round of
    // screen recordings to diagnose.
    var rawOptionsResponse by remember { mutableStateOf("") }
    var rawValueResponse by remember { mutableStateOf("") }

    LaunchedEffect(field.key) {
        val optionsResult = CameraSession.client.getSettingOptions(field.key)
        errorMessage = optionsResult.exceptionOrNull()?.message
        rawOptionsResponse = optionsResult.fold({ it.toString(2) }, { "error: ${it.message}" })
        options = optionsResult.getOrNull()?.let { json ->
            json.optJSONArray("param")?.let { arr -> (0 until arr.length()).mapNotNull { arr.opt(it)?.toString() } }
        } ?: emptyList()

        val valueResult = CameraSession.client.getSetting(field.key)
        rawValueResponse = valueResult.fold({ it.toString(2) }, { "error: ${it.message}" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(field.label) },
        text = {
            val current = options
            when {
                current == null -> CircularProgressIndicator()
                current.isEmpty() -> androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Camera reported no options for \"${field.key}\"" +
                            (errorMessage?.let { ": $it" } ?: " (guessed key name — may not match this firmware)."),
                    )
                    Text(
                        "Raw response to GET_SINGLE_SETTING_OPTIONS(\"${field.key}\"):",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(rawOptionsResponse, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Raw response to GET_SETTING(\"${field.key}\"):",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(rawValueResponse, style = MaterialTheme.typography.bodySmall)
                }
                else -> androidx.compose.foundation.layout.Column {
                    for (opt in current) {
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
