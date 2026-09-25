package com.myhome.app.net

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide handle to the camera's control socket, plus a local mirror of the
 * camera's settings and option lists.
 *
 * The mirror is what makes the UI instant: screens read [settings], [options]
 * and [currentMode] straight from memory, changes are applied there first and
 * confirmed with the camera in the background. (The camera itself applies a
 * mode switch in well under a second — the old lag came from waiting on a 3s
 * poll and sequential re-reads before showing anything.)
 *
 * The control socket only accepts one client at a time, so every screen
 * shares this session instead of opening its own.
 */
object CameraSession {
    val client = AmbaSocketClient()
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentMode = MutableStateFlow("time_lapse_record")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    var currentModeValue: String
        get() = _currentMode.value
        set(value) { _currentMode.value = value }

    private val _settings = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Last known value of every camera setting (raw camera strings). */
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    private val _options = MutableStateFlow<Map<String, SettingOptions>>(emptyMap())

    /** Option lists per setting key, filled in the background after connecting. */
    val options: StateFlow<Map<String, SettingOptions>> = _options.asStateFlow()

    // Values the user just changed that the camera hasn't confirmed yet. A
    // background settings read landing in that window would otherwise flip the
    // UI back to the old value for a moment.
    private val inFlightWrites = ConcurrentHashMap<String, String>()

    @Volatile
    private var pendingMode: String? = null

    private var prefetchJob: Job? = null

    private const val PREFS = "camera_session"
    private const val KEY_RESTORE_AUTO_OFF = "restore_auto_power_off"
    private const val KEY_SHUTDOWN_UNTIL = "shutdown_until"
    private const val SHUTDOWN_WINDOW_MS = 4 * 60_000L

