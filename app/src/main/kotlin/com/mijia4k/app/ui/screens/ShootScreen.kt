package com.mijia4k.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.mijia4k.app.net.CameraEndpoints
import com.mijia4k.app.net.CameraHttpClient
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.net.LocalPreviewCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class ModeOption(val label: String, val value: String)

// The camera's own GET_SINGLE_SETTING_OPTIONS for camera_mode has come back
// empty in testing so far, so this is the fallback shown until that's fixed
// or proven otherwise: the mode set + value strings the user described from
// years of using the camera's physical UI, with value strings guessed from
// the one confirmed real example in public Ambarella protocol research
// ("normal_record" for video). Tapping a mode still calls the real
// SET_SETTING command and shows the camera's actual accept/reject response
// in the status line, so wrong guesses surface immediately instead of
// silently doing nothing.
private val FALLBACK_MODES = listOf(
    ModeOption("Photo", "photo"),
    ModeOption("Video", "normal_record"),
    ModeOption("Time Lapse", "time_lapse_record"),
    ModeOption("Loop Video", "loop_record"),
    ModeOption("Burst", "burst"),
    ModeOption("Slow Motion", "slow_motion_record"),
    ModeOption("Video+Photo", "video_photo"),
    ModeOption("Timer", "self_timer"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootScreen(
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connected by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var modes by remember { mutableStateOf(FALLBACK_MODES) }
    var currentMode by remember { mutableStateOf<String?>(null) }
    var liveInfo by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    fun loadModes() {
        scope.launch {
            val fromCamera = CameraSession.client.getSettingOptions("camera_mode")
                .getOrNull()?.let { parseStringList(it) }
            modes = if (fromCamera.isNullOrEmpty()) FALLBACK_MODES else fromCamera.map { ModeOption(prettify(it), it) }
        }
    }

    LaunchedEffect(Unit) {
        connected = CameraSession.connect().isSuccess
        if (connected) loadModes()
    }

    // Sync the small preview files for everything currently on the camera
    // into the app's own storage as soon as we're connected, so Gallery is
    // instantly browsable (and mostly offline-capable) instead of having to
    // fetch over the camera's slow hotspot each time it's opened.
    LaunchedEffect(connected) {
        if (connected) {
            val httpClient = CameraHttpClient()
            val cache = LocalPreviewCache(context)
            runCatching {
                val groups = httpClient.listAllMedia()
                cache.sync(httpClient, groups)
            }
        }
    }

    // Poll the camera's own settings while this screen is open so the info
    // row (mode, and whatever else the camera reports — ISO/shutter/etc.
    // once we know the real key names) stays live, mirroring what its own
    // display would show.
    LaunchedEffect(connected) {
        while (connected) {
            CameraSession.client.getAllCurrentSettings().getOrNull()?.let { json ->
                val map = parseSettingsMap(json)
                liveInfo = liveInfoFor(currentMode, map)
                map["camera_mode"]?.let { currentMode = it }
            }
            delay(3000)
        }
    }

    // Recording duration, straight from the camera (GET_RECORD_TIME) —
    // only meaningful while actually recording.
    LaunchedEffect(recording) {
        while (recording) {
            CameraSession.client.getRecordTimeSeconds().getOrNull()?.let { recordSeconds = it }
            delay(1000)
        }
        if (!recording) recordSeconds = 0
    }

    val player = remember {
        // Default ExoPlayer buffering targets several seconds of video before
        // playback, which is fine for progressive/HLS but makes a live feed
        // feel badly delayed. Cut the buffer way down — this is a live
        // control feed, not something that needs to survive network hiccups
        // smoothly, so a stutter now and then is a fair trade for latency.
        val lowLatencyLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 300,
                /* maxBufferMs = */ 600,
                /* bufferForPlaybackMs = */ 100,
                /* bufferForPlaybackAfterRebufferMs = */ 150,
            )
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(RtspMediaSource.Factory())
            .setLoadControl(lowLatencyLoadControl)
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
                actions = {
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Filled.Photo, contentDescription = "Gallery")
                    }
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.BugReport, contentDescription = "Diagnostics")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Mode settings")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Swipe right-to-left anywhere on this screen to jump into
                // the full Gallery, like flicking to the next screen.
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            // This is a live control feed with its own
                            // shutter/record buttons below — ExoPlayer's
                            // default tap-to-show-controls overlay (with a
                            // seek bar/position readout that means nothing
                            // for a live RTSP stream) is just confusing here.
                            useController = false
                        }
                    },
                )
                if (isVideoLikeMode(currentMode) && recording) {
                    Text(
                        formatDuration(recordSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
            }

            if (liveInfo.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((key, value) in liveInfo) {
                        AssistChip(onClick = {}, label = { Text("$key: $value") })
                    }
                }
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
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

            Text(
                "Shooting mode",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(modes) { mode ->
                    FilterChip(
                        selected = mode.value == currentMode,
                        enabled = connected && !busy,
                        onClick = {
                            runCommand("Set mode ${mode.label}") { CameraSession.client.setCameraMode(mode.value) }
                            currentMode = mode.value
                        },
                        label = { Text(mode.label) },
                    )
                }
            }
        }
    }
}

