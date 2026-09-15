package com.mijia4k.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.mijia4k.app.net.CameraHttpClient
import com.mijia4k.app.net.CameraImageLoader
import com.mijia4k.app.net.MediaStoreSaver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { CameraHttpClient() }
    val imageLoader = remember { CameraImageLoader.get(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPath by remember { mutableStateOf(CameraHttpClient.DCIM_ROOT) }
    var entries by remember { mutableStateOf<List<CameraHttpClient.CameraFile>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var viewerFile by remember { mutableStateOf<CameraHttpClient.CameraFile?>(null) }

    val selectionMode = selected.isNotEmpty()

    fun load(path: String) {
        loading = true
        error = null
        selected = emptySet()
        scope.launch {
            try {
                entries = client.listDirectory(path)
            } catch (e: Exception) {
                error = e.message ?: "Failed to load $path"
                entries = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(currentPath) { load(currentPath) }

    fun goUp(): Boolean {
        if (currentPath == CameraHttpClient.DCIM_ROOT) return false
        val trimmed = currentPath.trimEnd('/')
        currentPath = trimmed.substringBeforeLast('/', "") + "/"
        return true
    }

    fun downloadSelected() {
        val toDownload = entries.filter { it.path in selected }
        downloading = true
        scope.launch {
            var ok = 0
            for (f in toDownload) {
                val success = runCatching { MediaStoreSaver.save(context, f, client) }.getOrDefault(false)
                if (success) ok++
            }
            downloading = false
            selected = emptySet()
            snackbarHostState.showSnackbar("Saved $ok/${toDownload.size} file(s) to Mijia4K")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selected.size} selected" else currentPath) },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectionMode -> selected = emptySet()
                            !goUp() -> onBack()
                        }
                    }) {
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
                    "Couldn't load $currentPath: $error",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                entries.isEmpty() -> Text("Empty folder", modifier = Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                ) {
                    items(entries, key = { it.path }) { entry ->
                        GridCell(
                            entry = entry,
                            imageLoader = imageLoader,
                            selected = entry.path in selected,
                            selectionMode = selectionMode,
                            onTap = {
                                when {
                                    entry.isDirectory -> currentPath = entry.path
                                    selectionMode -> selected =
                                        if (entry.path in selected) selected - entry.path else selected + entry.path
                                    else -> viewerFile = entry
                                }
                            },
                            onLongPress = {
                                if (!entry.isDirectory) {
                                    selected = if (entry.path in selected) selected - entry.path else selected + entry.path
                                }
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

    viewerFile?.let { file ->
        MediaViewer(
            file = file,
            imageLoader = imageLoader,
            onClose = { viewerFile = null },
            onDownload = {
                scope.launch {
                    val ok = runCatching { MediaStoreSaver.save(context, file, client) }.getOrDefault(false)
                    snackbarHostState.showSnackbar(if (ok) "Saved to Mijia4K" else "Download failed")
                }
            },
        )
    }
}

@Composable
private fun GridCell(
    entry: CameraHttpClient.CameraFile,
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
        if (entry.isDirectory) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(40.dp))
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            AsyncImage(
                model = entry.url,
                contentDescription = entry.name,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
            if (entry.isVideo) {
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
}

@Composable
private fun MediaViewer(
    file: CameraHttpClient.CameraFile,
    imageLoader: coil3.ImageLoader,
    onClose: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (file.isVideo) {
            val player = remember(file.path) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(file.url))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(file.path) { onDispose { player.release() } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
            )
        } else {
            Image(
                painter = coil3.compose.rememberAsyncImagePainter(model = file.url, imageLoader = imageLoader),
                contentDescription = file.name,
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
            file.name,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
        )
    }
}
