package com.mijia4k.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowDropDown
import com.mijia4k.app.net.SettingField
import com.mijia4k.app.net.fieldsFor
import com.mijia4k.app.net.toggleValue
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import com.mijia4k.app.net.parseSettingsArray
import com.mijia4k.app.ui.theme.MijiaTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ModeOption(
    val label: String,
    val value: String,
    val icon: ImageVector,
    /** Shutter starts/stops a recording rather than taking a single shot. */
    val isRecording: Boolean,
)

// Taken directly from the stock Mi Home app's "Select Mode" screen, same grid
// order. All nine values are now confirmed against the real camera (each was
// read back from its own settings dump after switching modes in the stock
// app), which corrected four earlier guesses: slow_motion, continuous_capture,
// record_capture and timing_capture.
private val CAMERA_MODES = listOf(
    ModeOption("Video", "normal_record", Icons.Filled.Videocam, isRecording = true),
    ModeOption("Time Lapse Video", "time_lapse_record", Icons.Filled.Timelapse, isRecording = true),
    ModeOption("Slow Motion", "slow_motion", Icons.Filled.SlowMotionVideo, isRecording = true),
    ModeOption("Loop Record", "loop_record", Icons.Filled.Loop, isRecording = true),
    ModeOption("Video+Photo", "record_capture", Icons.Filled.PhotoCameraFront, isRecording = true),
    ModeOption("Photo", "normal_capture", Icons.Filled.CameraAlt, isRecording = false),
    ModeOption("Timer", "timing_capture", Icons.Filled.Timer, isRecording = false),
    ModeOption("Burst", "continuous_capture", Icons.Filled.BurstMode, isRecording = false),
    // Interval stills: the camera keeps shooting until told to stop, so this
    // behaves like a recording session rather than a single shutter press.
    ModeOption("Time Lapse Photo", "time_lapse_capture", Icons.Filled.Schedule, isRecording = true),
)

private val DEFAULT_MODE = CAMERA_MODES.first { it.value == "time_lapse_record" }

