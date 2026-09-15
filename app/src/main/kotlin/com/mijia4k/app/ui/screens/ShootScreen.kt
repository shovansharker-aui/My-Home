package com.mijia4k.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.mijia4k.app.net.CameraEndpoints
import com.mijia4k.app.net.CameraSession
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connected by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        connected = CameraSession.connect().isSuccess
    }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(RtspMediaSource.Factory())
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(CameraEndpoints.RTSP_URL))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    fun runCommand(label: String, block: suspend () -> Result<*>) {
        busy = true
        scope.launch {
            val result = block()
            status = if (result.isSuccess) "$label OK" else "$label failed: ${result.exceptionOrNull()?.message}"
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shoot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                )
            }

            if (!connected) {
                Text(
                    "Not connected to the camera's control socket — shutter/record won't work.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    enabled = connected && !busy,
                    onClick = {
                        runCommand("Photo") { CameraSession.client.takePhoto() }
                        Toast.makeText(context, "Shutter triggered", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Take photo")
                    Text("  Photo")
                }

                Button(
                    enabled = connected && !busy,
                    onClick = {
                        if (recording) {
                            runCommand("Stop recording") { CameraSession.client.stopRecording() }
                        } else {
                            runCommand("Start recording") { CameraSession.client.startRecording() }
                        }
                        recording = !recording
                    },
                ) {
                    Icon(
                        if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = if (recording) "Stop recording" else "Start recording",
                    )
                    Text(if (recording) "  Stop" else "  Record")
                }
            }
        }
    }
}
