package com.mijia4k.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mijia4k.app.net.CameraSession
import kotlinx.coroutines.launch

/**
 * Everything the camera's own protocol will tell us about itself, dumped as
 * raw JSON. The Ambarella socket protocol doesn't document its setting keys
 * anywhere public, so this screen exists to read them straight off the real
 * camera (all current settings + current mode) before building dedicated
 * pickers for each one — same approach that found the file-listing API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var allSettings by remember { mutableStateOf("") }
    var modeSettings by remember { mutableStateOf("") }
    var deviceInfo by remember { mutableStateOf("") }
    var queryType by remember { mutableStateOf("") }
    var queryResult by remember { mutableStateOf("") }

    fun refresh() {
        loading = true
        scope.launch {
            CameraSession.connect()
            allSettings = CameraSession.client.getAllCurrentSettings()
                .fold({ it.toString(2) }, { "error: ${it.message}" })
            modeSettings = CameraSession.client.getCurrentModeSettings()
                .fold({ it.toString(2) }, { "error: ${it.message}" })
            deviceInfo = CameraSession.client.getDeviceInfo()
                .fold({ it.toString(2) }, { "error: ${it.message}" })
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Button(onClick = { refresh() }, enabled = !loading) {
                Text(if (loading) "Loading..." else "Refresh")
            }
            if (loading) CircularProgressIndicator(modifier = Modifier.padding(8.dp))

            SectionTitle("Device info")
            MonoBlock(deviceInfo)

            SectionTitle("Current mode settings")
            MonoBlock(modeSettings)

            SectionTitle("All current settings")
            MonoBlock(allSettings)

            SectionTitle("Look up options for a specific setting")
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = queryType,
                    onValueChange = { queryType = it },
                    label = { Text("setting key, e.g. camera_mode") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(onClick = {
                scope.launch {
                    queryResult = CameraSession.client.getSettingOptions(queryType)
                        .fold({ it.toString(2) }, { "error: ${it.message}" })
                }
            }) {
                Text("Query options")
            }
            MonoBlock(queryResult)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun MonoBlock(text: String) {
    if (text.isNotBlank()) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
