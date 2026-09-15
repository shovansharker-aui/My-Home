package com.mijia4k.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.CameraEndpoints
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.net.NetworkBinder
import kotlinx.coroutines.launch

private enum class ConnState { UNKNOWN, CHECKING, CONNECTED, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ConnState.UNKNOWN) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun connect() {
        state = ConnState.CHECKING
        errorMessage = null
        // The camera's hotspot has no internet, so Android will otherwise
        // route our traffic through mobile data / another Wi-Fi instead —
        // pin this app's networking to the camera's Wi-Fi explicitly.
        NetworkBinder.bindToCameraWifi(context)
        scope.launch {
            val result = CameraSession.connect()
            if (result.isSuccess) {
                state = ConnState.CONNECTED
                onConnected()
            } else {
                state = ConnState.FAILED
                errorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    // Auto-connect on open: the camera's own screen sits on "connecting..."
    // until a client completes the control-socket handshake, and the app
    // should drop straight into the live-preview screen once that's done
    // rather than making the user tap through, so do both as soon as this
    // screen is shown.
    LaunchedEffect(Unit) { connect() }

    Scaffold(topBar = { TopAppBar(title = { Text("Mijia 4K") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier)
            Text(
                text = "Connect your phone's Wi-Fi to the camera's hotspot " +
                    "(SSID starts with \"MiCam_\"), then check below. If your phone keeps hopping " +
                    "back to another network, turn off \"Switch to mobile data automatically\" / " +
                    "\"Avoid poor connections\" for this Wi-Fi in Android's Wi-Fi settings — the app " +
                    "pins its own traffic to the camera regardless, but Android may still show you a " +
                    "different network as connected unless that's off.",
                style = MaterialTheme.typography.bodyMedium,
            )

            when (state) {
                ConnState.UNKNOWN -> Text("Not checked yet")
                ConnState.CHECKING -> Text("Connecting to ${CameraEndpoints.HOST} ...")
                ConnState.CONNECTED -> Text("Connected — camera's screen should now show its main screen")
                ConnState.FAILED -> Text(
                    "Couldn't connect${errorMessage?.let { ": $it" } ?: ""}. Are you on the MiCam_ hotspot?",
                )
            }

            Button(onClick = { connect() }) {
                Text(if (state == ConnState.CHECKING) "Connecting..." else "Reconnect")
            }

            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }) {
                Text("Open Wi-Fi settings")
            }

            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Diagnostics")
            }
        }
    }
}
