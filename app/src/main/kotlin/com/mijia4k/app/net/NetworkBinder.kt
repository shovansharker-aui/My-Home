package com.mijia4k.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

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

    fun bindToCameraWifi(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        unbind(context)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                cm.bindProcessToNetwork(null)
            }
        }
        callback = cb
        runCatching { cm.requestNetwork(request, cb) }
    }

    fun unbind(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        runCatching { cm.bindProcessToNetwork(null) }
    }
}
