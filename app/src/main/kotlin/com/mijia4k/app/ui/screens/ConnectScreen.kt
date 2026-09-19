package com.mijia4k.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.ui.theme.MijiaTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ConnState { CHECKING, CONNECTED, FAILED }

/**
 * The app's landing page, styled after the stock Mi Home app's device page:
 * a hero image, then "Connect to camera" and "Album" rows. Unlike the stock
 * app (which waits for a tap before dialing the camera), this screen starts
 * connecting the moment it appears — by the time the user taps "Connect to
 * camera" the handshake is usually already done, and a slow/failed attempt
 * shows inline instead of only after a tap.
 */
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ConnState.CHECKING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun connect(navigateOnSuccess: Boolean) {
        state = ConnState.CHECKING
        errorMessage = null
        scope.launch {
            // CameraSession pins this app's traffic to the camera's
            // no-internet Wi-Fi before dialing — without that, Android routes
            // us over mobile data and 192.168.42.1 is simply unreachable.
            val result = CameraSession.connect(context)
            if (result.isSuccess) {
                state = ConnState.CONNECTED
                if (navigateOnSuccess) onConnected()
            } else {
                state = ConnState.FAILED
                errorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    // Always try to reach the camera whenever this screen is showing — the
    // user shouldn't have to tap anything for the app to start dialing in,
    // and coming back from the live screen should re-check rather than sit
    // on a stale "couldn't connect". A successful auto-connect does NOT jump
    // straight to the live screen (that would fight someone who came here to
    // open Album); tapping "Connect to camera" is what navigates in, and by
    // then the handshake is usually already done.
    LaunchedEffect(Unit) {
        while (true) {
            if (state != ConnState.CONNECTED) connect(navigateOnSuccess = false)
            delay(5000)
        }
    }

    Scaffold { padding ->
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
                },
        ) {
            Text(
                "Mi Action Camera 4K",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp),
            )

            CameraHero(
                connected = state == ConnState.CONNECTED,
                modifier = Modifier.fillMaxWidth().height(260.dp).padding(24.dp),
            )

            HorizontalDivider()

            ListItem(
                headlineContent = {
                    Text(
                        when (state) {
                            ConnState.CHECKING -> "Connecting to camera..."
                            ConnState.CONNECTED -> "Connect to camera"
                            ConnState.FAILED -> "Couldn't connect — tap to retry"
                        },
                    )
                },
                supportingContent = if (state == ConnState.FAILED) {
                    {
                        Text(
                            "Are you on the camera's \"MiCam_\" Wi-Fi hotspot?" +
                                (errorMessage?.let { " ($it)" } ?: ""),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    null
                },
                leadingContent = {
                    if (state == ConnState.CHECKING) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Wifi, contentDescription = null)
                    }
                },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                colors = ListItemDefaults.colors(),
                modifier = Modifier.clickable {
                    if (state == ConnState.CONNECTED) onConnected() else connect(navigateOnSuccess = true)
                },
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Album") },
                leadingContent = { Icon(Icons.Filled.Photo, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenGallery),
            )
            HorizontalDivider()

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                    Text("Open Wi-Fi settings")
                }
                TextButton(onClick = onOpenDiagnostics) {
                    Text("Diagnostics")
                }
            }
        }
    }
}

/**
 * A simple drawn illustration standing in for stock product photography (no
 * real camera imagery is bundled here) — a rounded camera body with a lens,
 * on a soft gradient backdrop, tinted by whether the camera is reachable.
 */
@Composable
private fun CameraHero(connected: Boolean, modifier: Modifier = Modifier) {
    val backdropTop = if (connected) MijiaTeal.copy(alpha = 0.35f) else Color(0xFFB0BEC5)
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(backdropTop, Color(0xFFECEFF1))),
                RoundedCornerShape(24.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color.White, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Videocam,
                contentDescription = null,
                tint = if (connected) MijiaTeal else Color(0xFF78909C),
                modifier = Modifier.size(52.dp),
            )
        }
        // A small "lens ring" accent, echoing the camera's circular lens.
        Canvas(modifier = Modifier.size(96.dp)) {
            drawCircle(
                color = (if (connected) MijiaTeal else Color(0xFF78909C)).copy(alpha = 0.25f),
                radius = size.minDimension / 2,
                center = Offset(size.width / 2, size.height / 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 24.dp)
                .size(10.dp)
                .background(if (connected) MijiaTeal else Color(0xFFB0BEC5), CircleShape),
        )
    }
}
