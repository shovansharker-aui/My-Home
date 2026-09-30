package com.myhome.app.home

import android.content.Context

/** How the dashboard shows its module tiles. */
object HomeLayout {
    enum class Mode { LIST, GRID }

    private const val PREFS = "home_shell"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Mode =
        if (prefs(context).getString("layout", null) == "GRID") Mode.GRID else Mode.LIST

    fun save(context: Context, mode: Mode) {
        prefs(context).edit().putString("layout", mode.name).apply()
    }
}
