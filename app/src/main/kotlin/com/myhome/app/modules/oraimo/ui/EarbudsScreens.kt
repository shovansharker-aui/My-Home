package com.myhome.app.modules.oraimo.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhome.app.home.AutoConnectWhileOpen
import com.myhome.app.home.rememberBluetoothEnabler
import com.myhome.app.modules.oraimo.link.EarbudsConnection
import com.myhome.app.modules.oraimo.link.EarbudsHub
import com.myhome.app.modules.oraimo.protocol.EarbudsProtocol
import com.myhome.app.modules.printer.ui.bluetoothPermissions
import com.myhome.app.modules.printer.ui.hasBluetoothPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF3F5A69)
private val Teal = Color(0xFF00BFA5)
private val Card = Color.White.copy(alpha = 0.82f)

/** What the stock app calls the equaliser presets, in the order the earbuds number them. */
private val EQ_PRESETS = listOf(0 to "Standard", 2 to "HavyBass™", 3 to "Rock", 4 to "Jazz", 5 to "Vocal", 1 to "Custom")
private val EQ_BANDS = listOf("50 Hz", "100 Hz", "400 Hz", "1 kHz", "2.5 kHz", "6.3 kHz", "16 kHz")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarbudsScreen(onBack: () -> Unit, onPick: () -> Unit) {
    val context = LocalContext.current
    val connection = remember { EarbudsHub.connection(context) }
    val state by connection.state.collectAsState()
    val info by connection.info.collectAsState()
    val ready = state is EarbudsConnection.State.Ready

    // While this page is open: reach the earbuds used last time (or the only oraimo pair that is paired) as soon as they are available.
    var userOff by remember { mutableStateOf(false) }
    AutoConnectWhileOpen(
        paused = userOff,
        canTry = { connection.state.value.let { it is EarbudsConnection.State.Idle || it is EarbudsConnection.State.Failed } },
        target = {
            EarbudsHub.lastAddress(context)
                ?: connection.paired().singleOrNull { it.name.contains("oraimo", ignoreCase = true) }?.address
        },
        connect = { address -> if (connection.connect(address).isSuccess) EarbudsHub.remember(context, address) },
    )
    val enableBluetooth = rememberBluetoothEnabler { if (EarbudsHub.lastAddress(context) != null || connection.paired().any { it.name.contains("oraimo", ignoreCase = true) }) userOff = false else onPick() }
    // Keep the readings fresh.
    LaunchedEffect(ready) {
        while (ready) {
            delay(20_000)
            connection.refresh()
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
                BluetoothBadge(state, onClick = { if (ready) { userOff = true; connection.disconnect() } else { userOff = false; enableBluetooth() } })
                Spacer(Modifier.size(14.dp))
            }
            Text(
                "Oraimo Sound", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 26.dp),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GreenCaseAnimation(Modifier.align(Alignment.CenterHorizontally))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card).padding(18.dp)) {
                    Text(
                        info.name ?: (state as? EarbudsConnection.State.Ready)?.name ?: "Earbuds",
                        color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when (val s = state) {
                            is EarbudsConnection.State.Ready -> "Connected"
                            EarbudsConnection.State.Connecting -> "Connecting…"
                            is EarbudsConnection.State.Failed -> s.message
                            else -> "Tap the Bluetooth icon to choose your earbuds"
                        },
                        color = Ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Battery("Left", info.left)
                        Battery("Right", info.right)
                        Battery("Case", info.case)
                    }
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Equaliser", color = Ink, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EQ_PRESETS.forEach { (id, name) ->
                            FilterChip(
                                selected = info.eqPreset == id,
                                enabled = ready,
                                onClick = { if (id == 1) connection.setCustomEq(info.eqGains ?: List(7) { 0 }) else connection.setEq(id) },
                                label = { Text(name) },
                            )
                        }
                    }
                    if (info.eqPreset == 1) {
                        val gains = info.eqGains ?: List(7) { 0 }
                        EQ_BANDS.forEachIndexed { i, band ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(band, color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                                Slider(
                                    value = gains[i].toFloat(), valueRange = -10f..10f, steps = 19, enabled = ready,
                                    onValueChange = { v -> connection.setCustomEq(gains.toMutableList().also { it[i] = v.toInt() }) },
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${gains[i]}", color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(28.dp))
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Game mode", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("Lower delay for games and video", color = Ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = info.gameMode == true, enabled = ready, onCheckedChange = { connection.setGameMode(it) })
                }
            }
        }
    }
}

@Composable
private fun Battery(label: String, percent: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f }, modifier = Modifier.size(64.dp), strokeWidth = 5.dp,
                color = Ink.copy(alpha = 0.10f), trackColor = Color.Transparent,
            )
            CircularProgressIndicator(
                progress = { (percent ?: 0) / 100f }, modifier = Modifier.size(64.dp), strokeWidth = 5.dp,
                color = if ((percent ?: 100) <= 15) Color(0xFFD9534F) else Teal, trackColor = Color.Transparent,
            )
            Text(percent?.let { "$it%" } ?: "–", color = Ink, fontWeight = FontWeight.SemiBold)
        }
        Text(label, color = Ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A small corner icon: teal Bluetooth when connected, grey crossed-out when not. Tap to connect or choose earbuds. */
@Composable
private fun BluetoothBadge(state: EarbudsConnection.State, onClick: () -> Unit) {
    val ready = state is EarbudsConnection.State.Ready
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (ready) Teal.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.7f))
            .clickable(enabled = state !is EarbudsConnection.State.Connecting, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (state is EarbudsConnection.State.Connecting) {
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

// ---- choosing the earbuds ------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarbudsPickScreen(onBack: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection = remember { EarbudsHub.connection(context) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose your earbuds") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permitted -> Notice("Bluetooth permission needed", "Allow nearby devices so the app can reach your earbuds.", "Allow") {
                    permissionLauncher.launch(bluetoothPermissions())
                }
                !enabled -> Notice("Bluetooth is off", "Turn Bluetooth on to reach your earbuds.", "Turn on") {
                    enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                else -> {
                    Text(
                        "Pick the earbuds from the devices paired with this phone. Pair new ones in Android's Bluetooth settings first, and keep them connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(connection.paired(), key = { it.address }) { device ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = connectingTo == null) {
                                        error = null
                                        connectingTo = device.address
                                        scope.launch {
                                            val r = connection.connect(device.address)
                                            connectingTo = null
                                            r.onSuccess {
                                                EarbudsHub.remember(context, device.address)
                                                onConnected()
                                            }.onFailure { error = it.message }
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Teal)
                                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (connectingTo == device.address) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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
