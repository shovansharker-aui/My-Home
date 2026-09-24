package com.mijia4k.app.modules.printer.ui

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Print
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
import com.mijia4k.app.modules.printer.ble.PrinterConnection
import com.mijia4k.app.modules.printer.ble.PrinterHub
import com.mijia4k.app.modules.printer.protocol.CatProtocol
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
    var density by remember { mutableStateOf(PrinterHub.density(context)) }
    val ready = state is PrinterConnection.State.Ready

    // Pick up where the last session left off.
    LaunchedEffect(Unit) {
        val last = PrinterHub.lastAddress(context)
        val idle = state is PrinterConnection.State.Idle || state is PrinterConnection.State.Failed
        if (last != null && idle && hasBluetoothPermissions(context) && connection.bluetoothEnabled) connection.connect(last)
    }
    LaunchedEffect(ready) {
        while (ready) {
            connection.refreshStatus()
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ConnectionCard(
                        state = state,
                        status = status,
                        onConnect = onOpenScan,
                        onDisconnect = { connection.disconnect() },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.78f)).padding(16.dp)) {
                        Text("Print darkness", color = Ink, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (d in CatProtocol.Density.entries) {
                                FilterChip(
                                    selected = density == d,
                                    onClick = { density = d; PrinterHub.setDensity(context, d) },
                                    label = { Text(d.label) },
                                )
                            }
                        }
                    }
                }
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

@Composable
private fun ConnectionCard(
    state: PrinterConnection.State,
    status: CatProtocol.Status?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val ready = state as? PrinterConnection.State.Ready
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.85f)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (ready != null) Teal.copy(alpha = 0.18f) else Color(0xFFE6E6E6)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ready != null) Icons.Filled.Print else Icons.Filled.BluetoothDisabled,
                    contentDescription = null,
                    tint = if (ready != null) Teal else Color(0xFF888888),
                )
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    when (state) {
                        is PrinterConnection.State.Ready -> state.name
                        PrinterConnection.State.Connecting -> "Connecting…"
                        is PrinterConnection.State.Failed -> "Couldn't connect"
                        else -> "No printer connected"
                    },
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                val sub = when {
                    state is PrinterConnection.State.Failed -> state.message
                    ready != null && status != null -> when {
                        status.paperOut -> "Out of paper"
                        status.overheated -> "Too hot — let it cool"
                        status.lowBattery -> "Battery low"
                        else -> "Ready"
                    }
                    ready != null -> "Ready"
                    else -> "Tap Connect to find your printer"
                }
                Text(sub, color = Ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
            }
            if (state is PrinterConnection.State.Connecting) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ready != null) {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            } else {
                Button(onClick = onConnect, enabled = state !is PrinterConnection.State.Connecting) { Text("Connect") }
            }
        }
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
