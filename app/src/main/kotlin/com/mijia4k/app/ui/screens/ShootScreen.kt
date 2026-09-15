package com.mijia4k.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.mijia4k.app.net.CameraEndpoints
import com.mijia4k.app.net.CameraHttpClient
import com.mijia4k.app.net.CameraImageLoader
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.net.LocalPreviewCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class ModeOption(val label: String, val value: String, val icon: ImageVector)

// Taken directly from the stock Mi Home app's "Select Mode" screen (user
// screenshot), same grid order: row 1 Video/Time Lapse Video/Slow Motion,
// row 2 Loop Record/Video+Photo/Photo, row 3 Timer/Burst/Time Lapse Photo.
// The *labels* are confirmed real; the *value* strings sent to the camera's
// SET_SETTING command are still guesses (only "normal_record" for Video
// comes from documented Ambarella protocol research) — the status line
// after tapping shows the camera's real accept/reject response.
private val CAMERA_MODES = listOf(
    ModeOption("Video", "normal_record", Icons.Filled.Videocam),
    ModeOption("Time Lapse Video", "time_lapse_record", Icons.Filled.Timelapse),
    ModeOption("Slow Motion", "slow_motion_record", Icons.Filled.SlowMotionVideo),
    ModeOption("Loop Record", "loop_record", Icons.Filled.Loop),
    ModeOption("Video+Photo", "video_photo", Icons.Filled.PhotoCameraFront),
    ModeOption("Photo", "photo", Icons.Filled.CameraAlt),
    ModeOption("Timer", "self_timer", Icons.Filled.Timer),
    ModeOption("Burst", "burst", Icons.Filled.BurstMode),
    ModeOption("Time Lapse Photo", "time_lapse_photo", Icons.Filled.Schedule),
)

