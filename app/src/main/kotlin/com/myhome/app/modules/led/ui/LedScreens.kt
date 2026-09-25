package com.myhome.app.modules.led.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhome.app.modules.led.ble.LedConnection
import com.myhome.app.modules.led.ble.LedHub
import com.myhome.app.modules.led.protocol.BledomProtocol
import com.myhome.app.modules.led.protocol.LED_EFFECTS
import com.myhome.app.modules.printer.ui.bluetoothPermissions
import com.myhome.app.modules.printer.ui.hasBluetoothPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF3F5A69)
private val Teal = Color(0xFF00BFA5)
private val Card = Color.White.copy(alpha = 0.82f)

private val SWATCHES = listOf(
    Color(0xFFFF0000), Color(0xFFFF6A00), Color(0xFFFFD400), Color(0xFF00E040),
    Color(0xFF00E5FF), Color(0xFF0050FF), Color(0xFF9C27FF), Color(0xFFFF2DA0), Color(0xFFFFFFFF),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedControlScreen(onBack: () -> Unit, onOpenScan: () -> Unit) {
    val context = LocalContext.current
    val connection = remember { LedHub.connection(context) }
    val state by connection.state.collectAsState()
    val ready = state is LedConnection.State.Ready

    var on by rememberSaveable { mutableStateOf(true) }
    var hue by rememberSaveable { mutableFloatStateOf(0f) }
    var sat by rememberSaveable { mutableFloatStateOf(1f) }
    var brightness by rememberSaveable { mutableFloatStateOf(100f) }
    var speed by rememberSaveable { mutableFloatStateOf(50f) }
    var effect by rememberSaveable { mutableIntStateOf(-1) }

    val color = Color.hsv(hue, sat, 1f)
    val saved = remember { mutableStateListOf<Int>().also { it.addAll(LedHub.customColors(context)) } }

    fun sendColor(c: Color) {
        effect = -1
        connection.send(
            LedConnection.KIND_COLOR,
            BledomProtocol.color((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()),
        )
    }

    fun applyColor(c: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hue = hsv[0]; sat = hsv[1]
        sendColor(c)
    }

    // Pick up where the last session left off.
    LaunchedEffect(Unit) {
        val last = LedHub.lastAddress(context) ?: return@LaunchedEffect
        // Try the saved strip a few times: it may be switching on or briefly out of reach.
        repeat(4) {
            val s = connection.state.value
            if (s is LedConnection.State.Ready || s is LedConnection.State.Connecting) return@LaunchedEffect
            if (hasBluetoothPermissions(context) && connection.bluetoothEnabled && connection.connect(last).isSuccess) return@LaunchedEffect
            delay(3_000)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFBEBDD), Color(0xFFE4EFF5), Color(0xFFD3E9E8))),
        ),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                Spacer(Modifier.weight(1f))
                BluetoothBadge(state, onClick = { if (ready) connection.disconnect() else onOpenScan() })
                Spacer(Modifier.size(14.dp))
            }
            Text(
                "LED Strip", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape)
                                .background(if (on && ready) color else Color(0xFFDDDDDD))
                                .border(2.dp, Ink.copy(alpha = 0.15f), CircleShape),
                        )
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(if (on) "On" else "Off", color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (ready) "Tap the button to switch the light" else "Connect to control it",
                                color = Ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = { on = !on; connection.send(LedConnection.KIND_POWER, BledomProtocol.power(on)) },
                            enabled = ready,
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(if (on && ready) Teal else Color(0xFFCFD8DC)),
                        ) { Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Power", tint = Color.White) }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Colour", color = Ink, fontWeight = FontWeight.SemiBold)
                    ColorWheel(hue, sat, ready, Modifier.align(Alignment.CenterHorizontally)) { h, s -> hue = h; sat = s; sendColor(Color.hsv(h, s, 1f)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (s in SWATCHES) Swatch(s, ready) { applyColor(s) }
                    }
                    Text("My colours", color = Ink, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (argb in saved) Swatch(Color(argb), ready, onLongClick = { saved.remove(argb); LedHub.setCustomColors(context, saved.toList()) }) { applyColor(Color(argb)) }
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, Ink.copy(alpha = 0.35f), CircleShape)
                                .clickable(enabled = ready && color.toArgb() !in saved) { saved.add(color.toArgb()); LedHub.setCustomColors(context, saved.toList()) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, contentDescription = "Save this colour", tint = Ink.copy(alpha = 0.6f)) }
                    }
                    Text("Tap + to save the colour on the wheel. Press and hold a saved colour to remove it.", color = Ink.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)

                    Text("Brightness  ${brightness.toInt()}%", color = Ink, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = brightness, valueRange = 0f..100f, enabled = ready,
                        onValueChange = {
                            brightness = it
                            connection.send(LedConnection.KIND_BRIGHTNESS, BledomProtocol.brightness(it.toInt()))
                        },
                    )
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Effects", color = Ink, fontWeight = FontWeight.SemiBold)
                    EffectWheel(LED_EFFECTS.map { it.name }, effect, ready) { index ->
                        effect = index
                        val e = LED_EFFECTS[index]
                        val rgb = e.rgb
                        connection.send(
                            LedConnection.KIND_EFFECT,
                            if (rgb != null) BledomProtocol.color(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
                            else BledomProtocol.effect(e.mode),
                        )
                    }
                    Text("Effect speed  ${speed.toInt()}%", color = Ink, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = speed, valueRange = 0f..100f, enabled = ready,
                        onValueChange = {
                            speed = it
                            connection.send(LedConnection.KIND_SPEED, BledomProtocol.speed(it.toInt()))
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** A small corner icon: teal Bluetooth when connected, grey crossed-out when not. Tap to connect or disconnect. */
@Composable
private fun BluetoothBadge(state: LedConnection.State, onClick: () -> Unit) {
    val ready = state is LedConnection.State.Ready
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (ready) Teal.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.7f))
            .clickable(enabled = state !is LedConnection.State.Connecting, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (state is LedConnection.State.Connecting) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (ready) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                contentDescription = if (ready) "Connected — tap to disconnect" else "Not connected — tap to connect",
                tint = if (ready) Teal else Color(0xFF888888),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---- scanning ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedScanScreen(onBack: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection = remember { LedHub.connection(context) }
    val state by connection.state.collectAsState()
    val found by connection.found.collectAsState()
    var permitted by remember { mutableStateOf(hasBluetoothPermissions(context)) }
    var enabled by remember { mutableStateOf(connection.bluetoothEnabled) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectingTo by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasBluetoothPermissions(context)
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        enabled = result.resultCode == Activity.RESULT_OK || connection.bluetoothEnabled
    }

    LaunchedEffect(Unit) { if (!permitted) permissionLauncher.launch(bluetoothPermissions()) }
    LaunchedEffect(permitted, enabled) { if (permitted && enabled) connection.startScan() }
    DisposableEffect(Unit) { onDispose { connection.stopScan() } }

    fun connect(address: String, name: String) {
        error = null
        connectingTo = address
        scope.launch {
            val result = connection.connect(address)
            connectingTo = null
            result.onSuccess {
                LedHub.remember(context, name, address)
                onConnected()
            }.onFailure { error = it.message ?: "Could not connect" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect an LED strip") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permitted -> Notice("Bluetooth permission needed", "Allow nearby devices so the app can find your light.", "Allow") {
                    permissionLauncher.launch(bluetoothPermissions())
                }
                !enabled -> Notice("Bluetooth is off", "Turn Bluetooth on to look for your light.", "Turn on") {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                else -> {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state is LedConnection.State.Scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(if (state is LedConnection.State.Scanning) "  Looking for lights…" else "Scan stopped", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { connection.startScan() }) { Text("Rescan") }
                    }
                    Text(
                        "Close the Lotus Lantern app first — a strip only talks to one phone at a time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(found, key = { it.address }) { device ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = connectingTo == null) { connect(device.address, device.name) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Teal)
                                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (connectingTo == device.address) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Swatch(color: Color, enabled: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(color)
            .border(1.5.dp, Ink.copy(alpha = 0.2f), CircleShape)
            .combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick),
    )
}
