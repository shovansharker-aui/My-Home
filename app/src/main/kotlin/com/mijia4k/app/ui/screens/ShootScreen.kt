package com.mijia4k.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.content.res.Configuration
import android.util.Log
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import com.mijia4k.app.ui.HardwareKeys
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
import androidx.compose.ui.platform.LocalConfiguration
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

/** Digital zoom ceiling for the live preview. */
private const val MAX_ZOOM = 10f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootScreen(
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var connected by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var recordStartedAt by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    val sessionMode by CameraSession.currentMode.collectAsState()
    val currentMode = CAMERA_MODES.firstOrNull { it.value == sessionMode } ?: DEFAULT_MODE
    val sessionSettings by CameraSession.settings.collectAsState()

    // A mode change, from a tap here or from the camera itself, ends whatever "recording" state was showing.
    LaunchedEffect(sessionMode) { recording = false }

    // The camera reports what it is doing. Follow its changes so a recording
    // started elsewhere (its own button, a previous session) shows the stop
    // button instead of a shutter that would try to start a second one.
    val appStatus = sessionSettings["app_status"]
    LaunchedEffect(appStatus) {
        if (appStatus == null) return@LaunchedEffect
        val cameraIsRecording = appStatus.contains("record")
        if (cameraIsRecording && !recording) {
            recordStartedAt = System.currentTimeMillis()
            recording = true
        } else if (!cameraIsRecording && recording) {
            recording = false
        }
    }
    var showModeDialog by remember { mutableStateOf(false) }
    var openField by remember { mutableStateOf<SettingField?>(null) }
    var showGrid by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var distortionCorrection by remember { mutableStateOf<Boolean?>(null) }
    var freeBytes by remember { mutableStateOf<Long?>(null) }
    var battery by remember { mutableStateOf<com.mijia4k.app.net.AmbaSocketClient.BatteryStatus?>(null) }
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
                    // Reflect the camera's actual state rather than assuming
                    // it starts enabled — the icon used to be able to lie.
                    map["distortion_correction"]?.let { distortionCorrection = it.equals("on", true) }
                }
                // Card space and battery move slowly, and every command is
                // serialized behind a 600ms inter-command gap — polling them
                // each cycle would park the shutter behind four round trips.
                if (tick % SLOW_POLL_EVERY == 0) {
                    freeBytes = CameraSession.client.getStorageStatus().freeBytes
                    // Not every firmware answers this one; it stays hidden
                    // rather than showing a bogus level if the camera refuses.
                    battery = CameraSession.client.getBattery().getOrNull()?.takeIf { it.level in 0..100 }
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
    fun runCommand(label: String, spin: Boolean = false, onSuccess: () -> Unit = {}, block: suspend () -> Result<*>) {
        if (busy) return
        busy = true
        if (spin) capturing = true
        scope.launch {
            val result = block()
            result.onSuccess { onSuccess() }
            result.onFailure { Log.w("ShootScreen", "$label failed", it) }
            capturing = false
            busy = false
        }
    }

    fun triggerShutter() {
        if (!connected || busy) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // The recording flag only moves once the camera has actually accepted
        // the command — flipping it optimistically left a red stop button (and
        // a running timer) over a camera that never started.
        if (currentMode.isRecording) {
            if (recording) {
                runCommand("Stop recording", spin = true, onSuccess = { recording = false }) {
                    CameraSession.client.stopRecording()
                }
            } else {
                runCommand(
                    "Start recording",
                    spin = true,
                    onSuccess = {
                        recordStartedAt = System.currentTimeMillis()
                        recording = true
                    },
                ) { CameraSession.client.startRecording() }
            }
        } else {
            runCommand("Shutter", spin = true) { CameraSession.client.takePhoto() }
        }
    }

    // Hardware volume keys act as a shutter while this screen is showing.
    LaunchedEffect(Unit) {
        HardwareKeys.shutterScreenActive = true
        HardwareKeys.shutter.collect { triggerShutter() }
    }
    DisposableEffect(Unit) { onDispose { HardwareKeys.shutterScreenActive = false } }

    // A live view you are watching should not dim and lock.
    val hostView = LocalView.current
    DisposableEffect(Unit) {
        hostView.keepScreenOn = true
        onDispose { hostView.keepScreenOn = false }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            // Landscape gives the whole height to the preview; Settings is a swipe down away.
            if (!landscape) TopAppBar(
                title = {},
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
        val gestureModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            // Swipe down anywhere for Camera Settings; swipe left for the Album.
            // Watches touches on the way down without consuming them, so the
            // controls' own scrolling and taps still work; skipped while zoomed
            // in (that drag pans the preview) or pinching.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var dx = 0f
                    var dy = 0f
                    var multiTouch = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size > 1) multiTouch = true
                        event.changes.firstOrNull()?.let {
                            dx += it.positionChange().x
                            dy += it.positionChange().y
                        }
                    } while (event.changes.any { it.pressed })
                    if (!multiTouch && zoom <= 1f) {
                        when {
                            dy > 160f && dy > kotlin.math.abs(dx) -> onOpenSettings()
                            dx < -150f && kotlin.math.abs(dx) > kotlin.math.abs(dy) -> onOpenGallery()
                        }
                    }
                }
            }

        val toolsRow: @Composable () -> Unit = {
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
        }

        val preview: @Composable (Modifier) -> Unit = { mod ->
            Box(
                mod
                    .background(Color.Black)
                    .clipToBounds()
                    .onSizeChanged { previewSize = it }
                    .pointerInput(Unit) {
                        // Pinch to zoom up to 10x, drag to pan once zoomed, double-tap to
                        // jump between 1x and 3x. A one-finger swipe at 1x is left alone
                        // so swipe-down (Settings) and swipe-left (Album) still work.
                        detectTapGestures(onDoubleTap = {
                            if (zoom > 1.01f) { zoom = 1f; pan = Offset.Zero } else zoom = 3f
                        })
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || zoom > 1f) {
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                    val maxX = (previewSize.width * (newZoom - 1f)) / 2f
                                    val maxY = (previewSize.height * (newZoom - 1f)) / 2f
                                    pan = Offset(
                                        (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                        (pan.y + panChange.y).coerceIn(-maxY, maxY),
                                    )
                                    zoom = newZoom
                                    if (zoom <= 1f) pan = Offset.Zero
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        translationX = pan.x,
                        translationY = pan.y,
                    ),
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
                battery?.let { BatteryBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
                if (zoom > 1.01f) {
                    Text(
                        "%.1f×".format(zoom),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .clickable { zoom = 1f; pan = Offset.Zero }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                if (previewFailed) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Live preview stopped", color = Color.White)
                        TextButton(onClick = {
                            scope.launch { CameraSession.ensureViewfinder() }
                            previewFailed = false
                        }) { Text("Retry") }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    freeBytes?.takeIf { !recording }?.let {
                        Text(
                            "${formatFree(it)} free",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (recording) {
                        Text(
                            formatDuration(elapsedSeconds),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
                    }
                }
            }
        }

        val controls: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = if (landscape) 12.dp else 24.dp, horizontal = 32.dp),
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
                    onClick = { triggerShutter() },
                    modifier = Modifier
                        .size(72.dp)
                        .background(if (recording) Color.Red else MijiaTeal, CircleShape),
                ) {
                    if (recording) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    if (capturing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, modifier = Modifier.size(60.dp))
                    }
                }

                IconButton(
                    onClick = { showModeDialog = true },
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Icon(currentMode.icon, contentDescription = "Change mode (${currentMode.label})", tint = MijiaTeal)
                }
            }
        }

        // Settings for the current mode, right under the shutter row.
        val params: @Composable (Int, androidx.compose.ui.unit.Dp) -> Unit = { perRow, chipWidth ->
            ModeSettingsBar(
                fields = remember(sessionMode) { fieldsFor(sessionMode) },
                settings = sessionSettings,
                enabled = connected,
                perRow = perRow,
                chipWidth = chipWidth,
                onToggle = { field, next ->
                    scope.launch {
                        CameraSession.writeSetting(field.key, toggleValue(field, next)).onFailure { Log.w("ShootScreen", "set ${field.key} failed", it) }
                    }
                },
                onOpen = { openField = it },
            )
        }

        if (landscape) {
            // Preview takes the left two thirds; everything else stacks in the right third.
            Row(gestureModifier) {
                Box(Modifier.weight(2f).fillMaxHeight().background(Color.Black)) {
                    preview(Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    toolsRow()
                    controls()
                    params(5, 56.dp)
                }
            }
        } else {
            Column(gestureModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                toolsRow()
                preview(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                controls()
                params(5, 64.dp)
            }
        }
    }

    openField?.let { field ->
        SettingOptionSheet(
            field = field,
            currentValue = sessionSettings[field.key],
            onPick = { value ->
                openField = null
                scope.launch {
                    CameraSession.writeSetting(field.key, value).onFailure { Log.w("ShootScreen", "set ${field.key} failed", it) }
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
                                                CameraSession.switchMode(mode.value).onFailure { Log.w("ShootScreen", "mode switch failed", it) }
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


private fun formatFree(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1.0) "%.1f GB".format(gb) else "%d MB".format((bytes / 1024 / 1024).toInt())
}
