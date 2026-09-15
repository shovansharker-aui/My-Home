package com.mijia4k.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.NetworkDiagnostics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<NetworkDiagnostics.DiagnosticsReport?>(null) }
    var running by remember { mutableStateOf(false) }

    fun runProbe() {
        running = true
        scope.launch {
            report = NetworkDiagnostics().run()
            running = false
        }
    }

    LaunchedEffect(Unit) { runProbe() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Button(onClick = { runProbe() }, enabled = !running) {
                Text(if (running) "Scanning..." else "Re-scan camera")
            }
            if (running) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            report?.let { r ->
                Text(
                    text = "Host reachable: ${r.hostReachable}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text("Ports", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(r.ports) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${p.port} (${p.label}): ", style = MaterialTheme.typography.bodySmall)
                            Text(if (p.open) "OPEN" else "closed", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (r.pathProbes.isNotEmpty()) {
                        item {
                            Text(
                                "HTTP path probes",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    items(r.pathProbes) { p -> ProbeCard(p) }

                    if (r.linkedAssetProbes.isNotEmpty()) {
                        item {
                            Text(
                                "Linked <script> assets (from the pages above)",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    items(r.linkedAssetProbes) { p -> ProbeCard(p) }

                    if (r.inlineScripts.isNotEmpty()) {
                        item {
                            Text(
                                "Inline <script> bodies",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    items(r.inlineScripts) { s ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                s.take(4000),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeCard(p: NetworkDiagnostics.PathProbeResult) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(p.path, style = MaterialTheme.typography.bodyMedium)
            Text(
                "status=${p.httpStatus} type=${p.contentType} err=${p.error}",
                style = MaterialTheme.typography.bodySmall,
            )
            p.bodyPreview?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
