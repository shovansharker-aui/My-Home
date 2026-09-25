package com.myhome.app.modules.oraimo.link

import android.content.Context

/** App-wide holder for the earbuds link and the pair last used. */
object EarbudsHub {
    private const val PREFS = "oraimo_module"
    private var connection: EarbudsConnection? = null

    fun connection(context: Context): EarbudsConnection =
        connection ?: EarbudsConnection(context.applicationContext).also { connection = it }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastAddress(context: Context): String? = prefs(context).getString("address", null)

    fun remember(context: Context, address: String) {
        prefs(context).edit().putString("address", address).apply()
    }
}
