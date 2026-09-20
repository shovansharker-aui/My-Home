package com.mijia4k.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.CameraSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's landing page: a calm landscape and one line of text. While the
 * camera's hotspot can't be reached it says "Connect to the camera"; once the
 * handshake succeeds it says "Camera connected" and waits — the live view opens
 * only when the screen is tapped.
 */
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var switchingOff by remember { mutableStateOf(CameraSession.shutdownPending(context)) }

    fun connect() {
        scope.launch {
            // CameraSession pins this app's traffic to the camera's
            // no-internet Wi-Fi before dialing — without that, Android routes
            // us over mobile data and 192.168.42.1 is simply unreachable.
            val ok = CameraSession.connect(context).isSuccess
            connected = ok
        }
    }

    // Keep trying whenever this screen is showing, so joining the camera's
    // Wi-Fi is all it takes.
    LaunchedEffect(Unit) {
        while (true) {
            switchingOff = CameraSession.shutdownPending(context)
            if (!connected || !CameraSession.client.isConnected) {
                connected = false
                connect()
            }
            delay(4000)
        }
    }

    Scaffold { padding ->
        Landing(
            padding = padding,
            connected = connected,
            switchingOff = switchingOff,
            onTitleTap = { if (connected) onConnected() else connect() },
            onOpenWifi = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            onOpenGallery = onOpenGallery,
            onOpenDiagnostics = onOpenDiagnostics,
        )
    }
}

@Composable
private fun Landing(
    padding: PaddingValues,
    connected: Boolean,
    switchingOff: Boolean,
    onTitleTap: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val ink = Color(0xFF3F5A69)
    Box(
        Modifier
            .fillMaxSize()
            // Tap anywhere to open the live view once connected (or to retry).
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onTitleTap,
            )
            // Swipe left for the Album, which works with or without the camera.
            .pointerInput(Unit) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (travelled < -150f) onOpenGallery() },
                ) { change, amount ->
                    change.consume()
                    travelled += amount
                }
            },
    ) {
        NatureScene(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.10f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onTitleTap,
                    )
                    .padding(24.dp),
            ) {
                Text(
                    when {
                        switchingOff -> "Camera is switching off"
                        connected -> "Camera connected"
                        else -> "Connect to the camera"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = ink,
                )
                if (connected) {
                    Text(
                        "Tap to open",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(0.90f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onOpenWifi) { Text("Wi-Fi settings", color = ink) }
                TextButton(onClick = onOpenGallery) { Text("Album", color = ink) }
                TextButton(onClick = onOpenDiagnostics) { Text("Diagnostics", color = ink) }
            }
        }
    }
}
