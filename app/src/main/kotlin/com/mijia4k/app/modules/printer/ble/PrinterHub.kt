package com.mijia4k.app.modules.printer.ble

import android.content.Context
import com.mijia4k.app.modules.printer.protocol.CatProtocol

/** App-wide holder for the printer link and the few things worth remembering between runs. */
object PrinterHub {
    private const val PREFS = "printer_module"
    private var connection: PrinterConnection? = null

    fun connection(context: Context): PrinterConnection =
        connection ?: PrinterConnection(context.applicationContext).also { connection = it }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastAddress(context: Context): String? = prefs(context).getString("address", null)
    fun lastName(context: Context): String? = prefs(context).getString("name", null)

    fun remember(context: Context, name: String, address: String) {
        prefs(context).edit().putString("address", address).putString("name", name).apply()
    }

    fun forget(context: Context) {
        prefs(context).edit().remove("address").remove("name").apply()
    }

    fun density(context: Context): CatProtocol.Density =
        CatProtocol.Density.entries.firstOrNull { it.name == prefs(context).getString("density", null) }
            ?: CatProtocol.Density.MEDIUM

    fun setDensity(context: Context, density: CatProtocol.Density) {
        prefs(context).edit().putString("density", density.name).apply()
    }
}
