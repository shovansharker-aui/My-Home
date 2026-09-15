package com.mijia4k.app.net

import android.content.Context

/**
 * The camera hotspot's SSID/password, entered once on the Connect screen.
 * Stored in plain SharedPreferences: this is the camera's own fixed device
 * credential (not a personal account password), and the app is single-user,
 * so that tradeoff is acceptable rather than pulling in an encrypted-prefs
 * dependency for it.
 */
object WifiCredentialsStore {
    private const val PREFS = "mijia4k_wifi"
    private const val KEY_SSID = "ssid"
    private const val KEY_PASSWORD = "password"

    fun save(context: Context, ssid: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SSID, ssid)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun ssid(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SSID, "").orEmpty()

    fun password(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PASSWORD, "").orEmpty()

    fun hasCredentials(context: Context): Boolean = ssid(context).isNotBlank()
}
