package com.myhome.app.home

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.myhome.app.modules.printer.ui.bluetoothPermissions
import com.myhome.app.modules.printer.ui.hasBluetoothPermissions
import kotlinx.coroutines.delay

fun isBluetoothOn(context: Context): Boolean =
    context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

/**
 * Returns a function that makes sure Bluetooth is usable — asking for the
 * permission and for the phone's Bluetooth to be switched on if it is not — and
 * then runs [onReady]. Android shows its own "turn on Bluetooth?" prompt; apps
 * cannot switch Bluetooth on silently.
 */
@Composable
fun rememberBluetoothEnabler(onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val ready = rememberUpdatedState(onReady)
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (isBluetoothOn(context)) ready.value()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasBluetoothPermissions(context)) {
            if (isBluetoothOn(context)) ready.value() else enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
    return {
        when {
            !hasBluetoothPermissions(context) -> permissionLauncher.launch(bluetoothPermissions())
            !isBluetoothOn(context) -> enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> ready.value()
        }
    }
}

/**
 * While the calling screen is open, keeps trying to reach a saved device: every
 * few seconds, if Bluetooth is on, nothing is connected or connecting, and the
 * user has not switched the link off on purpose, it calls [connect].
 */
@Composable
fun AutoConnectWhileOpen(
    paused: Boolean,
    canTry: () -> Boolean,
    target: () -> String?,
    connect: suspend (String) -> Unit,
) {
    val context = LocalContext.current
    val pausedNow = rememberUpdatedState(paused)
    val canTryNow = rememberUpdatedState(canTry)
    val targetNow = rememberUpdatedState(target)
    val connectNow = rememberUpdatedState(connect)
    LaunchedEffect(Unit) {
        while (true) {
            if (!pausedNow.value && hasBluetoothPermissions(context) && isBluetoothOn(context) && canTryNow.value()) {
                targetNow.value()?.let { connectNow.value(it) }
            }
            delay(3_000)
        }
    }
}
