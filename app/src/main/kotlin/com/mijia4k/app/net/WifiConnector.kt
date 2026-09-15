package com.mijia4k.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Actively connects the phone to the camera's hotspot given a saved
 * SSID/password, instead of just waiting for the user to have already
 * joined it manually. On Android 10+ this uses [WifiNetworkSpecifier],
 * which shows a brief system confirmation (by OS design, apps can't silently
 * join arbitrary Wi-Fi) but otherwise requires no special permission or
 * manual Wi-Fi toggling — Android turns Wi-Fi on for this automatically.
 */
object WifiConnector {
    suspend fun connect(context: Context, ssid: String, password: String, timeoutMs: Long = 15_000): Boolean {
        if (ssid.isBlank()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectModern(context, ssid, password, timeoutMs)
        } else {
            connectLegacy(context, ssid, password, timeoutMs)
        }
    }

    private suspend fun connectModern(context: Context, ssid: String, password: String, timeoutMs: Long): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (password.isNotEmpty()) specifierBuilder.setWpa2Passphrase(password)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        val result = CompletableDeferred<Boolean>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                result.complete(true)
            }

            override fun onUnavailable() {
                result.complete(false)
            }
        }

        cm.requestNetwork(request, callback)
        return withTimeoutOrNull(timeoutMs) { result.await() } ?: false
    }

    @Suppress("DEPRECATION")
    private suspend fun connectLegacy(context: Context, ssid: String, password: String, timeoutMs: Long): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val config = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (password.isNotEmpty()) {
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            } else {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }
        val netId = wifiManager.addNetwork(config)
        if (netId == -1) return false
        wifiManager.disconnect()
        wifiManager.enableNetwork(netId, true)
        wifiManager.reconnect()
        // Pre-Q has no reliable per-request connect callback; give the radio
        // a moment to associate before the caller checks camera reachability.
        delay(timeoutMs.coerceAtMost(5_000))
        return true
    }
}
