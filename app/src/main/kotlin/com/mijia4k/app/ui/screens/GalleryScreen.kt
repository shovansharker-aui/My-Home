package com.mijia4k.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.mijia4k.app.net.CameraHttpClient
import com.mijia4k.app.net.CameraImageLoader
import com.mijia4k.app.net.CameraSession
import com.mijia4k.app.net.LocalPreviewCache
import com.mijia4k.app.net.MediaStoreSaver
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** One tile's worth of data, whether it came from the live camera listing or the offline cache. */
private data class GalleryItem(
    val key: String,
    val name: String,
    val isVideo: Boolean,
    /** Something the image loader can render for the grid; null for videos on this camera. */
    val thumbModel: Any?,
    /** What the viewer streams — the low-res proxy for videos when one exists. */
    val playbackFile: CameraHttpClient.CameraFile?,
    /** The full-resolution original, for share/info. */
    val mediaFile: CameraHttpClient.CameraFile?,
    val downloadable: List<CameraHttpClient.CameraFile>,
    val localThumb: File?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { CameraHttpClient() }
    val previewCache = remember { LocalPreviewCache(context) }
    val imageLoader = remember { CameraImageLoader.get(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var offline by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Keyed, not indexed: the list can be replaced while the viewer is open
    // (the sync pass rebuilds it), and an index into the old list then either
    // showed the wrong shot or crashed on out-of-bounds.
    var viewerKey by remember { mutableStateOf<String?>(null) }

    val selectionMode = selected.isNotEmpty()

    fun buildItems(groups: List<CameraHttpClient.MediaGroup>): List<GalleryItem> = groups.map { group ->
        val cachedThumb = previewCache.localFileFor(group)?.takeIf { it.exists() }
        GalleryItem(
            key = group.mediaFile.path,
            name = group.mediaFile.name,
            isVideo = group.isVideo,
            thumbModel = cachedThumb ?: group.posterFile?.url,
            playbackFile = group.playbackFile,
            mediaFile = group.mediaFile,
            downloadable = group.downloadable,
            localThumb = cachedThumb,
        )
    }

    fun refresh() {
        loading = true
        error = null
        selected = emptySet()
        scope.launch {
            val attempt = withTimeoutOrNull(8000) { runCatching { client.listAllMedia() } }
            val online = attempt?.getOrNull()
            if (online != null) {
                offline = false
                items = buildItems(online)
                loading = false
                previewCache.sync(client, online)
                // Pick up posters that just finished downloading.
                items = buildItems(online)
            } else {
                offline = true
                items = previewCache.listCached().map { f ->
                    val name = previewCache.displayNameOf(f)
                    GalleryItem(
                        key = f.absolutePath,
                        name = name,
                        isVideo = name.substringAfterLast('.', "").lowercase() in CameraHttpClient.VIDEO_EXTENSIONS,
                        thumbModel = f,
                        playbackFile = null,
                        mediaFile = null,
                        downloadable = emptyList(),
                        localThumb = f,
                    )
                }
                loading = false
                if (items.isEmpty()) {
                    // Distinguish "couldn't reach the camera" from "the camera
                    // answered but the listing failed" — they used to look
                    // identical, both reported as "offline".
                    val reason = attempt?.exceptionOrNull()?.message
                    error = if (reason != null) {
                        "Couldn't read the camera's file list: $reason"
                    } else {
                        "Camera unreachable and nothing cached locally yet"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    suspend fun download(targets: List<CameraHttpClient.CameraFile>): Pair<Int, Int> {
        var ok = 0
        for (file in targets) {
            if (runCatching { MediaStoreSaver.saveToDownloads(context, file, client) }.getOrDefault(false)) ok++
        }
        return ok to targets.size
    }

    fun downloadSelected() {
        val targets = items.filter { it.key in selected }.flatMap { it.downloadable }
        downloading = true
        scope.launch {
            val (ok, total) = download(targets)
            downloading = false
            selected = emptySet()
            snackbarHostState.showSnackbar(
                if (total == 0) "Nothing downloadable while offline" else "Downloaded $ok/$total file(s) to Downloads/Mijia4K",
            )
        }
    }

    fun deleteSelected() {
        val toDelete = items.filter { it.key in selected }
        deleting = true
        scope.launch {
            var ok = 0
            var failed = 0
            for (item in toDelete) {
                // Delete the original + proxy (.THM) + RAW (.DNG).
                val files = (item.downloadable +
                    listOfNotNull(item.playbackFile?.takeIf { it != item.mediaFile }))
                    .distinctBy { it.path }
                for (file in files) {
                    val result = CameraSession.deleteFile(context, file.path)
                    if (result.isSuccess) ok++ else {
                        android.util.Log.w("Gallery", "Delete failed for ${file.path}: ${result.exceptionOrNull()}")
                        failed++
                    }
                }
            }
            deleting = false
            selected = emptySet()
            val msg = when {
                failed == 0 -> "Deleted $ok file(s) from camera"
                ok == 0 -> "Delete failed — ${if (offline) "not available offline" else "camera refused"}"
                else -> "Deleted $ok, failed $failed"
            }
            snackbarHostState.showSnackbar(msg)
            if (ok > 0) refresh()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selected.size} selected" else if (offline) "Album (offline)" else "Album") },
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) selected = emptySet() else onBack() }) {
                        Icon(
                            if (selectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(enabled = !downloading && !deleting, onClick = { downloadSelected() }) {
                            Icon(Icons.Filled.Download, contentDescription = "Download selected")
                        }
                        IconButton(enabled = !downloading && !deleting, onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        IconButton(enabled = !loading, onClick = { refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(error.orEmpty(), modifier = Modifier.align(Alignment.Center).padding(24.dp))
                items.isEmpty() -> Text("No photos or videos found", modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
                        GridCell(
                            item = item,
                            imageLoader = imageLoader,
                            selected = item.key in selected,
                            selectionMode = selectionMode,
                            onTap = {
                                if (selectionMode) {
                                    selected = if (item.key in selected) selected - item.key else selected + item.key
                                } else {
                                    viewerKey = item.key
                                }
                            },
                            onLongPress = {
                                selected = if (item.key in selected) selected - item.key else selected + item.key
                            },
                        )
                    }
                }
            }
            if (downloading || deleting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${selected.size} item(s)?") },
            text = { Text("This permanently removes the selected files from the camera's SD card. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; deleteSelected() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    val startIndex = viewerKey?.let { key -> items.indexOfFirst { it.key == key } } ?: -1
    if (viewerKey != null && startIndex < 0) {
        // The shot we were viewing is gone (deleted on the camera, or the
        // listing changed underneath us) — close rather than index into it.
        viewerKey = null
    } else if (startIndex >= 0) {
        MediaPagerViewer(
            items = items,
            startIndex = startIndex,
            imageLoader = imageLoader,
            client = client,
            onClose = { viewerKey = null },
            onShareError = { scope.launch { snackbarHostState.showSnackbar(it) } },
            onDownload = { item ->
                scope.launch {
                    val (ok, total) = download(item.downloadable)
                    snackbarHostState.showSnackbar(
                        if (total == 0) "Not available offline" else "Downloaded $ok/$total file(s) to Downloads/Mijia4K",
                    )
                }
            },
        )
    }
}

@Composable
private fun GridCell(
    item: GalleryItem,
    imageLoader: ImageLoader,
    selected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        item.thumbModel?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = item.name,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        } ?: Box(Modifier.fillMaxSize().background(Color(0xFF202020)))
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
        if (item.thumbModel != null && item.localThumb == null) {
            // Not synced into local storage yet — thumbnail is loading
            // straight from the camera over the (slow) hotspot.
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
            }
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onTap() }, modifier = Modifier.align(Alignment.TopEnd))
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

@Composable
private fun MediaPagerViewer(
    items: List<GalleryItem>,
    startIndex: Int,
    imageLoader: ImageLoader,
    client: CameraHttpClient,
    onClose: () -> Unit,
    onShareError: (String) -> Unit,
    onDownload: (GalleryItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var showInfo by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    // System back (button or gesture) should close this viewer and land back
    // on the grid, not pop the whole Gallery screen off the nav stack.
    BackHandler(onBack = onClose)

    val current = items.getOrNull(pagerState.currentPage) ?: run {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            if (item.isVideo) {
                // The pager composes neighbouring pages ahead of time, so
                // every adjacent video used to start playing at once — you'd
                // hear the next clip while still watching this one.
                VideoPage(item = item, isActive = page == pagerState.currentPage)
            } else {
                ZoomableImage(
                    model = item.mediaFile?.url ?: item.thumbModel ?: return@HorizontalPager,
                    imageLoader = imageLoader,
                    contentDescription = item.name,
                )
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(
                enabled = !sharing && current.mediaFile != null,
                onClick = {
                    sharing = true
                    scope.launch {
                        runCatching { shareItem(context, current, client) }
                            .onFailure { onShareError("Couldn't share: ${it.message}") }
                        sharing = false
                    }
                },
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
            }
            IconButton(onClick = { onDownload(current) }) {
                Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
            }
        }
        Text(
            current.name,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
        )
        if (sharing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } },
            title = { Text(current.name) },
            text = {
                Column {
                    Text("Type: ${if (current.isVideo) "Video" else "Image"}")
                    current.mediaFile?.sizeBytes?.let { Text("Size: ${it / 1024} KB") }
                    current.downloadable.drop(1).forEach { Text("Also: ${it.name}") }
                    Text("Poster cached locally: ${current.localThumb != null}")
                    if (current.mediaFile == null) Text("Only the cached poster is available offline")
                }
            },
        )
    }
}

/**
 * Copies the original into the app's cache and hands it to the share sheet.
 * The cache subdirectory has to match a root declared in `file_paths.xml` —
 * it didn't, so `getUriForFile` threw and took the whole app down.
 */
private suspend fun shareItem(
    context: android.content.Context,
    item: GalleryItem,
    client: CameraHttpClient,
) {
    val media = item.mediaFile ?: error("nothing to share offline")
    val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val target = File(shareDir, media.name)

    if (!target.exists() || target.length() == 0L) {
        runCatching {
            target.outputStream().use { out -> client.downloadTo(media, out) }
        }.onFailure {
            target.delete()
            throw it
        }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = if (item.isVideo) "video/mp4" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share",
        ),
    )
}

@Composable
private fun VideoPage(item: GalleryItem, isActive: Boolean) {
    val context = LocalContext.current
    var failure by remember(item.key) { mutableStateOf<String?>(null) }

    val player = remember(item.key) {
        ExoPlayer.Builder(context).build().apply {
            // Stream the proxy: the original is 545 MB against its 47 MB copy,
            // and the camera's hotspot cannot keep up with the former.
            item.playbackFile?.url?.let { setMediaItem(MediaItem.fromUri(it)) }
            prepare()
        }
    }

    DisposableEffect(item.key) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failure = error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Only the page actually on screen plays.
    LaunchedEffect(isActive) {
        player.playWhenReady = isActive
        if (!isActive) player.seekTo(0)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        )
        failure?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't play this video ($it)", color = Color.White)
                TextButton(onClick = {
                    failure = null
                    player.prepare()
                    player.playWhenReady = true
                }) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun ZoomableImage(model: Any, imageLoader: ImageLoader, contentDescription: String?) {
    var scale by remember(model) { mutableStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    Image(
        painter = rememberAsyncImagePainter(model = model, imageLoader = imageLoader),
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            )
            .pointerInput(model) {
                // A plain detectTransformGestures() consumes single-finger
                // pans unconditionally, which starves the parent Pager of
                // the drag events it needs to swipe between photos — only
                // consume here when there's an actual pinch or the image is
                // already zoomed in, so an unzoomed single-finger swipe
                // passes through to the Pager untouched.
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (zoomChange != 1f || scale > 1f) {
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + panChange
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    )
}
