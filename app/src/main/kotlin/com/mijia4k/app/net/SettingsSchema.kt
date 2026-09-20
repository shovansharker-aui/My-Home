package com.mijia4k.app.net

data class SettingField(val label: String, val key: String, val isToggle: Boolean = false)

// Field labels/order below are transcribed from real screen recordings of
// the stock app. The *keys* are now confirmed too — read directly off the
// real camera's GET_ALL_CURRENT_SETTINGS dump (msg_id 3, which works
// perfectly), not guessed. That dump also revealed that the per-field
// GET_SETTING/GET_SINGLE_SETTING_OPTIONS commands (msg_id 1/9, both of which
// take a "type" argument) fail with the *same* error code regardless of
// which key is passed — so this screen reads every value from one
// GET_ALL_CURRENT_SETTINGS call instead of querying fields individually.

val VIDEO_FIELDS = listOf(
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Mic Mute", "video_mute", isToggle = true),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
    SettingField("ISO", "video_iso"),
)
val TIME_LAPSE_VIDEO_FIELDS = listOf(
    SettingField("Interval", "video_time_lapse"),
    SettingField("Video Length", "video_time_lapse_length"),
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
val SLOW_MOTION_FIELDS = listOf(
    SettingField("Speed", "video_rate"),
    SettingField("Color", "video_color"),
    SettingField("Quality", "video_quality"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
val LOOP_RECORD_FIELDS = listOf(
    SettingField("Video Length", "video_loop_length"),
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Mic Mute", "video_mute", isToggle = true),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
val VIDEO_PHOTO_FIELDS = listOf(
    SettingField("Interval", "video_piv_time_lapse"),
    SettingField("Color", "video_color"),
    SettingField("Resolution", "video_resolution"),
    SettingField("Quality", "video_quality"),
    SettingField("Mic Mute", "video_mute", isToggle = true),
    SettingField("Stamp", "video_stamp"),
    SettingField("Auto Record", "video_record_startup", isToggle = true),
    SettingField("Metering Mode", "video_metering_mode"),
    SettingField("EV", "video_ev_bias"),
    SettingField("WB", "video_white_balance"),
)
val PHOTO_FIELDS = listOf(
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("Shutter", "photo_shutter"),
    SettingField("ISO", "photo_iso"),
    SettingField("RAW", "photo_raw", isToggle = true),
    SettingField("WB", "photo_wb"),
    // Unconfirmed: the real camera dump doesn't show an obvious
    // "photo_color" key — "photo_digital_effect" is the closest candidate
    // but its "off" value doesn't look like a color-profile name, so this
    // one may still be wrong.
    SettingField("Color", "photo_digital_effect"),
)
val TIMER_FIELDS = listOf(
    SettingField("Countdown", "photo_selftimer"),
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("ISO", "photo_iso"),
    SettingField("WB", "photo_wb"),
)
val BURST_FIELDS = listOf(
    SettingField("Rate", "photo_burst_frequence"),
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("ISO", "photo_iso"),
    SettingField("WB", "photo_wb"),
)
val TIME_LAPSE_PHOTO_FIELDS = listOf(
    SettingField("Interval", "photo_time_lapse"),
    SettingField("Aspect Ratio", "photo_size"),
    SettingField("Stamp", "photo_stamp"),
    SettingField("Metering Mode", "photo_metering_mode"),
    SettingField("EV", "photo_ev_bias"),
    SettingField("ISO", "photo_iso"),
    SettingField("WB", "photo_wb"),
)

// "Camera Parameter" rows, in the stock app's exact order. Rotate reads back
// as "up"/"down" rather than on/off, so it needs its own on/off value pair.
val ROTATE_FIELD = SettingField("Rotate", "auto_rotate", isToggle = true)
val GENERAL_FIELDS = listOf(
    SettingField("Video Standard", "system_type"),
    SettingField("Beep Volume", "prompt_volume"),
    SettingField("Default Mode", "default_boot_mode"),
    ROTATE_FIELD,
    SettingField("Auto Screen Lock", "auto_lock_screen"),
    SettingField("Auto Power Off", "auto_power_off"),
    SettingField("Wi-Fi Auto On", "wifi_auto_start", isToggle = true),
)

// Values confirmed live against the real camera. Must match CAMERA_MODES in
// ShootScreen.kt.
val MODE_LABELS = mapOf(
    "normal_record" to "Video",
    "time_lapse_record" to "Time Lapse Video",
    "slow_motion" to "Slow Motion",
    "loop_record" to "Loop Record",
    "record_capture" to "Video+Photo",
    "normal_capture" to "Photo",
    "timing_capture" to "Timer",
    "continuous_capture" to "Burst",
    "time_lapse_capture" to "Time Lapse Photo",
)

fun fieldsFor(modeValue: String): List<SettingField> = when (modeValue) {
    "normal_record" -> VIDEO_FIELDS
    "time_lapse_record" -> TIME_LAPSE_VIDEO_FIELDS
    "slow_motion" -> SLOW_MOTION_FIELDS
    "loop_record" -> LOOP_RECORD_FIELDS
    "record_capture" -> VIDEO_PHOTO_FIELDS
    "normal_capture" -> PHOTO_FIELDS
    "timing_capture" -> TIMER_FIELDS
    "continuous_capture" -> BURST_FIELDS
    "time_lapse_capture" -> TIME_LAPSE_PHOTO_FIELDS
    else -> PHOTO_FIELDS
}

fun isToggleOn(field: SettingField, value: String?): Boolean = when {
    value == null -> false
    field.key == ROTATE_FIELD.key -> value.equals("down", ignoreCase = true)
    else -> value.equals("on", true) || value == "1" || value.equals("true", true)
}

fun toggleValue(field: SettingField, checked: Boolean): String = when (field.key) {
    ROTATE_FIELD.key -> if (checked) "down" else "up"
    else -> if (checked) "on" else "off"
}

/** What the camera accepts for one setting, straight from its own option query. */
data class SettingOptions(
    val values: List<String>,
    val settable: Boolean,
    /** Shooting mode active when this was fetched; "readonly" only means something for that mode. */
    val fetchedInMode: String,
)

/** Every key that has a picker, so option lists can be preloaded for all of them. */
fun allPickerKeys(): List<String> =
    listOf(
        VIDEO_FIELDS, TIME_LAPSE_VIDEO_FIELDS, SLOW_MOTION_FIELDS, LOOP_RECORD_FIELDS, VIDEO_PHOTO_FIELDS,
        PHOTO_FIELDS, TIMER_FIELDS, BURST_FIELDS, TIME_LAPSE_PHOTO_FIELDS, GENERAL_FIELDS,
    ).flatten().filter { !it.isToggle }.map { it.key }.distinct()

private val VALUE_LABELS = mapOf(
    "last_used_mode" to "Last used",
    "date/time" to "Date & Time",
    "off" to "Off",
    "on" to "On",
    "mute" to "Mute",
    "up" to "Up",
    "down" to "Down",
)

/** Human label for a raw camera value; the raw value is always what gets written back. */
fun displayValue(key: String, raw: String?): String {
    if (raw.isNullOrBlank()) return "..."
    if (key == "default_boot_mode" || key == "mode_setting") MODE_LABELS[raw]?.let { return it }
    VALUE_LABELS[raw]?.let { return it }
    if (key.endsWith("_ev_bias")) return if (raw == "+0") "0" else raw
    if (key == "video_resolution") return raw.replace("/", " @")
    return if (raw.all { it.isLetter() || it == '_' }) raw.replace('_', ' ').replaceFirstChar { it.uppercase() } else raw
}

/** Compact form for the live screen's icon chips, where width is tight. */
fun shortValue(key: String, raw: String?): String {
    if (key == "video_resolution" && raw != null) {
        val m = Regex("""\d+x(\d+)/(\d+)""").matchEntire(raw)
        if (m != null) {
            val (height, fps) = m.destructured
            return (if (height == "2160") "4K" else "${height}p") + fps
        }
    }
    return displayValue(key, raw)
}
