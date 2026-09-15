package com.mijia4k.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
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
import com.mijia4k.app.net.CameraHttpClient
import com.mijia4k.app.net.MediaStoreSaver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { CameraHttpClient() }
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPath by remember { mutableStateOf(CameraHttpClient.DCIM_ROOT) }
    var entries by remember { mutableStateOf<List<CameraHttpClient.CameraFile>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(currentPath) },
                navigationIcon = {
                    IconButton(onClick = { if (!goUp()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(
                            enabled = !downloading,
                            onClick = {
                                val toDownload = entries.filter { it.path in selected }
                                downloading = true
                                scope.launch {
                                    var ok = 0
                                    for (f in toDownload) {
                                        val success = runCatching {
                                            MediaStoreSaver.save(context, f, client)
                                        }.getOrDefault(false)
                                        if (success) ok++
                                    }
                                    downloading = false
                                    selected = emptySet()
                                    snackbarHostState.showSnackbar("Saved $ok/${toDownload.size} file(s) to Mijia4K")
                                }
                            },
                        ) {
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
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDirectory) {
                                        currentPath = entry.path
                                    } else {
                                        selected = if (entry.path in selected) {
                                            selected - entry.path
                                        } else {
                                            selected + entry.path
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!entry.isDirectory) {
                                Checkbox(
                                    checked = entry.path in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + entry.path else selected - entry.path
                                    },
                                )
                            }
                            Icon(
                                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                contentDescription = null,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                                entry.sizeBytes?.let {
                                    Text(
                                        "${it / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (downloading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}
