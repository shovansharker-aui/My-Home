package com.mijia4k.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 */
object NetworkBinder {
    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * Suspends until the process is actually bound (or a timeout elapses).
     * `requestNetwork`'s onAvailable callback fires asynchronously on the
     * main looper — callers that opened a socket right after calling this
     * without waiting would race it and get the phone's default route
     * (mobile data) instead of the camera's Wi-Fi, which is exactly the
     * "failed to connect... from <mobile-data-IP>" failure seen on device.
     */
    suspend fun bindToCameraWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        unbind(context)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val bound = CompletableDeferred<Boolean>()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                bound.complete(true)
            }

            override fun onLost(network: Network) {
                cm.bindProcessToNetwork(null)
            }
        }
        callback = cb
        return runCatching { cm.requestNetwork(request, cb) }
            .fold(
                onSuccess = { withTimeoutOrNull(4000) { bound.await() } ?: false },
                onFailure = { false },
            )
    }

    fun unbind(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        runCatching { cm.bindProcessToNetwork(null) }
    }
}
