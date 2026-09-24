package com.mijia4k.app.modules.printer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mijia4k.app.modules.printer.ble.PrinterConnection
import com.mijia4k.app.modules.printer.ble.PrinterHub
import com.mijia4k.app.modules.printer.protocol.CatProtocol
import kotlinx.coroutines.launch

fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun hasBluetoothPermissions(context: Context): Boolean =
    bluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * The frame every printing feature shares: what will come out of the printer
 * at the top, the feature's own controls in the middle, and a Print button
 * (or a way to connect) at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintTool(
    title: String,
    onBack: () -> Unit,
    onOpenScan: () -> Unit,
    bitmap: Bitmap?,
    emptyHint: String,
    controls: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection = remember { PrinterHub.connection(context) }
    val state by connection.state.collectAsState()
    val progress by connection.progress.collectAsState()
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val ready = state is PrinterConnection.State.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) }
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                Button(
                    onClick = {
                        if (!ready) {
                            onOpenScan()
                        } else if (bitmap != null) {
                            printing = true
                            message = null
                            scope.launch {
                                val job = CatProtocol.printJob(CatProtocol.toLines(bitmap), PrinterHub.density(context))
                                val result = connection.print(job)
                                message = result.fold(onSuccess = { "Printed" }, onFailure = { it.message ?: "Print failed" })
                                printing = false
                            }
                        }
                    },
                    enabled = !printing && (!ready || bitmap != null),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (printing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Text("  Printing…")
                    } else {
                        Text(if (ready) "Print" else "Connect a printer")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipPaper()
                    .heightIn(min = 120.dp, max = 340.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Text(emptyHint, color = Color(0xFF8A8A8A), modifier = Modifier.padding(24.dp))
                }
            }
            controls()
            Box(Modifier.size(8.dp))
        }
    }
}

private fun Modifier.clipPaper(): Modifier =
    this.background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFDADADA), RoundedCornerShape(12.dp))

/** A labelled row of choices, used for sizes, alignment and the like. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in options) {
                androidx.compose.material3.FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text(option)) },
                )
            }
        }
    }
}
