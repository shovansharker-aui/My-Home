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
import androidx.compose.material3.RadioButton
import androidx.compose.ui.platform.LocalContext
import com.mijia4k.app.ui.AppOrientation
import com.mijia4k.app.ui.OrientationMode
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
import com.mijia4k.app.net.GENERAL_FIELDS
import com.mijia4k.app.net.MODE_LABELS
import com.mijia4k.app.net.SettingField
import com.mijia4k.app.net.SettingOptions
import com.mijia4k.app.net.displayValue
import com.mijia4k.app.net.fieldsFor
import com.mijia4k.app.net.isToggleOn
import com.mijia4k.app.net.toggleValue
import com.mijia4k.app.net.parseSettingsArray
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which screen inside Settings is showing — the stock app uses full screens, not dialogs. */
private sealed interface SettingsPage {
    data object Camera : SettingsPage
    data object WifiSettings : SettingsPage
    data object SdCard : SettingsPage
    data class Picker(val field: SettingField) : SettingsPage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()

    val values by CameraSession.settings.collectAsState()
    var page by remember { mutableStateOf<SettingsPage>(SettingsPage.Camera) }
    var lastWriteStatus by remember { mutableStateOf<String?>(null) }

    // Everything shown here is already in memory; this just freshens it.
    LaunchedEffect(Unit) { CameraSession.refreshSettings() }

    // The value changes on screen at once and the picker closes without
    // waiting; the camera's answer only matters if it refuses, in which case
    // the value snaps back and the reason is shown.
    fun writeSetting(field: SettingField, newValue: String, closePicker: Boolean = true) {
        if (closePicker) page = SettingsPage.Camera
        scope.launch {
            val result = CameraSession.writeSetting(field.key, newValue)
            lastWriteStatus = result.fold(
                onSuccess = { "${field.label} set to \"${displayValue(field.key, newValue)}\"" },
                onFailure = { "Couldn't set ${field.label}: ${it.message}" },
            )
        }
    }

    // The toolbar arrow walks back one level at a time; the system back
    // gesture used to skip all of that and drop the whole Settings screen
    // from any sub-page.
    BackHandler(enabled = page != SettingsPage.Camera) {
        page = SettingsPage.Camera
    }

    val title = when (val p = page) {
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
                                SettingsPage.Camera -> return@IconButton onBack()
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
                onOpenDiagnostics = onOpenDiagnostics,
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
    onOpenDiagnostics: () -> Unit,
) {
    var deviceInfo by remember { mutableStateOf<JSONObject?>(null) }
    var deviceInfoError by remember { mutableStateOf<String?>(null) }
    var storage by remember { mutableStateOf<AmbaSocketClient.StorageStatus?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showOrientation by remember { mutableStateOf(false) }
    var showShutdown by remember { mutableStateOf(false) }
    var shutdownResult by remember { mutableStateOf<String?>(null) }
    val offScope = rememberCoroutineScope()
    val orientation by AppOrientation.mode.collectAsState()
    val appContext = LocalContext.current
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
            InfoRow("Screen orientation", orientation.label, onClick = { showOrientation = true })
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
            ListItem(
                headlineContent = { Text("Turn off camera", color = Color(0xFFD64545)) },
                supportingContent = { Text("Switches off after about 2 minutes") },
                modifier = Modifier.clickable { showShutdown = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Diagnostics (advanced/debug)") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenDiagnostics),
            )
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


    if (showOrientation) {
        AlertDialog(
            onDismissRequest = { showOrientation = false },
            confirmButton = { TextButton(onClick = { showOrientation = false }) { Text("Close") } },
            title = { Text("Screen orientation") },
            text = {
                Column {
                    for (option in OrientationMode.entries) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                AppOrientation.set(appContext, option)
                                showOrientation = false
                            }.padding(vertical = 12.dp),
                        ) {
                            RadioButton(selected = option == orientation, onClick = null)
                            Text(option.label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
        )
    }

    if (showShutdown) {
        AlertDialog(
            onDismissRequest = { showShutdown = false; shutdownResult = null },
            title = { Text("Turn off camera") },
            text = {
                Text(
                    shutdownResult
                        ?: "The camera will switch itself off in about 2 minutes. The app stops talking to it so it can " +
                        "go idle, and puts your usual auto power-off time back the next time you connect.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (shutdownResult != null) {
                            showShutdown = false
                            shutdownResult = null
                        } else {
                            offScope.launch {
                                shutdownResult = CameraSession.scheduleShutdown(appContext).fold(
                                    onSuccess = { "Done. The camera will switch off shortly." },
                                    onFailure = { "Couldn't schedule it: ${it.message}" },
                                )
                            }
                        }
                    },
                ) { Text(if (shutdownResult != null) "OK" else "Turn off", color = if (shutdownResult == null) Color(0xFFD64545) else Color.Unspecified) }
            },
            dismissButton = {
                if (shutdownResult == null) TextButton(onClick = { showShutdown = false }) { Text("Cancel") }
            },
        )
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
 * highlighted and marked with a leading chevron. The lists come from the
 * camera's own option query (already cached by the time this opens), so every
 * setting is a choice — nothing is typed in by hand.
 */
@Composable
private fun OptionPickerPage(
    padding: PaddingValues,
    field: SettingField,
    currentValue: String,
    onPick: (String) -> Unit,
) {
    val allOptions by CameraSession.options.collectAsState()
    val mode by CameraSession.currentMode.collectAsState()
    val entry = allOptions[field.key]
    var failed by remember(field.key) { mutableStateOf(false) }

    LaunchedEffect(field.key) {
        if (CameraSession.options.value[field.key] == null) {
            failed = CameraSession.fetchOptions(field.key) == null
        }
    }

    when {
        entry == null && failed -> Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Couldn't load the choices for ${field.label} from the camera.")
            Button(
                onClick = { failed = false },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Retry") }
        }

        entry == null -> Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        else -> {
            // "Read-only" only counts if it was reported for the mode we're in now.
            val locked = !entry.settable && entry.fetchedInMode == mode
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (locked) {
                    item {
                        Text(
                            "${field.label} can't be changed in this shooting mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(entry.values) { option ->
                    val selected = option.equals(currentValue, ignoreCase = true)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !locked) { onPick(option) }
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
                            displayValue(field.key, option),
                            color = when {
                                locked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                selected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    HorizontalDivider()
                }
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
