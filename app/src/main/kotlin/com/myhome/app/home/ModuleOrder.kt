package com.myhome.app.home

import android.content.Context

/** The order the user chose for the module tiles; modules not mentioned (new ones) follow, in their built-in order. */
object ModuleOrder {
    private const val PREFS = "home_shell"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<String> =
        prefs(context).getString("order", "").orEmpty().split(',').filter { it.isNotBlank() }

    fun save(context: Context, ids: List<String>) {
        prefs(context).edit().putString("order", ids.joinToString(",")).apply()
    }

    fun apply(modules: List<HomeModule>, ids: List<String>): List<HomeModule> =
        ids.mapNotNull { id -> modules.firstOrNull { it.id == id } } + modules.filter { it.id !in ids }
}
