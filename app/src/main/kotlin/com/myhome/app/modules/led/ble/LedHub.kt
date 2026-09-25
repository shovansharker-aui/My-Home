package com.myhome.app.modules.led.ble

import android.content.Context

/** App-wide holder for the LED link and the last strip used. */
object LedHub {
    private const val PREFS = "led_module"
    private var connection: LedConnection? = null

    fun connection(context: Context): LedConnection =
        connection ?: LedConnection(context.applicationContext).also { connection = it }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastAddress(context: Context): String? = prefs(context).getString("address", null)

    fun remember(context: Context, name: String, address: String) {
        prefs(context).edit().putString("address", address).putString("name", name).apply()
    }

    /** Colours the user saved from the wheel, as ARGB ints. */
    fun customColors(context: Context): List<Int> =
        prefs(context).getString("custom_colors", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    fun setCustomColors(context: Context, colors: List<Int>) {
        prefs(context).edit().putString("custom_colors", colors.joinToString(",")).apply()
    }
}
