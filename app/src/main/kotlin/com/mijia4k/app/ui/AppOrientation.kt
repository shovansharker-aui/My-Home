package com.mijia4k.app.ui

import android.content.Context
import android.content.pm.ActivityInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whole-app screen orientation. [AUTO] leaves the activity unlocked, which is
 * what makes Android show its own rotate-suggestion icon when the phone is
 * turned with auto-rotate off; the other two force a layout regardless.
 */
enum class OrientationMode(val label: String, val requested: Int) {
    AUTO("Auto (rotate icon)", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
}

object AppOrientation {
    private const val PREFS = "app_prefs"
    private const val KEY = "orientation"

    private val _mode = MutableStateFlow(OrientationMode.AUTO)
    val mode: StateFlow<OrientationMode> = _mode.asStateFlow()

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _mode.value = OrientationMode.entries.firstOrNull { it.name == saved } ?: OrientationMode.AUTO
    }

    fun set(context: Context, mode: OrientationMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }
}
