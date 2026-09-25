package com.myhome.app.modules.printer.ui

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhome.app.modules.printer.ble.PrinterConnection
import com.myhome.app.modules.printer.ble.PrinterHub
import com.myhome.app.modules.printer.protocol.CatProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ToolEntry(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

private val Ink = Color(0xFF3F5A69)
private val Teal = Color(0xFF00BFA5)

@Composable
fun PrinterHomeScreen(
    onBack: () -> Unit,
    tools: List<ToolEntry>,
    onOpenTool: (String) -> Unit,
    onOpenScan: () -> Unit,
) {
    val context = LocalContext.current
    val connection = remember { PrinterHub.connection(context) }
    val state by connection.state.collectAsState()
    val status by connection.status.collectAsState()
    val battery by connection.battery.collectAsState()
    val ready = state is PrinterConnection.State.Ready

    // Pick up where the last session left off.
    LaunchedEffect(Unit) {
        val last = PrinterHub.lastAddress(context) ?: return@LaunchedEffect
        // Try the saved printer a few times: it may be switching on or briefly out of reach.
        repeat(4) {
            val s = connection.state.value
            if (s is PrinterConnection.State.Ready || s is PrinterConnection.State.Connecting) return@LaunchedEffect
            if (hasBluetoothPermissions(context) && connection.bluetoothEnabled && connection.connect(last).isSuccess) return@LaunchedEffect
            delay(3_000)
        }
    }
    LaunchedEffect(ready) {
        while (ready) {
            connection.refreshStatus()
            connection.refreshBattery()
            delay(5_000)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFBEBDD), Color(0xFFE4EFF5), Color(0xFFD3E9E8))),
        ),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                Spacer(Modifier.weight(1f))
                if (ready) BatteryBadge(battery, status?.lowBattery == true)
                Spacer(Modifier.size(8.dp))
                BluetoothBadge(state, onClick = { if (ready) connection.disconnect() else onOpenScan() })
                Spacer(Modifier.size(14.dp))
            }
            Column(Modifier.padding(horizontal = 26.dp).padding(bottom = 6.dp)) {
                Text("Mini Printer", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                gridItems(tools, key = { it.route }) { tool ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White.copy(alpha = 0.78f))
                            .clickable { onOpenTool(tool.route) }
                            .padding(16.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFFFBE6D6), Color(0xFFC3E0E6)))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(tool.icon, contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp)) }
                        Spacer(Modifier.size(12.dp))
                        Text(tool.title, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(tool.subtitle, color = Ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** A small corner icon: teal Bluetooth when connected, grey crossed-out when not. Tap to connect or disconnect. */
@Composable
private fun BluetoothBadge(state: PrinterConnection.State, onClick: () -> Unit) {
    val ready = state is PrinterConnection.State.Ready
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (ready) Teal.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.7f))
            .clickable(enabled = state !is PrinterConnection.State.Connecting, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (state is PrinterConnection.State.Connecting) {
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

/** The printer's charge: an icon, with the percentage when the printer reports one. */
@Composable
private fun BatteryBadge(percent: Int?, low: Boolean) {
    val warn = low || (percent != null && percent <= 15)
    Row(
        Modifier.height(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (warn) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
            contentDescription = "Battery",
            tint = if (warn) Color(0xFFD9534F) else Teal,
            modifier = Modifier.size(22.dp),
        )
        if (percent != null) Text("$percent%", color = Ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 2.dp))
    }
}

// ---- scanning ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection = remember { PrinterHub.connection(context) }
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
                PrinterHub.remember(context, name, address)
                onConnected()
            }.onFailure { error = it.message ?: "Could not connect" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect a printer") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permitted -> Notice(
                    "Bluetooth permission needed",
                    "Allow nearby devices so the app can find your printer.",
                    "Allow",
                ) { permissionLauncher.launch(bluetoothPermissions()) }

                !enabled -> Notice(
                    "Bluetooth is off",
                    "Turn Bluetooth on to look for your printer.",
                    "Turn on",
                ) { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }

                else -> {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state is PrinterConnection.State.Scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            if (state is PrinterConnection.State.Scanning) "  Looking for printers…" else "Scan stopped",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { connection.startScan() }) { Text("Rescan") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
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
                                if (connectingTo == device.address) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                                }
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
