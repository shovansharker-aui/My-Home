package com.mijia4k.app.net

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-wide handle to the camera's control socket.
 *
 * The camera's own screen stays on a "connecting..." state until a client
 * completes the START_SESSION handshake on port 7878 — the stock app does
 * this as soon as it joins the hotspot, which is what flips the camera's
 * display over to its main screen. [connect] replicates that, and the
 * resulting session is kept open and shared (the control socket only accepts
 * one client at a time) so every screen reuses it instead of fighting over
 * its own connection.
 */
object CameraSession {
    val client = AmbaSocketClient()
    private val lock = Mutex()

    /**
     * Last shooting mode the camera reported, so Settings can show the right
     * parameter list. A flow rather than a plain field because Settings used
     * to read it once at composition and then never update — opening Settings
     * mid-poll could show another mode's fields entirely.
     */
    private val _currentMode = MutableStateFlow("time_lapse_record")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    var currentModeValue: String
        get() = _currentMode.value
        set(value) { _currentMode.value = value }

    /**
     * Pins the process to the camera's Wi-Fi *and then* opens the session.
     * Binding lives here rather than in one screen so every caller —
     * including the live screen's reconnect loop — recovers from the phone
     * drifting back onto mobile data, which is the failure that actually
     * happens in the field.
     */
    suspend fun connect(context: Context): Result<Unit> = lock.withLock {
        NetworkBinder.ensureBound(context)
        if (client.isConnected) return@withLock Result.success(Unit)
        client.connectAndStartSession()
    }

    suspend fun disconnect() = lock.withLock {
        client.disconnect()
    }
}
