package com.mijia4k.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.CameraEndpoints
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.net.NetworkBinder
import com.mijia4k.app.net.WifiConnector
import com.mijia4k.app.net.WifiCredentialsStore
import kotlinx.coroutines.launch

private enum class ConnState { UNKNOWN, CHECKING, CONNECTED, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ConnState.UNKNOWN) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusDetail by remember { mutableStateOf<String?>(null) }

    var ssid by remember { mutableStateOf(WifiCredentialsStore.ssid(context)) }
    var password by remember { mutableStateOf(WifiCredentialsStore.password(context)) }
    var passwordVisible by remember { mutableStateOf(false) }

    fun connect() {
        state = ConnState.CHECKING
        errorMessage = null
        scope.launch {
            // If we know the hotspot's credentials, actively join it first —
            // don't just wait for the user to have already done that
            // manually. Android turns Wi-Fi on by itself for this.
            if (ssid.isNotBlank()) {
                statusDetail = "Joining Wi-Fi \"$ssid\"..."
                WifiConnector.connect(context, ssid, password)
            }
            // Whether or not that connected, pin our own traffic to whatever
            // no-internet Wi-Fi is around — covers the case where the phone
            // was already manually joined to the hotspot.
            NetworkBinder.bindToCameraWifi(context)

            statusDetail = "Connecting to camera at ${CameraEndpoints.HOST}..."
            val result = CameraSession.connect()
            if (result.isSuccess) {
                state = ConnState.CONNECTED
                onConnected()
            } else {
                state = ConnState.FAILED
                errorMessage = result.exceptionOrNull()?.message
            }
            statusDetail = null
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
                // Gallery (with previously-synced previews) should still be
                // reachable by swipe even when the camera itself isn't
                // connected yet.
                .pointerInput(Unit) {
                    var dragAccumulator = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragEnd = {
                            if (dragAccumulator < -150f) onOpenGallery()
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier)
            Text(
                text = "Enter the camera hotspot's Wi-Fi name/password below so the app can join it " +
                    "on its own. If your phone still shows a different network as \"connected\", turn off " +
                    "\"Switch to mobile data automatically\" / \"Avoid poor connections\" for this Wi-Fi in " +
                    "Android's Wi-Fi settings — the app pins its own traffic to the camera regardless.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("Camera Wi-Fi name (SSID)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Wi-Fi password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { WifiCredentialsStore.save(context, ssid, password) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Wi-Fi credentials")
            }

            when (state) {
                ConnState.UNKNOWN -> Text("Not checked yet")
                ConnState.CHECKING -> Text(statusDetail ?: "Connecting...")
                ConnState.CONNECTED -> Text("Connected — camera's screen should now show its main screen")
                ConnState.FAILED -> Text(
                    "Couldn't connect${errorMessage?.let { ": $it" } ?: ""}. Are you on/near the MiCam_ hotspot?",
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

            OutlinedButton(onClick = onOpenGallery, modifier = Modifier.fillMaxWidth()) {
                Text("Gallery (swipe left also works)")
            }
            OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Diagnostics")
            }
        }
    }
}
