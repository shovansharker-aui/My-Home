package com.mijia4k.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
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
import com.mijia4k.app.net.parseSettingsArray
import kotlinx.coroutines.launch

private data class SettingField(val label: String, val key: String, val isToggle: Boolean = false)

// Field labels/order below are transcribed from real screen recordings of
// the stock app. The *keys* are now confirmed too — read directly off the
// real camera's GET_ALL_CURRENT_SETTINGS dump (msg_id 3, which works
// perfectly), not guessed. That dump also revealed that the per-field
// GET_SETTING/GET_SINGLE_SETTING_OPTIONS commands (msg_id 1/9, both of which
// take a "type" argument) fail with the *same* error code regardless of
// which key is passed — so this screen now reads every value from one
// GET_ALL_CURRENT_SETTINGS call instead of querying fields individually,
// and edits go through a free-text field (there's no way to enumerate valid
// options from the camera on this firmware) rather than a live picker.

private val VIDEO_FIELDS = listOf(
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Mic Mute", "video_mute", isToggle = true),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
    SettingField("ISO", "video_iso"),
)
private val TIME_LAPSE_VIDEO_FIELDS = listOf(
    SettingField("Interval", "video_time_lapse"),
    SettingField("Video Length", "video_time_lapse_length"),
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
private val SLOW_MOTION_FIELDS = listOf(
    SettingField("Speed", "video_rate"),
    SettingField("Color", "video_color"),
    SettingField("Quality", "video_quality"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
private val LOOP_RECORD_FIELDS = listOf(
    SettingField("Video Length", "video_loop_length"),
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Mic Mute", "video_mute", isToggle = true),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
private val VIDEO_PHOTO_FIELDS = listOf(
    SettingField("Interval", "video_piv_time_lapse"),
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Mic Mute", "video_mute", isToggle = true),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
private val PHOTO_FIELDS = listOf(
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("Shutter", "photo_shutter"),
    SettingField("ISO", "photo_iso"),
    SettingField("RAW", "photo_raw", isToggle = true),
    SettingField("WB", "photo_wb"),
    // Unconfirmed: the real camera dump doesn't show an obvious
    // "photo_color" key — "photo_digital_effect" is the closest candidate
    // but its "off" value doesn't look like a color-profile name, so this
    // one may still be wrong.
    SettingField("Color", "photo_digital_effect"),
)
private val TIMER_FIELDS = listOf(
    SettingField("Countdown", "photo_selftimer"),
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("ISO", "photo_iso"),
    SettingField("WB", "photo_wb"),
)
private val BURST_FIELDS = listOf(
    SettingField("Rate", "photo_burst_frequence"),
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("ISO", "photo_iso"),
    SettingField("WB", "photo_wb"),
)
private val TIME_LAPSE_PHOTO_FIELDS = listOf(
    SettingField("Interval", "photo_time_lapse"),
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("ISO", "photo_iso"),
    SettingField("WB", "photo_wb"),
)

// General device settings ("Camera Settings" from the Settings hub) — not
// tied to any shooting mode. Includes a few fields confirmed to exist in
// the protocol dump but not seen in the screen recordings (LED Mode, LCD
// Brightness, Language) — likely just further down the same list.
private val GENERAL_FIELDS = listOf(
    SettingField("Video Standard", "system_type"),
    SettingField("Beep Volume", "prompt_volume"),
    SettingField("Default Mode", "default_boot_mode"),
    // Real value seen was "up", not "on"/"off" — not actually a toggle.
    SettingField("Rotate", "auto_rotate"),
    SettingField("Auto Screen Lock", "auto_lock_screen"),
    SettingField("Auto Power Off", "auto_power_off"),
    SettingField("Wi-Fi Auto On", "wifi_auto_start", isToggle = true),
    SettingField("LED Mode", "led_mode"),
    SettingField("LCD Brightness", "lcd_brightness"),
    SettingField("Language", "language"),
)

// Values confirmed live against the real camera: "time_lapse_record"
// matched the camera's own settings dump exactly, and the camera
// live-reported "normal_capture" as its current mode (a value never
// guessed before) — video-family modes use "_record", photo-family modes
// use "_capture". Must match CAMERA_MODES in ShootScreen.kt.
private val MODE_LABELS = mapOf(
    "normal_record" to "Video",
    "time_lapse_record" to "Time Lapse Video",
    "slow_motion" to "Slow Motion",
    "loop_record" to "Loop Record",
    "record_capture" to "Video+Photo",
    "normal_capture" to "Photo",
    "timing_capture" to "Timer",
    "continuous_capture" to "Burst",
    "time_lapse_capture" to "Time Lapse Photo",
)

// The only confirmed-real option list (from the Slow Motion mode's EV
// picker in the stock app) — used as a quick-pick for any *_ev_bias field
// instead of free-text entry.
private val EV_OPTIONS = listOf(
    "+2.0EV", "+1.7EV", "+1.3EV", "+1.0EV", "+0.7EV", "+0.3EV", "0",
    "-0.3EV", "-0.7EV", "-1.0EV", "-1.3EV", "-1.7EV", "-2.0EV",
)

// GET_SINGLE_SETTING_OPTIONS comes back empty even for confirmed-correct
// keys (verified live against the real camera) — this firmware just
// doesn't support live option enumeration for most fields. These are
// curated guesses from standard action-cam UX conventions, not confirmed
// real option strings — "Other..." always lets you type the exact value
// instead if a guess is wrong or incomplete.
private val KNOWN_OPTIONS: Map<String, List<String>> = mapOf(
    "system_type" to listOf("NTSC", "PAL"),
    "prompt_volume" to listOf("mute", "low", "high"),
    "auto_lock_screen" to listOf("off", "30s", "1min", "3min"),
    "auto_power_off" to listOf("off", "3min", "5min", "10min"),
    "auto_rotate" to listOf("up", "down"),
    "lcd_brightness" to listOf("low", "medium", "high"),
    "led_mode" to listOf("all_on", "front_off", "all_off"),
    "video_metering_mode" to listOf("center", "average", "spot"),
    "photo_metering_mode" to listOf("center", "average", "spot"),
    "video_white_balance" to listOf("auto", "sunny", "cloudy", "incandescent", "fluorescent"),
    "photo_wb" to listOf("auto", "sunny", "cloudy", "incandescent", "fluorescent"),
    "video_stamp" to listOf("off", "date", "date_time"),
    "photo_stamp" to listOf("off", "date", "date_time"),
)

private fun fieldsFor(modeValue: String): List<SettingField> = when (modeValue) {
    "normal_record" -> VIDEO_FIELDS
    "time_lapse_record" -> TIME_LAPSE_VIDEO_FIELDS
    "slow_motion" -> SLOW_MOTION_FIELDS
    "loop_record" -> LOOP_RECORD_FIELDS
    "record_capture" -> VIDEO_PHOTO_FIELDS
    "normal_capture" -> PHOTO_FIELDS
    "timing_capture" -> TIMER_FIELDS
    "continuous_capture" -> BURST_FIELDS
    "time_lapse_capture" -> TIME_LAPSE_PHOTO_FIELDS
    else -> PHOTO_FIELDS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()
    val modeValue = CameraSession.currentModeValue
    val fields = remember(modeValue) { fieldsFor(modeValue) }

    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editField by remember { mutableStateOf<SettingField?>(null) }
    var showGeneral by remember { mutableStateOf(false) }
    var lastWriteStatus by remember { mutableStateOf<String?>(null) }

    // One call gets every setting the camera has, mode-specific and general
    // alike — GET_SETTING per-field doesn't work on this firmware, but
    // GET_ALL_CURRENT_SETTINGS does.
    fun refreshAll() {
        scope.launch {
            values = CameraSession.client.getAllCurrentSettings().getOrNull()?.let { parseSettingsArray(it) } ?: values
        }
    }

    LaunchedEffect(Unit) { refreshAll() }

    fun writeSetting(field: SettingField, newValue: String) {
        scope.launch {
            val result = CameraSession.client.setSetting(field.key, newValue)
            lastWriteStatus = if (result.isSuccess) {
                "Set ${field.label} = \"$newValue\" — camera replied: ${result.getOrNull()}"
            } else {
                "Set ${field.label} failed: ${result.exceptionOrNull()?.message}"
            }
            editField = null
            refreshAll()
        }
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
                onFieldTap = { editField = it },
                onToggle = { field, checked -> writeSetting(field, if (checked) "on" else "off") },
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
                items(fields) { field ->
                    SettingRow(
                        field,
                        values[field.key],
                        onTap = { editField = field },
                        onToggle = { checked -> writeSetting(field, if (checked) "on" else "off") },
                    )
                }

                lastWriteStatus?.let { status ->
                    item {
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
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
    }

    editField?.let { field ->
        EditFieldDialog(
            field = field,
            currentValue = values[field.key].orEmpty(),
            onDismiss = { editField = null },
            onSave = { newValue -> writeSetting(field, newValue) },
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
    padding: PaddingValues,
    values: Map<String, String>,
    onFieldTap: (SettingField) -> Unit,
    onToggle: (SettingField, Boolean) -> Unit,
) {
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

/**
 * GET_SINGLE_SETTING_OPTIONS comes back empty even for confirmed-correct
 * keys (verified live), so this firmware just doesn't support live option
 * enumeration for most fields — priority is: the confirmed EV list, then a
 * curated guess list (KNOWN_OPTIONS) for common fields, then a live query
 * attempt anyway (in case some field is the exception), then free-text.
 * "Other..." always escapes a shown list into manual entry.
 */
@Composable
private fun EditFieldDialog(field: SettingField, currentValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(currentValue) }
    val isEv = field.key.endsWith("_ev_bias")
    val curated = KNOWN_OPTIONS[field.key]

    // loading = null, resolved (success or empty) = non-null list.
    var options by remember {
        mutableStateOf<List<String>?>(
            when {
                isEv -> EV_OPTIONS
                curated != null -> curated
                else -> null
            },
        )
    }
    var manualEntry by remember { mutableStateOf(false) }

    LaunchedEffect(field.key) {
        if (isEv || curated != null) return@LaunchedEffect
        val result = CameraSession.client.getSettingOptions(field.key)
        options = result.getOrNull()?.let { json ->
            json.optJSONArray("param")?.let { arr -> (0 until arr.length()).mapNotNull { arr.opt(it)?.toString() } }
        } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.label) },
        text = {
            val current = options
            when {
                manualEntry || current?.isEmpty() == true -> androidx.compose.foundation.layout.Column {
                    if (current?.isEmpty() == true) {
                        Text(
                            "Camera reported no selectable options for \"${field.key}\" — enter a value manually.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Current: $currentValue",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("New value") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                current == null -> CircularProgressIndicator()
                else -> androidx.compose.foundation.layout.Column {
                    for (opt in current) {
                        Text(
                            opt,
                            modifier = Modifier.fillMaxWidth().clickable { onSave(opt) }.padding(vertical = 10.dp),
                        )
                    }
                    Text(
                        "Other...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().clickable { manualEntry = true }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (manualEntry || options?.isEmpty() == true) {
                Button(onClick = { onSave(text) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