    /** True while a requested switch-off is running its course; reconnecting now would keep the camera awake. */
    fun shutdownPending(context: Context): Boolean =
        System.currentTimeMillis() < context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SHUTDOWN_UNTIL, 0L)

    /**
     * The camera has no remote power-off command we could find, so this sets
     * its own auto power-off to the shortest value (2 minutes), lets go of the
     * connection, and stops reconnecting until it has had time to switch off.
     * The user's previous auto power-off value is put back the next time a
     * session starts.
     */
    suspend fun scheduleShutdown(context: Context): Result<Unit> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = _settings.value["auto_power_off"]
        if (current != null && current != "2min" && !prefs.contains(KEY_RESTORE_AUTO_OFF)) {
            prefs.edit().putString(KEY_RESTORE_AUTO_OFF, current).apply()
        }
        val result = writeSetting("auto_power_off", "2min")
        if (result.isSuccess) {
            prefs.edit().putLong(KEY_SHUTDOWN_UNTIL, System.currentTimeMillis() + SHUTDOWN_WINDOW_MS).apply()
            disconnect()
        }
        return result
    }

    private suspend fun restoreAutoPowerOff(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_RESTORE_AUTO_OFF, null) ?: return
        if (writeSetting("auto_power_off", saved).isSuccess) {
            prefs.edit().remove(KEY_RESTORE_AUTO_OFF).remove(KEY_SHUTDOWN_UNTIL).apply()
        }
    }

    /**
     * Pins the process to the camera's Wi-Fi *and then* opens the session.
     * Binding lives here rather than in one screen so every caller —
     * including the live screen's reconnect loop — recovers from the phone
     * drifting back onto mobile data, which is the failure that actually
     * happens in the field.
     */
    suspend fun connect(context: Context): Result<Unit> {
        if (shutdownPending(context)) return Result.failure(IllegalStateException("Camera is switching off"))
        val fresh = lock.withLock {
            NetworkBinder.ensureBound(context)
            if (client.isConnected) return@withLock Result.success(false)
            client.connectAndStartSession().map { true }
        }
        if (fresh.getOrNull() == true) onSessionStarted(context)
        return fresh.map { }
    }

    /**
     * Deletes one file, reconnecting if the control socket turns out to be
     * dead. The socket can go stale while another screen (the Album) is open
     * and only reveals it on the next command, which would otherwise fail the
     * whole batch.
     */
    suspend fun deleteFile(context: Context, path: String): Result<Unit> {
        if (!client.isConnected) connect(context)
        var result = client.deleteFile(path)
        if (result.isFailure && result.exceptionOrNull() !is CameraCommandException) {
            connect(context)
            result = client.deleteFile(path)
        }
        return result.map { }
    }

    suspend fun disconnect() = lock.withLock {
        client.disconnect()
    }

    private fun onSessionStarted(context: Context) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            ensureViewfinder()
            restoreAutoPowerOff(context)
            prefetchOptions(_currentMode.value)
        }
    }

    /** Makes sure the camera is streaming (app_status "vf"); an idle camera gives the live view nothing to show. */
    suspend fun ensureViewfinder() {
        val status = refreshSettings()?.get("app_status")
        android.util.Log.d("CameraSession", "ensureViewfinder: app_status=$status")
        // Only an idle camera needs the feed started; sending it mid-recording
        // makes the camera stop answering on the control port.
        if (status == "idle") {
            val r = client.startViewfinder()
            android.util.Log.d("CameraSession", "startViewfinder -> ${r.getOrNull() ?: r.exceptionOrNull()}")
        }
    }

    /** Re-reads every setting from the camera and merges in anything still being written. */
    suspend fun refreshSettings(): Map<String, String>? {
        val fresh = client.getAllCurrentSettings().getOrNull()?.let { parseSettingsArray(it) } ?: return null
        val merged = fresh.toMutableMap()
        merged.putAll(inFlightWrites)
        val pending = pendingMode
        if (pending != null) {
            merged["mode_setting"] = pending
        } else {
            merged["mode_setting"]?.takeIf { it in MODE_LABELS }?.let { _currentMode.value = it }
        }
        _settings.value = merged
        return merged
    }

    /**
     * Switches shooting mode. The UI flips immediately; the camera confirms in
     * the background, then the new mode's values and option lists are loaded
     * so the settings screen is already populated by the time it's opened.
     * On refusal the real mode is read back and the UI snaps to it.
     */
    suspend fun switchMode(mode: String): Result<Unit> {
        pendingMode = mode
        _currentMode.value = mode
        _settings.update { it + ("mode_setting" to mode) }

        val result = client.setCameraMode(mode)
        if (pendingMode == mode) pendingMode = null
        if (result.isFailure) {
            refreshSettings()
            return Result.failure(result.exceptionOrNull() ?: IllegalStateException("mode change failed"))
        }
        if (_currentMode.value == mode) {
            refreshSettings()
            prefetchJob?.cancel()
            prefetchJob = scope.launch { prefetchOptions(mode) }
        }
        return Result.success(Unit)
    }

    /** Writes one setting: shown at once, sent to the camera, then verified with a single read-back. */
    suspend fun writeSetting(key: String, value: String): Result<Unit> {
        val previous = _settings.value[key]
        inFlightWrites[key] = value
        _settings.update { it + (key to value) }

        val result = client.setSetting(key, value)
        inFlightWrites.remove(key)
        if (result.isFailure && previous != null) {
            _settings.update { it + (key to previous) }
        }
        refreshSettings()
        return result.map { }
    }

    /** Loads option lists for the fields of [mode] first, then everything else, skipping what's cached. */
    private suspend fun prefetchOptions(mode: String) {
        val first = (fieldsFor(mode) + GENERAL_FIELDS).filter { !it.isToggle }.map { it.key }
        val fetchedFor = _options.value
        for (key in (first + allPickerKeys()).distinct()) {
            // Mode-specific keys are refetched after a mode change because
            // their "readonly" flag depends on the mode; the rest is cached.
            val cached = fetchedFor[key]
            val inCurrentSet = key in first
            if (cached != null && (!inCurrentSet || cached.fetchedInMode == mode)) continue
            fetchOptions(key)
        }
    }

    /** Fetches one option list on demand (used by the picker if it opens before the prefetch got there). */
    suspend fun fetchOptions(key: String): SettingOptions? {
        val json = client.getSettingOptions(key).getOrNull() ?: return null
        val arr = json.optJSONArray("options") ?: return null
        val options = SettingOptions(
            values = (0 until arr.length()).map { arr.optString(it) },
            settable = !json.optString("permission").equals("readonly", ignoreCase = true),
            fetchedInMode = pendingMode ?: _currentMode.value,
        )
        _options.update { it + (key to options) }
        return options
    }
}
