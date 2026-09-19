package com.mijia4k.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.AmbaSocketClient
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.net.parseSettingsArray
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SettingField(val label: String, val key: String, val isToggle: Boolean = false)

// Field labels/order below are transcribed from real screen recordings of
// the stock app. The *keys* are now confirmed too — read directly off the
// real camera's GET_ALL_CURRENT_SETTINGS dump (msg_id 3, which works
// perfectly), not guessed. That dump also revealed that the per-field
// GET_SETTING/GET_SINGLE_SETTING_OPTIONS commands (msg_id 1/9, both of which
// take a "type" argument) fail with the *same* error code regardless of
// which key is passed — so this screen reads every value from one
// GET_ALL_CURRENT_SETTINGS call instead of querying fields individually.

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

// "Camera Parameter" rows, in the stock app's exact order. Rotate reads back
// as "up"/"down" rather than on/off, so it needs its own on/off value pair.
private val ROTATE_FIELD = SettingField("Rotate", "auto_rotate", isToggle = true)
private val GENERAL_FIELDS = listOf(
    SettingField("Video Standard", "system_type"),
    SettingField("Beep Volume", "prompt_volume"),
    SettingField("Default Mode", "default_boot_mode"),
    ROTATE_FIELD,
    SettingField("Auto Screen Lock", "auto_lock_screen"),
    SettingField("Auto Power Off", "auto_power_off"),
    SettingField("Wi-Fi Auto On", "wifi_auto_start", isToggle = true),
)

// Values confirmed live against the real camera. Must match CAMERA_MODES in
// ShootScreen.kt.
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

private val EV_OPTIONS = listOf(
    "+2.0EV", "+1.7EV", "+1.3EV", "+1.0EV", "+0.7EV", "+0.3EV", "0",
    "-0.3EV", "-0.7EV", "-1.0EV", "-1.3EV", "-1.7EV", "-2.0EV",
)

/** A picker entry: what the stock app shows, and what the camera actually stores. */
private data class Choice(val label: String, val value: String)

private fun choices(vararg pairs: Pair<String, String>) = pairs.map { Choice(it.first, it.second) }

// Option lists transcribed from the stock app's own pickers, so these are the
// real choices the camera offers rather than guesses. Labels are what the
// stock app displays; values are what the camera reports back in its settings
// dump, which is lowercase and abbreviated ("mute", "off", "5min") and does
// *not* match the displayed label — writing the label verbatim would be
// rejected. "Other..." still escapes to manual entry if a value is wrong.
private val OPTION_CHOICES: Map<String, List<Choice>> = mapOf(
    "system_type" to choices("NTSC" to "NTSC", "PAL" to "PAL"),
    "prompt_volume" to choices("High" to "high", "Medium" to "medium", "Mute" to "mute"),
    "auto_lock_screen" to choices(
        "Never" to "off", "30s" to "30s", "1Min" to "1min", "2Min" to "2min", "5Min" to "5min",
    ),
    "auto_power_off" to choices(
        "Never" to "off", "2Min" to "2min", "5Min" to "5min",
        "10Min" to "10min", "20Min" to "20min", "30Min" to "30min",
    ),
    "default_boot_mode" to (
        listOf(Choice("Last used", "last_used")) + MODE_LABELS.map { Choice(it.value, it.key) }
        ),
    "video_metering_mode" to choices("Center" to "center", "Average" to "average", "Spot" to "spot"),
    "photo_metering_mode" to choices("Center" to "center", "Average" to "average", "Spot" to "spot"),
    "video_white_balance" to choices(
        "Auto" to "auto", "Sunny" to "sunny", "Cloudy" to "cloudy",
        "Incandescent" to "incandescent", "Fluorescent" to "fluorescent",
    ),
    "photo_wb" to choices(
        "Auto" to "auto", "Sunny" to "sunny", "Cloudy" to "cloudy",
        "Incandescent" to "incandescent", "Fluorescent" to "fluorescent",
    ),
    "video_stamp" to choices("Off" to "off", "Date" to "date", "Date & Time" to "date_time"),
    "photo_stamp" to choices("Off" to "off", "Date" to "date", "Date & Time" to "date_time"),
)

/** What to show in a row for a raw camera value — the stock app's label when we know it. */
private fun displayValue(key: String, raw: String?): String {
    if (raw.isNullOrBlank()) return "..."
    return OPTION_CHOICES[key]?.firstOrNull { it.value.equals(raw, ignoreCase = true) }?.label ?: raw
}

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

private fun isToggleOn(field: SettingField, value: String?): Boolean = when {
    value == null -> false
    field.key == ROTATE_FIELD.key -> value.equals("down", ignoreCase = true)
    else -> value.equals("on", true) || value == "1" || value.equals("true", true)
}