/** Storage/battery are refreshed every Nth settings poll (~30s) rather than every 3s. */
private const val SLOW_POLL_EVERY = 10

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
    var busy by remember { mutableStateOf(false) }
    var recordStartedAt by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val sessionMode by CameraSession.currentMode.collectAsState()
    val currentMode = CAMERA_MODES.firstOrNull { it.value == sessionMode } ?: DEFAULT_MODE
    val sessionSettings by CameraSession.settings.collectAsState()

    // A mode change, from a tap here or from the camera itself, ends whatever "recording" state was showing.
    LaunchedEffect(sessionMode) { recording = false }
    // What the camera itself last reported for mode_setting, whether or not
    // it matches one of our values — shown so a wrong guess is visible
    // instead of silently invisible.
    var cameraReportedMode by remember { mutableStateOf<String?>(null) }
    var showModeDialog by remember { mutableStateOf(false) }
    var openField by remember { mutableStateOf<SettingField?>(null) }
    var showGrid by remember { mutableStateOf(false) }
    var distortionCorrection by remember { mutableStateOf<Boolean?>(null) }
    var remainingVideoSeconds by remember { mutableStateOf<Int?>(null) }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var lastThumb by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(Unit) {
        lastThumb = LocalPreviewCache(context).listCached().firstOrNull()
    }

    // Sync the poster files for everything currently on the camera into the
    // app's own storage as soon as we're connected, so Album is instantly
    // browsable (and mostly offline-capable) instead of fetching over the
    // camera's slow hotspot every time it's opened.
    LaunchedEffect(connected) {
        if (connected) {
            val cache = LocalPreviewCache(context)
            runCatching {
                val httpClient = CameraHttpClient()
                cache.sync(httpClient, httpClient.listAllMedia())
            }
            lastThumb = cache.listCached().firstOrNull()
        }
    }

    // Connects, then keeps polling the camera's own settings while this
    // screen is open so the info row stays live and the highlighted mode
    // reflects what the camera actually reports rather than what was last
    // tapped. Runs continuously (not gated on `connected`) so a dropped
    // connection self-heals within one cycle — CameraSession.connect() also
    // re-pins the process to the camera's Wi-Fi, which is what actually
    // breaks when the phone drifts back to mobile data.
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            connected = CameraSession.connect(context).isSuccess
            if (!connected) {
                // Never leave a stop button on screen for a recording that
                // can't be stopped; the camera's state is unknown from here.
                recording = false
            } else {
                CameraSession.refreshSettings()?.let { map ->
                    cameraReportedMode = map["mode_setting"]
                    // Reflect the camera's actual state rather than assuming
                    // it starts enabled — the icon used to be able to lie.
                    map["distortion_correction"]?.let { distortionCorrection = it.equals("on", true) }
                }
                // Card space and battery move slowly, and every command is
                // serialized behind a 600ms inter-command gap — polling them
                // each cycle would park the shutter behind four round trips.
                if (tick % SLOW_POLL_EVERY == 0) {
                    remainingVideoSeconds = CameraSession.client.getStorageStatus().remainingVideoSeconds
                    // Not every firmware answers this one; it stays hidden
                    // rather than showing a bogus level if the camera refuses.
                    batteryPercent = CameraSession.client.getBatteryLevel().getOrNull()?.takeIf { it in 0..100 }
                }
            }
            tick++
            delay(if (connected) 3000 else 2000)
        }
    }

    // GET_RECORD_TIME isn't implemented on this firmware (it answers with a
    // refusal), so the elapsed counter is kept locally from the moment the
    // camera accepted the start command.
    LaunchedEffect(recording) {
        while (recording) {
            elapsedSeconds = ((System.currentTimeMillis() - recordStartedAt) / 1000).toInt().coerceAtLeast(0)
            delay(500)
        }
        elapsedSeconds = 0
    }

    // Clear transient status text so a stale "sent — confirming..." doesn't
    // sit on screen forever.
    LaunchedEffect(status) {
        if (status != null) {
            delay(4000)
            status = null
        }
    }

    val player = remember {
        // Default ExoPlayer buffering targets several seconds of video before
        // playback, which is fine for progressive/HLS but makes a live feed
        // feel badly delayed. Cut the buffer way down — this is a live
        // control feed, so an occasional stutter is a fair trade for latency.
        val lowLatencyLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(300, 600, 100, 150)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(RtspMediaSource.Factory())
            .setLoadControl(lowLatencyLoadControl)
            .build()
    }
    var previewFailed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // The live feed only exists once the control session is up, and a failed
    // prepare() is permanent — the preview used to race the handshake on
    // launch and then stay black forever with no way to retry.
    LaunchedEffect(connected, previewFailed) {
        if (connected && !previewFailed) {
            player.setMediaItem(MediaItem.fromUri(CameraEndpoints.RTSP_URL))
            player.prepare()
            player.playWhenReady = true
        }
    }

    /** Runs a camera command, reporting what the *camera* said rather than just that bytes were sent. */
    fun runCommand(label: String, onSuccess: () -> Unit = {}, block: suspend () -> Result<*>) {
        if (busy) return
        busy = true
        scope.launch {
            val result = block()
            status = result.fold(
                onSuccess = { onSuccess(); "$label OK" },
                onFailure = { "$label failed: ${it.message}" },
            )
            busy = false
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
                    batteryPercent?.let {
                        Text("$it%", style = MaterialTheme.typography.bodySmall)
                    }
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
                // the full Album, like flicking to the next screen.
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
                IconButton(
                    enabled = connected && distortionCorrection != null,
                    onClick = {
                        val next = !(distortionCorrection ?: false)
                        runCommand(
                            "Distortion correction",
                            onSuccess = { distortionCorrection = next },
                        ) {
                            CameraSession.client.setSetting("distortion_correction", if (next) "on" else "off")
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Straighten,
                        contentDescription = "Distortion correction",
                        tint = if (distortionCorrection == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { showGrid = !showGrid }) {
                    Icon(
                        Icons.Filled.GridOn,
                        contentDescription = "Grid overlay",
                        tint = if (showGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            // Live control feed with its own shutter/record
                            // buttons below — ExoPlayer's default tap-to-show
                            // seek bar is meaningless for a live stream.
                            useController = false
                        }
                    },
                )
                if (showGrid) GridOverlay(Modifier.fillMaxSize())

                if (previewFailed) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Live preview stopped", color = Color.White)
                        TextButton(onClick = { previewFailed = false }) { Text("Retry") }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    remainingVideoSeconds?.takeIf { !recording }?.let {
                        Text(formatDuration(it), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                    if (recording) {
                        Text(formatDuration(elapsedSeconds), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
                    }
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        .clickable { showModeDialog = true }
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Icon(currentMode.icon, contentDescription = null, tint = MijiaTeal, modifier = Modifier.size(20.dp))
                    Text(
                        currentMode.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change mode")
                }
                if (cameraReportedMode != null && CAMERA_MODES.none { it.value == cameraReportedMode }) {
                    Text(
                        " (camera reports: \"$cameraReportedMode\")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (!connected) {
                Text(
                    "Not connected to the camera — reconnecting...",
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
                IconButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    lastThumb?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "Album",
                            imageLoader = CameraImageLoader.get(context),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: Icon(Icons.Filled.Photo, contentDescription = "Album")
                }

                IconButton(
                    enabled = connected && !busy,
                    onClick = {
                        // The recording flag only moves once the camera has
                        // actually accepted the command — flipping it
                        // optimistically left a red stop button (and a
                        // running timer) over a camera that never started.
                        if (currentMode.isRecording) {
                            if (recording) {
                                runCommand("Stop recording", onSuccess = { recording = false }) {
                                    CameraSession.client.stopRecording()
                                }
                            } else {
                                runCommand(
                                    "Start recording",
                                    onSuccess = {
                                        recordStartedAt = System.currentTimeMillis()
                                        recording = true
                                    },
                                ) { CameraSession.client.startRecording() }
                            }
                        } else {
                            runCommand("Shutter") { CameraSession.client.takePhoto() }
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(if (recording) Color.Red else MijiaTeal, CircleShape),
                ) {
                    if (recording) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            // Settings for the current mode, right under the shutter row.
            ModeSettingsBar(
                fields = remember(sessionMode) { fieldsFor(sessionMode) },
                settings = sessionSettings,
                enabled = connected,
                onToggle = { field, next ->
                    scope.launch {
                        CameraSession.writeSetting(field.key, toggleValue(field, next)).onFailure {
                            status = "Couldn't set ${field.label}: ${it.message}"
                        }
                    }
                },
                onOpen = { openField = it },
            )
        }
    }

    openField?.let { field ->
        SettingOptionSheet(
            field = field,
            currentValue = sessionSettings[field.key],
            onPick = { value ->
                openField = null
                scope.launch {
                    CameraSession.writeSetting(field.key, value).onFailure {
                        status = "Couldn't set ${field.label}: ${it.message}"
                    }
                }
            },
            onDismiss = { openField = null },
        )
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
                                        // The highlight and every mode-specific
                                        // value flip right now; the camera confirms
                                        // in the background and the UI only snaps
                                        // back if it refuses.
                                        if (mode.value != sessionMode) {
                                            recording = false
                                            scope.launch {
                                                CameraSession.switchMode(mode.value).onFailure {
                                                    status = "Set mode ${mode.label} failed: ${it.message}"
                                                }
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
                                        if (mode == currentMode) MijiaTeal else Color(0xFF3A3A3A),
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

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