private val ShutterTeal = Color(0xFF00BFA5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootScreen(
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connected by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var currentMode by remember { mutableStateOf(CAMERA_MODES.first { it.value == "time_lapse_record" }) }
    // What the camera itself last reported for camera_mode, whether or not
    // it matches one of our guessed CAMERA_MODES values — shown so a wrong
    // guess is visible instead of silently invisible.
    var cameraReportedMode by remember { mutableStateOf<String?>(null) }
    var showModeDialog by remember { mutableStateOf(false) }
    var liveInfo by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showGrid by remember { mutableStateOf(false) }
    var distortionCorrection by remember { mutableStateOf(true) }
    var lastThumb by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(Unit) {
        connected = CameraSession.connect().isSuccess
        lastThumb = LocalPreviewCache(context).listCached().maxByOrNull { it.lastModified() }
    }

    // Sync the small preview files for everything currently on the camera
    // into the app's own storage as soon as we're connected, so Gallery is
    // instantly browsable (and mostly offline-capable) instead of having to
    // fetch over the camera's slow hotspot every time it's opened.
    LaunchedEffect(connected) {
        if (connected) {
            val httpClient = CameraHttpClient()
            val cache = LocalPreviewCache(context)
            runCatching {
                val groups = httpClient.listAllMedia()
                cache.sync(httpClient, groups)
            }
            lastThumb = LocalPreviewCache(context).listCached().maxByOrNull { it.lastModified() }
        }
    }

    // Poll the camera's own settings while this screen is open so the info
    // row stays live, and — importantly — so the highlighted mode reflects
    // what the camera actually reports rather than what we last tapped.
    // Tapping a mode sends the command but does NOT update `currentMode`
    // directly; this loop is the single source of truth for what mode is
    // "selected" in the UI, so a rejected/no-effect command just doesn't
    // flip the highlight instead of showing a mode that isn't really active.
    LaunchedEffect(connected) {
        while (connected) {
            CameraSession.client.getAllCurrentSettings().getOrNull()?.let { json ->
                val map = parseSettingsMap(json)
                map["camera_mode"]?.let { raw ->
                    cameraReportedMode = raw
                    CAMERA_MODES.firstOrNull { it.value == raw }?.let { matched ->
                        currentMode = matched
                        CameraSession.currentModeValue = matched.value
                    }
                }
                liveInfo = liveInfoFor(currentMode.value, map)
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
            .setBufferDurationsMs(300, 600, 100, 150)
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
        scope.launch {
            val result = block()
            status = if (result.isSuccess) "$label OK" else "$label failed: ${result.exceptionOrNull()?.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Action Camera 4K") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
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
                        onDragEnd = { if (dragAccumulator < -150f) onOpenGallery() },
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Backed by a real toggle sent to the camera — the stock app
                // shows a toast confirming this when it's on.
                IconButton(onClick = {
                    distortionCorrection = !distortionCorrection
                    runCommand("Distortion correction") {
                        CameraSession.client.setSetting("distortion_correction", if (distortionCorrection) "on" else "off")
                    }
                }) {
                    Icon(
                        Icons.Filled.Straighten,
                        contentDescription = "Distortion correction",
                        tint = if (distortionCorrection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Present in the stock app's top row; its exact backing
                // function isn't confirmed, so this is a visual toggle only
                // for now rather than guessing at a camera command.
                IconButton(onClick = { }) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = "Focus/exposure")
                }
                IconButton(onClick = { showGrid = !showGrid }) {
                    Icon(
                        Icons.Filled.GridOn,
                        contentDescription = "Grid overlay",
                        tint = if (showGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            // Live control feed with its own shutter/record
                            // buttons below — ExoPlayer's default tap-to-show
                            // overlay (seek bar/position, meaningless for a
                            // live RTSP stream) was just confusing here.
                            useController = false
                        }
                    },
                )
                if (showGrid) GridOverlay(Modifier.fillMaxSize())
                if (isVideoLikeMode(currentMode.value) && recording) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(formatDuration(recordSeconds), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
                    }
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    currentMode.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(4.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = { showModeDialog = true }) },
                )
                // The camera's own camera_mode value doesn't match any of
                // our guessed CAMERA_MODES strings — surfaced so the real
                // value is visible instead of silently falling back to
                // whatever was last confirmed.
                if (cameraReportedMode != null && CAMERA_MODES.none { it.value == cameraReportedMode }) {
                    Text(
                        " (camera reports: \"$cameraReportedMode\")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (liveInfo.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((key, value) in liveInfo) {
                        Text("$key: $value", style = MaterialTheme.typography.bodySmall)
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gallery shortcut — shows the last synced preview like the
                // stock app's thumbnail button when one's available.
                IconButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    lastThumb?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "Gallery",
                            imageLoader = CameraImageLoader.get(context),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: Icon(Icons.Filled.Photo, contentDescription = "Gallery")
                }

                IconButton(
                    enabled = connected,
                    onClick = {
                        if (isVideoLikeMode(currentMode.value)) {
                            if (recording) {
                                runCommand("Stop recording") { CameraSession.client.stopRecording() }
                            } else {
                                runCommand("Start recording") { CameraSession.client.startRecording() }
                            }
                            recording = !recording
                        } else {
                            runCommand("Shutter") { CameraSession.client.takePhoto() }
                            Toast.makeText(context, "Shutter triggered", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(if (recording) Color.Red else ShutterTeal, CircleShape),
                ) {
                    if (recording) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Mode settings")
                }
            }
        }
    }

    if (showModeDialog) {
        Dialog(onDismissRequest = { showModeDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF1B1B1B)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Select Mode", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 24.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
                    items(CAMERA_MODES) { mode ->
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .pointerInput(mode) {
                                    detectTapGestures(onTap = {
                                        showModeDialog = false
                                        // Don't flip the highlighted mode
                                        // optimistically — send the command,
                                        // then let the settings-poll loop
                                        // above confirm (or not) what the
                                        // camera actually switched to.
                                        scope.launch {
                                            val result = CameraSession.client.setCameraMode(mode.value)
                                            status = if (result.isSuccess) {
                                                "Set mode ${mode.label} sent — confirming with camera..."
                                            } else {
                                                "Set mode ${mode.label} failed: ${result.exceptionOrNull()?.message}"
                                            }
                                        }
                                    })
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .background(
                                        if (mode == currentMode) ShutterTeal else Color(0xFF3A3A3A),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(mode.icon, contentDescription = mode.label, tint = Color.White)
                            }
                            Text(mode.label, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                IconButton(onClick = { showModeDialog = false }, modifier = Modifier.padding(top = 24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White.copy(alpha = 0.5f)
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 1.dp.toPx())
        drawLine(color, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), strokeWidth = 1.dp.toPx())
        drawLine(color, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 1.dp.toPx())
        drawLine(color, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), strokeWidth = 1.dp.toPx())
    }
}

private fun isVideoLikeMode(mode: String): Boolean =
    mode.contains("record") || mode.contains("video") || mode.contains("loop") || mode.contains("slow")

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

// Key -> display label, matching the exact field keys used in
// SettingsScreen.kt's per-mode field lists (transcribed from real screen
// recordings of the stock app). 2-3 of each mode's real fields, picked as
// the ones most useful to glance at live.
private val LIVE_INFO_LABELS = mapOf(
    "resolution" to "Resolution",
    "quality" to "Quality",
    "speed" to "Speed",
    "video_length" to "Length",
    "interval" to "Interval",
    "iso" to "ISO",
    "shutter" to "Shutter",
    "metering_mode" to "Metering",
    "countdown" to "Countdown",
    "burst_rate" to "Rate",
    "aspect_ratio" to "Ratio",
)

private val LIVE_INFO_KEYS_BY_MODE = mapOf(
    "normal_record" to listOf("resolution", "quality"),
    "time_lapse_record" to listOf("interval", "resolution"),
    "slow_motion_record" to listOf("speed", "quality"),
    "loop_record" to listOf("video_length", "resolution"),
    "video_photo" to listOf("interval", "resolution"),
    "photo" to listOf("iso", "shutter", "metering_mode"),
    "self_timer" to listOf("countdown", "iso"),
    "burst" to listOf("burst_rate", "iso"),
    "time_lapse_photo" to listOf("interval", "iso"),
)

/** Picks the 2-3 most relevant fields to show for the current mode, using the real (transcribed) field keys. */
private fun liveInfoFor(mode: String, settings: Map<String, String>): List<Pair<String, String>> {
    val keys = LIVE_INFO_KEYS_BY_MODE[mode] ?: listOf("resolution", "quality")
    return keys.mapNotNull { key -> settings[key]?.let { value -> (LIVE_INFO_LABELS[key] ?: key) to value } }
}

/** Best-effort parse of a msg_id=3 (GET_ALL_CURRENT_SETTINGS) reply into a flat key/value map. */
private fun parseSettingsMap(json: JSONObject): Map<String, String> {
    val obj = json.optJSONObject("param") ?: return emptyMap()
    return obj.keys().asSequence().associateWith { key -> obj.opt(key)?.toString().orEmpty() }
}
