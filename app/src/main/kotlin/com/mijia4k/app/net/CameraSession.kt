package com.mijia4k.app.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-wide handle to the camera's control socket.
 *
 * The camera's own screen stays on a "connecting..." state until a client
 * completes the AMBA_START_SESSION handshake on port 7878 — the stock app
 * does this as soon as it joins the hotspot, which is what flips the
 * camera's display over to its main screen. [connect] replicates that, and
 * the resulting session is kept open and shared (the control socket only
 * accepts one client at a time) so the Shoot screen can reuse it instead of
 * each screen fighting over its own connection.
 */
object CameraSession {
    val client = AmbaSocketClient()
    private val lock = Mutex()

    /** Last shooting mode value set from the Shoot screen — read by Settings to show the right parameter list. */
    var currentModeValue: String = "time_lapse_record"

    suspend fun connect(): Result<Unit> = lock.withLock {
        if (client.isConnected) return@withLock Result.success(Unit)
        client.connectAndStartSession()
    }

    suspend fun disconnect() = lock.withLock {
        client.disconnect()
    }
}
