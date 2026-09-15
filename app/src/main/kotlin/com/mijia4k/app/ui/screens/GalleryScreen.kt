package com.mijia4k.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.mijia4k.app.net.CameraHttpClient
import com.mijia4k.app.net.CameraImageLoader
import com.mijia4k.app.net.LocalPreviewCache
import com.mijia4k.app.net.MediaStoreSaver
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** One thumbnail's worth of data, whether it came from the live camera listing or the offline cache. */
private data class GalleryItem(
    val key: String,
    val name: String,
    val isVideo: Boolean,
    val thumbModel: Any,
    val previewFile: CameraHttpClient.CameraFile?,
    val originalFile: CameraHttpClient.CameraFile?,
    val localFile: File?,
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
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    val selectionMode = selected.isNotEmpty()

    fun buildItems(groups: List<CameraHttpClient.MediaGroup>): List<GalleryItem> = groups.map { group ->
        val local = previewCache.localFileFor(group).takeIf { it.exists() }
        GalleryItem(
            key = group.previewFile.path,
            name = group.previewFile.name,
            isVideo = group.previewFile.isVideo,
            thumbModel = local ?: group.previewFile.url,
            previewFile = group.previewFile,
            originalFile = group.originalFile,
            localFile = local,
        )
    }

    fun refresh() {
        loading = true
        error = null
        selected = emptySet()
        scope.launch {
            val online = withTimeoutOrNull(6000) { runCatching { client.listAllMedia() }.getOrNull() }
            if (online != null) {
                offline = false
                items = buildItems(online)
                loading = false
                previewCache.sync(client, online)
                // Pick up any previews that just finished downloading.
                items = buildItems(online)
            } else {
                offline = true
                items = previewCache.listCached().map { f ->
                    val ext = f.name.substringAfterLast('.', "").lowercase()
                    GalleryItem(
                        key = f.absolutePath,
                        name = previewCache.displayNameOf(f),
                        isVideo = ext in CameraHttpClient.VIDEO_EXTENSIONS,
                        thumbModel = f,
                        previewFile = null,
                        originalFile = null,
                        localFile = f,
                    )
                }
                loading = false
                if (items.isEmpty()) error = "Camera unreachable and nothing cached locally yet"
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun downloadSelected() {
        val toDownload = items.filter { it.key in selected }
        downloading = true
        scope.launch {
            var ok = 0
            var total = 0
            for (item in toDownload) {
                item.previewFile?.let { total++; if (runCatching { MediaStoreSaver.saveToDownloads(context, it, client) }.getOrDefault(false)) ok++ }
                item.originalFile?.let { total++; if (runCatching { MediaStoreSaver.saveToDownloads(context, it, client) }.getOrDefault(false)) ok++ }
            }
            downloading = false
            selected = emptySet()
            snackbarHostState.showSnackbar(
                if (total == 0) "Nothing downloadable while offline" else "Downloaded $ok/$total file(s) to Downloads/Mijia4K",
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selected.size} selected" else if (offline) "Gallery (offline)" else "Gallery") },
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
                        IconButton(enabled = !downloading, onClick = { downloadSelected() }) {
                            Icon(Icons.Filled.Download, contentDescription = "Download selected")
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
                    items(items, key = { it.key }) { item ->
                        val index = items.indexOf(item)
                        GridCell(
                            item = item,
                            imageLoader = imageLoader,
                            selected = item.key in selected,
                            selectionMode = selectionMode,
                            onTap = {
                                if (selectionMode) {
                                    selected = if (item.key in selected) selected - item.key else selected + item.key
                                } else {
                                    viewerIndex = index
                                }
                            },
                            onLongPress = {
                                selected = if (item.key in selected) selected - item.key else selected + item.key
                            },
                        )
                    }
                }
            }
            if (downloading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }

    viewerIndex?.let { startIndex ->
        MediaPagerViewer(
            items = items,
            startIndex = startIndex,
            imageLoader = imageLoader,
            client = client,
            previewCache = previewCache,
            onClose = { viewerIndex = null },
            onDownload = { item ->
                scope.launch {
                    var ok = 0
                    var total = 0
                    item.previewFile?.let { total++; if (runCatching { MediaStoreSaver.saveToDownloads(context, it, client) }.getOrDefault(false)) ok++ }
                    item.originalFile?.let { total++; if (runCatching { MediaStoreSaver.saveToDownloads(context, it, client) }.getOrDefault(false)) ok++ }
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
        AsyncImage(
            model = item.thumbModel,
            contentDescription = item.name,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize().background(Color.Black),
        )
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
        if (item.localFile == null) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaPagerViewer(
    items: List<GalleryItem>,
    startIndex: Int,
    imageLoader: ImageLoader,
    client: CameraHttpClient,
    previewCache: LocalPreviewCache,
    onClose: () -> Unit,
    onDownload: (GalleryItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var showInfo by remember { mutableStateOf(false) }

    // System back (button or gesture) should close this viewer and land back
    // on the grid, not pop the whole Gallery screen off the nav stack.
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPage(item)
            } else {
                ZoomableImage(model = item.localFile ?: item.previewFile?.url ?: item.thumbModel, imageLoader = imageLoader, contentDescription = item.name)
            }
        }

        val current = items[pagerState.currentPage]

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = {
                scope.launch {
                    val fileToShare = current.localFile ?: run {
                        val group = current.previewFile ?: return@run null
                        val target = File(context.cacheDir, group.name)
                        runCatching {
                            target.outputStream().use { out -> client.downloadTo(group, out) }
                        }
                        target.takeIf { it.exists() }
                    }
                    if (fileToShare == null) return@launch
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileToShare)
                    val mime = if (current.isVideo) "video/mp4" else "image/*"
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Share",
                        ),
                    )
                }
            }) {
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
    }

    if (showInfo) {
        val current = items[pagerState.currentPage]
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showInfo = false }) { Text("Close") } },
            title = { Text(current.name) },
            text = {
                Column {
                    Text("Type: ${if (current.isVideo) "Video" else "Image"}")
                    current.previewFile?.sizeBytes?.let { Text("Preview size: ${it / 1024} KB") }
                    current.originalFile?.let { Text("Original: ${it.name} (${(it.sizeBytes ?: 0) / 1024} KB)") }
                        ?: Text("Original: not available${if (current.previewFile == null) " (offline)" else ""}")
                    Text("Cached locally: ${current.localFile != null}")
                }
            },
        )
    }
}

@Composable
private fun VideoPage(item: GalleryItem) {
    val context = LocalContext.current
    val model = item.localFile ?: item.previewFile?.url ?: item.thumbModel
    val player = remember(item.key) {
        val uri = when (model) {
            is File -> android.net.Uri.fromFile(model)
            else -> android.net.Uri.parse(model.toString())
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(item.key) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
    )
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
