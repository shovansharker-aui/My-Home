package com.mijia4k.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.mijia4k.app.net.CameraHttpClient
import com.mijia4k.app.net.CameraImageLoader
import com.mijia4k.app.net.LocalPreviewCache
import com.mijia4k.app.net.MediaStoreSaver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { CameraHttpClient() }
    val previewCache = remember { LocalPreviewCache(context) }
    val imageLoader = remember { CameraImageLoader.get(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var groups by remember { mutableStateOf<List<CameraHttpClient.MediaGroup>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var viewerGroup by remember { mutableStateOf<CameraHttpClient.MediaGroup?>(null) }

    val selectionMode = selected.isNotEmpty()

    fun refresh() {
        loading = true
        error = null
        selected = emptySet()
        scope.launch {
            try {
                val loaded = client.listAllMedia()
                groups = loaded
                loading = false
                syncing = true
                previewCache.sync(client, loaded)
                syncing = false
            } catch (e: Exception) {
                error = e.message ?: "Failed to load media"
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun downloadSelected() {
        val toDownload = groups.filter { it.previewFile.path in selected }
        downloading = true
        scope.launch {
            var ok = 0
            var total = 0
            for (g in toDownload) {
                total++
                if (runCatching { MediaStoreSaver.saveToDownloads(context, g.previewFile, client) }.getOrDefault(false)) ok++
                g.originalFile?.let { original ->
                    total++
                    if (runCatching { MediaStoreSaver.saveToDownloads(context, original, client) }.getOrDefault(false)) ok++
                }
            }
            downloading = false
            selected = emptySet()
            snackbarHostState.showSnackbar("Downloaded $ok/$total file(s) to Downloads/Mijia4K")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selected.size} selected" else "Gallery") },
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
                error != null -> Text(
                    "Couldn't load the camera's media: $error",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                groups.isEmpty() -> Text("No photos or videos on the camera", modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                ) {
                    items(groups, key = { it.previewFile.path }) { group ->
                        val local = previewCache.localFileFor(group).takeIf { it.exists() }
                        GridCell(
                            group = group,
                            model = local ?: group.previewFile.url,
                            imageLoader = imageLoader,
                            selected = group.previewFile.path in selected,
                            selectionMode = selectionMode,
                            onTap = {
                                if (selectionMode) {
                                    val key = group.previewFile.path
                                    selected = if (key in selected) selected - key else selected + key
                                } else {
                                    viewerGroup = group
                                }
                            },
                            onLongPress = {
                                val key = group.previewFile.path
                                selected = if (key in selected) selected - key else selected + key
                            },
                        )
                    }
                }
            }
            if (syncing) {
                Text(
                    "Syncing previews...",
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                )
            }
            if (downloading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }

    viewerGroup?.let { group ->
        val local = previewCache.localFileFor(group).takeIf { it.exists() }
        MediaViewer(
            model = local ?: group.previewFile.url,
            isVideo = group.previewFile.isVideo,
            name = group.previewFile.name,
            imageLoader = imageLoader,
            onClose = { viewerGroup = null },
            onDownload = {
                scope.launch {
                    var ok = 0
                    var total = 1
                    if (runCatching { MediaStoreSaver.saveToDownloads(context, group.previewFile, client) }.getOrDefault(false)) ok++
                    group.originalFile?.let {
                        total++
                        if (runCatching { MediaStoreSaver.saveToDownloads(context, it, client) }.getOrDefault(false)) ok++
                    }
                    snackbarHostState.showSnackbar("Downloaded $ok/$total file(s) to Downloads/Mijia4K")
                }
            },
        )
    }
}

@Composable
private fun GridCell(
    group: CameraHttpClient.MediaGroup,
    model: Any,
    imageLoader: coil3.ImageLoader,
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
            model = model,
            contentDescription = group.previewFile.name,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize().background(Color.Black),
        )
        if (group.previewFile.isVideo) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onTap() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
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
private fun MediaViewer(
    model: Any,
    isVideo: Boolean,
    name: String,
    imageLoader: coil3.ImageLoader,
    onClose: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            val player = remember(model) {
                val uri = when (model) {
                    is java.io.File -> android.net.Uri.fromFile(model)
                    else -> android.net.Uri.parse(model.toString())
                }
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(model) { onDispose { player.release() } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
            )
        } else {
            Image(
                painter = rememberAsyncImagePainter(model = model, imageLoader = imageLoader),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
        IconButton(onClick = onDownload, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
        }
        Text(
            name,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
        )
    }
}