private fun toggleValue(field: SettingField, checked: Boolean): String = when (field.key) {
    ROTATE_FIELD.key -> if (checked) "down" else "up"
    else -> if (checked) "on" else "off"
}

/** Which screen inside Settings is showing — the stock app uses full screens, not dialogs. */
private sealed interface SettingsPage {
    data object Hub : SettingsPage
    data object Camera : SettingsPage
    data object WifiSettings : SettingsPage
    data object SdCard : SettingsPage
    data class Picker(val field: SettingField) : SettingsPage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()
    val modeValue by CameraSession.currentMode.collectAsState()
    val fields = remember(modeValue) { fieldsFor(modeValue) }

    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Hub) }
    var lastWriteStatus by remember { mutableStateOf<String?>(null) }

    suspend fun refreshAll() {
        values = CameraSession.client.getAllCurrentSettings().getOrNull()?.let { parseSettingsArray(it) } ?: values
    }

    LaunchedEffect(Unit) { refreshAll() }

    // The camera applies some changes slowly (mode switches have taken 30s+
    // to show up in its own settings dump). Reading back immediately showed
    // the *old* value and made a successful write look like it failed, so
    // re-read a few times before giving up on seeing the new value.
    fun writeSetting(field: SettingField, newValue: String, closePicker: Boolean = true) {
        scope.launch {
            val result = CameraSession.client.setSetting(field.key, newValue)
            lastWriteStatus = result.fold(
                onSuccess = { "${field.label} set to \"${displayValue(field.key, newValue)}\"" },
                onFailure = { "Couldn't set ${field.label}: ${it.message}" },
            )
            if (closePicker) page = SettingsPage.Camera
            if (result.isSuccess) {
                repeat(6) {
                    refreshAll()
                    if (values[field.key].equals(newValue, ignoreCase = true)) return@launch
                    delay(2000)
                }
            } else {
                refreshAll()
            }
        }
    }

    // The toolbar arrow walks back one level at a time; the system back
    // gesture used to skip all of that and drop the whole Settings screen
    // from any sub-page.
    BackHandler(enabled = page != SettingsPage.Hub) {
        page = if (page == SettingsPage.Camera) SettingsPage.Hub else SettingsPage.Camera
    }

    val title = when (val p = page) {
        SettingsPage.Hub -> "Settings"
        SettingsPage.Camera -> "Camera Settings"
        SettingsPage.WifiSettings -> "Wi-Fi settings"
        SettingsPage.SdCard -> "SD card"
        is SettingsPage.Picker -> p.field.label
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            page = when (page) {
                                SettingsPage.Hub -> return@IconButton onBack()
                                SettingsPage.Camera -> SettingsPage.Hub
                                else -> SettingsPage.Camera
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val p = page) {
            SettingsPage.Camera -> CameraSettingsList(
                padding = padding,
                values = values,
                onFieldTap = { page = SettingsPage.Picker(it) },
                onToggle = { field, checked -> writeSetting(field, toggleValue(field, checked), closePicker = false) },
                onOpenWifi = { page = SettingsPage.WifiSettings },
                onOpenSdCard = { page = SettingsPage.SdCard },
            )

            SettingsPage.WifiSettings -> WifiSettingsPage(
                padding = padding,
                onCancel = { page = SettingsPage.Camera },
                onSave = { ssid, password ->
                    scope.launch {
                        val result = CameraSession.client.setWifi(ssid, password)
                        lastWriteStatus = if (result.isSuccess) {
                            "Wi-Fi settings updated"
                        } else {
                            "Wi-Fi settings failed: ${result.exceptionOrNull()?.message}"
                        }
                        page = SettingsPage.Camera
                    }
                },
            )

            SettingsPage.SdCard -> SdCardPage(padding = padding)

            is SettingsPage.Picker -> OptionPickerPage(
                padding = padding,
                field = p.field,
                currentValue = values[p.field.key].orEmpty(),
                onPick = { writeSetting(p.field, it) },
            )

            SettingsPage.Hub -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        headlineContent = { Text("Camera Settings") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { page = SettingsPage.Camera },
                    )
                    HorizontalDivider(thickness = 8.dp)
                }

                item {
                    SectionHeader("${MODE_LABELS[modeValue] ?: modeValue} Parameter Settings")
                }
                items(fields) { field ->
                    SettingRow(
                        field,
                        values[field.key],
                        onTap = { page = SettingsPage.Picker(field) },
                        onToggle = { checked -> writeSetting(field, toggleValue(field, checked), closePicker = false) },
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
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingRow(field: SettingField, value: String?, onTap: () -> Unit, onToggle: (Boolean) -> Unit) {
    if (field.isToggle) {
        ListItem(
            headlineContent = { Text(field.label) },
            trailingContent = { Switch(checked = isToggleOn(field, value), onCheckedChange = onToggle) },
        )
    } else {
        ListItem(
            headlineContent = { Text(field.label) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayValue(field.key, value), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            },
            modifier = Modifier.clickable(onClick = onTap),
        )
    }
    HorizontalDivider()
}

/** A "label — value >" row whose value isn't a camera setting (device info, storage). */
@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onClick != null) Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
    HorizontalDivider()
}

@Composable
private fun CameraSettingsList(
    padding: PaddingValues,
    values: Map<String, String>,
    onFieldTap: (SettingField) -> Unit,
    onToggle: (SettingField, Boolean) -> Unit,
    onOpenWifi: () -> Unit,
    onOpenSdCard: () -> Unit,
) {
    var deviceInfo by remember { mutableStateOf<JSONObject?>(null) }
    var deviceInfoError by remember { mutableStateOf<String?>(null) }
    var storage by remember { mutableStateOf<AmbaSocketClient.StorageStatus?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showTimeSync by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CameraSession.client.getDeviceInfo()
            .onSuccess { deviceInfo = it }
            .onFailure { deviceInfoError = it.message }
        storage = CameraSession.client.getStorageStatus()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        item { SectionHeader("Camera Parameter") }
        items(GENERAL_FIELDS) { field ->
            SettingRow(field, values[field.key], onTap = { onFieldTap(field) }, onToggle = { onToggle(field, it) })
        }
        item {
            ListItem(
                headlineContent = { Text("Wi-Fi settings") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenWifi),
            )
            HorizontalDivider()
            InfoRow("SD card", formatStorage(storage?.freeBytes, storage?.totalBytes), onClick = onOpenSdCard)
        }

        item { SectionHeader("Device Info") }
        item {
            val info = deviceInfo
            if (info == null) {
                InfoRow("Type", deviceInfoError?.let { "error" } ?: "...")
            } else {
                InfoRow("Type", info.firstOf("model", "device_model", "camera_type", "chip", "brand"))
                InfoRow("Serial No.", info.firstOf("serial_number", "serial_num", "sn", "serial"))
                InfoRow(
                    "Firmware Version",
                    info.firstOf("firmware_ver", "fw_ver", "fwver", "firm_ver", "firmware", "version", "sw_ver"),
                )
            }
            InfoRow("Camera Time", values.firstOf("camera_clock", "date_time", "system_time"), onClick = { showTimeSync = true })
            ListItem(
                headlineContent = { Text("Restore camera settings") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showRestoreConfirm = true },
            )
            HorizontalDivider()
        }

        deviceInfoError?.let { error ->
            item {
                Text(
                    "Couldn't read device info: $error",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    if (showTimeSync) {
        CameraTimeSyncDialog(onDismiss = { showTimeSync = false })
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            confirmButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("OK") } },
            title = { Text("Restore camera settings") },
            // Deliberately not wired to a real command: there's no confirmed
            // protocol message for "restore defaults", and guessing at one on
            // hardware that would then wipe its config and reboot isn't a
            // safe gamble. Use the camera's own screen for this.
            text = {
                Text(
                    "This app doesn't know the camera's real \"restore defaults\" command yet, so this is intentionally " +
                        "inert rather than guessing at one on your actual camera. Use the camera's own menu to reset it.",
                )
            },
        )
    }
}

@Composable
private fun CameraTimeSyncDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera Time") },
        text = { Text(status ?: "Sync camera time with the clock on your phone?") },
        confirmButton = {
            TextButton(
                onClick = {
                    if (status != null) return@TextButton onDismiss()
                    scope.launch {
                        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                        val result = CameraSession.client.setSetting("camera_clock", now)
                        status = if (result.isSuccess) {
                            "Camera clock set to $now"
                        } else {
                            "Failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                },
            ) { Text("OK", color = Color.Red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SdCardPage(padding: PaddingValues) {
    var storage by remember { mutableStateOf<AmbaSocketClient.StorageStatus?>(null) }
    var showFormatConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        storage = CameraSession.client.getStorageStatus()
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Text("SD card storage", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(formatStorage(storage?.freeBytes, storage?.totalBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val total = storage?.totalBytes
        val free = storage?.freeBytes
        val used = if (total != null && free != null && total > 0) {
            ((total - free).toFloat() / total).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(progress = { used }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        storage?.let { st ->
            val photos = st.remainingPhotos
            val videoSeconds = st.remainingVideoSeconds
            if (photos == null && videoSeconds == null) return@let
            Text(
                listOfNotNull(
                    photos?.let { "about $it photos" },
                    videoSeconds?.let { "${it / 60} min of video" },
                ).joinToString(" or ", prefix = "Room for "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "* Formatting the SD card will erase all the files stored on the card. Please back up the files before " +
                "formatting.\nIn order to conveniently browse locally stored photos and videos, the files on the SD " +
                "card are not encrypted. Please keep it properly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { showFormatConfirm = true }) {
                Text("Format SD Card", color = Color.Red, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showFormatConfirm) {
        AlertDialog(
            onDismissRequest = { showFormatConfirm = false },
            confirmButton = { TextButton(onClick = { showFormatConfirm = false }) { Text("OK") } },
            title = { Text("Format SD Card") },
            // Same reasoning as "restore camera settings", but higher stakes:
            // a guessed format command would irreversibly wipe the card.
            text = {
                Text(
                    "This app doesn't know the camera's real format command yet, and guessing at one would risk " +
                        "irreversibly erasing the card. Use the camera's own menu to format it.",
                )
            },
        )
    }
}

@Composable
private fun WifiSettingsPage(padding: PaddingValues, onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var ssid by remember { mutableStateOf("MiCam_") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Text("Please connect Wi-Fi name: MiCam_xxxxxx", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Text("Wi-Fi password", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Enter new password (8~16 numbers)") },
            singleLine = true,
            visualTransformation = if (showPassword) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showPassword = !showPassword }.padding(top = 12.dp),
        ) {
            Switch(checked = showPassword, onCheckedChange = { showPassword = it }, modifier = Modifier.size(width = 52.dp, height = 32.dp))
            Text("Show password", modifier = Modifier.padding(start = 12.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = { onSave(ssid, password) },
                enabled = ssid.isNotBlank() && password.length in 8..16,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("OK") }
        }
    }
}

/**
 * A full-screen option list, matching the stock app: the current value is
 * highlighted and marked with a leading chevron. GET_SINGLE_SETTING_OPTIONS
 * comes back empty even for confirmed-correct keys on this firmware, so the
 * lists come from the confirmed EV values, then the stock app's own pickers
 * (KNOWN_OPTIONS), then a live query attempt, then free text.
 */
@Composable
private fun OptionPickerPage(
    padding: PaddingValues,
    field: SettingField,
    currentValue: String,
    onPick: (String) -> Unit,
) {
    val isEv = field.key.endsWith("_ev_bias")
    val curated = OPTION_CHOICES[field.key]
        ?: EV_OPTIONS.map { Choice(it, it) }.takeIf { isEv }
    var options by remember { mutableStateOf(curated) }
    var manualEntry by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(currentValue) }

    LaunchedEffect(field.key) {
        if (curated != null) return@LaunchedEffect
        options = CameraSession.client.getSettingOptions(field.key).getOrNull()?.let { json ->
            json.optJSONArray("param")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.opt(i)?.toString()?.let { Choice(it, it) } }
            }
        } ?: emptyList()
    }

    val current = options
    when {
        current == null -> Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        manualEntry || current.isEmpty() -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (current.isEmpty()) {
                Text(
                    "The camera doesn't report selectable options for \"${field.key}\" — enter a value manually.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Current: $currentValue", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("New value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = { onPick(text) }, modifier = Modifier.padding(top = 16.dp)) { Text("Save") }
        }

        else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(current) { option ->
                val selected = option.value.equals(currentValue, ignoreCase = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(option.value) }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    } else {
                        Spacer(Modifier.size(width = 32.dp, height = 1.dp))
                    }
                    Text(
                        option.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
            }
            item {
                Text(
                    "Other...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable { manualEntry = true }.padding(16.dp),
                )
            }
        }
    }
}

private fun formatStorage(free: Long?, total: Long?): String {
    fun gb(bytes: Long) = "%.1fG".format(bytes / 1024.0 / 1024.0 / 1024.0)
    // The camera answers GET_SPACE with 0/-1 for type strings it doesn't
    // recognise, which would otherwise render as a bogus "0.0G/-0.0G".
    val f = free?.takeIf { it > 0 }
    val t = total?.takeIf { it > 0 }
    return when {
        f != null && t != null -> "${gb(f)}/${gb(t)}"
        t != null -> gb(t)
        else -> "—"
    }
}

/** The exact key names in these replies aren't documented, so try the likely spellings. */
private fun JSONObject.firstOf(vararg keys: String): String {
    val param = optJSONObject("param") ?: this
    for (key in keys) param.optString(key).takeIf { it.isNotBlank() }?.let { return it }
    return "—"
}

private fun Map<String, String>.firstOf(vararg keys: String): String {
    for (key in keys) this[key]?.takeIf { it.isNotBlank() }?.let { return it }
    return "—"
}