private fun prettify(rawValue: String): String =
    rawValue.replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun isVideoLikeMode(mode: String?): Boolean =
    mode == null || mode.contains("record") || mode.contains("video") || mode.contains("loop") || mode.contains("slow")

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

/**
 * Picks a handful of relevant fields to show for the current mode out of
 * whatever the camera's settings dump contains. The real key names this
 * camera uses aren't confirmed yet, so this matches loosely by substring
 * against a shortlist of plausible names per field — if/when the real dump
 * shows different key names, extend the candidate lists below rather than
 * guessing a fixed schema.
 */
private fun liveInfoFor(mode: String?, settings: Map<String, String>): List<Pair<String, String>> {
    fun find(vararg candidates: String): Pair<String, String>? {
        for (c in candidates) {
            val hit = settings.entries.firstOrNull { it.key.contains(c, ignoreCase = true) }
            if (hit != null) return prettify(hit.key) to hit.value
        }
        return null
    }

    val wanted = when {
        mode == null -> listOf("iso", "resolution")
        mode.contains("photo") && !mode.contains("video") -> listOf("iso", "shutter", "meter")
        mode.contains("slow") -> listOf("slow", "fps", "resolution")
        mode.contains("time_lapse") || mode.contains("timelapse") -> listOf("interval", "resolution")
        mode.contains("loop") -> listOf("loop", "resolution")
        mode.contains("burst") -> listOf("burst", "iso")
        mode.contains("timer") -> listOf("timer", "delay")
        mode.contains("video") || mode.contains("record") -> listOf("resolution", "iso")
        else -> listOf("iso", "resolution")
    }

    return wanted.mapNotNull { find(it) }
}

/** Best-effort parse of a msg_id=9 (GET_SINGLE_SETTING_OPTIONS) reply into option names. */
private fun parseStringList(json: JSONObject): List<String> {
    json.optJSONArray("param")?.let { arr ->
        return (0 until arr.length()).mapNotNull { arr.opt(it)?.toString() }
    }
    json.optJSONObject("param")?.let { obj ->
        return obj.keys().asSequence().toList()
    }
    json.opt("param")?.let { return listOf(it.toString()) }
    return emptyList()
}

/** Best-effort parse of a msg_id=3 (GET_ALL_CURRENT_SETTINGS) reply into a flat key/value map. */
private fun parseSettingsMap(json: JSONObject): Map<String, String> {
    val obj = json.optJSONObject("param") ?: return emptyMap()
    return obj.keys().asSequence().associateWith { key -> obj.opt(key)?.toString().orEmpty() }
}
