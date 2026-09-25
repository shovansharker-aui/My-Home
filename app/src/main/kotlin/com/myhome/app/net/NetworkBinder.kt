package com.myhome.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The camera's hotspot has no internet, so Android's normal network
 * selection deprioritizes it and routes the phone's traffic elsewhere
 * (mobile data, another saved Wi-Fi) even while the phone stays radio-
 * associated with it — which breaks every request this app makes. This
 * explicitly requests that no-internet Wi-Fi and binds the process to it,
 * so our sockets/HTTP/RTSP calls keep going over the camera's network
 * regardless of what Android considers the "default" network for
 * everything else on the phone.
 *
 * The request is registered **once** and left registered: when the camera's
 * Wi-Fi drops and later comes back, `onAvailable` fires again and re-binds
 * on its own. That matters because the reconnect loop that notices the drop
 * lives on a different screen than the one that first bound the process —
 * without a standing request, traffic silently stayed on mobile data and no
 * amount of retrying could reach the camera again.
 */
object NetworkBinder {
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var boundNetwork: Network? = null

    /** Completed by whichever [ensureBound] call is waiting for the next bind. */
    @Volatile
    private var pendingBind: CompletableDeferred<Boolean>? = null

    val isBound: Boolean get() = boundNetwork != null

    /**
     * Ensures this process's traffic is pinned to the camera's Wi-Fi,
     * suspending until it is (or a timeout elapses). Cheap to call
     * repeatedly — once bound it returns immediately, so the reconnect loop
     * can call it on every attempt without churning network requests.
     */
    suspend fun ensureBound(context: Context): Boolean {
        if (boundNetwork != null) return true
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        val waiter = CompletableDeferred<Boolean>()
        pendingBind = waiter

        if (callback == null) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val ok = runCatching { cm.bindProcessToNetwork(network) }.getOrDefault(false)
                    boundNetwork = if (ok) network else null
                    Log.d(TAG, "Camera Wi-Fi available, bound=$ok")
                    pendingBind?.complete(ok)
                }

                override fun onLost(network: Network) {
                    if (network == boundNetwork) {
                        Log.d(TAG, "Camera Wi-Fi lost, unbinding")
                        boundNetwork = null
                        runCatching { cm.bindProcessToNetwork(null) }
                    }
                }
            }
            val registered = runCatching { cm.requestNetwork(request, cb) }.isSuccess
            if (!registered) {
                pendingBind = null
                return false
            }
            callback = cb
        }

        return withTimeoutOrNull(BIND_TIMEOUT_MS) { waiter.await() } ?: (boundNetwork != null)
    }

    /** Releases the standing request — the app is done talking to the camera. */
    fun release(context: Context) {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        boundNetwork = null
        pendingBind = null
        runCatching { cm.bindProcessToNetwork(null) }
    }

    private const val TAG = "NetworkBinder"
    private const val BIND_TIMEOUT_MS = 4000L
}
