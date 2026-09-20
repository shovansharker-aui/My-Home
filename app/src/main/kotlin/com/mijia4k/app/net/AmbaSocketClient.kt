package com.mijia4k.app.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * The camera answered, but refused the command. Distinct from an IO failure:
 * the socket is still healthy, so callers/teardown must not tear it down.
 */
class CameraCommandException(
    val msgId: Int,
    val rval: Int,
    val type: String?,
) : Exception(
    buildString {
        append("Camera refused msg_id=$msgId")
        type?.let { append(" (\"$it\")") }
        append(": rval=$rval")
        rvalHint(rval)?.let { append(" — $it") }
    },
)

/** Meanings confirmed by probing this firmware directly; others stay generic. */
private fun rvalHint(rval: Int): String? = when (rval) {
    -4 -> "invalid/expired session token"
    -7 -> "unsupported command or value on this firmware"
    -13 -> "this firmware can't read that setting individually"
    -14 -> "SD card missing or not ready"
    else -> null
}

/**
 * Client for the Ambarella A12 JSON control socket exposed by this camera family
 * (same firmware lineage as SJCAM SJ8 Pro / Thieye T5e) on 192.168.42.1:7878.
 *
 * Protocol: one JSON object per request/response, no framing/newline, single
 * client connection at a time. A session token must be obtained via
 * START_SESSION before any other command is accepted.
 */
class AmbaSocketClient(
    private val host: String = CameraEndpoints.HOST,
    private val port: Int = CameraEndpoints.CONTROL_PORT,
) {
    private var socket: Socket? = null
    private var writer: OutputStream? = null
    private var reader: BufferedReader? = null
    private var token: Int = 0

    // The camera's firmware is known to misbehave (or reboot) if commands are
    // sent back-to-back without a pause; serialize all calls through this lock
    // and enforce a minimum gap between sends.
    private val callLock = Mutex()
    private var lastSendAtMs = 0L

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    suspend fun connectAndStartSession(): Result<Unit> = callLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                closeQuietly()
                val s = Socket()
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.soTimeout = READ_TIMEOUT_MS
                socket = s
                writer = s.getOutputStream()
                reader = BufferedReader(InputStreamReader(s.getInputStream()))

                // token 0 is the "I don't have one yet" sentinel for START_SESSION.
                token = 0
                val response = exchange(
                    JSONObject().apply {
                        put("msg_id", MsgId.START_SESSION)
                        put("token", 0)
                    },
                )
                token = response.optInt("param", 0)
                Unit
            }.onFailure {
                // A half-open socket from a failed handshake would otherwise
                // leave isConnected reporting true (TCP connect succeeded even
                // though the START_SESSION exchange didn't).
                closeQuietly()
            }
        }
    }

    suspend fun disconnect() = callLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (isConnected) {
                    exchange(
                        JSONObject().apply {
                            put("msg_id", MsgId.STOP_SESSION)
                            put("token", token)
                        },
                    )
                }
            }
            closeQuietly()
        }
    }

    suspend fun takePhoto(): Result<JSONObject> = command(MsgId.TAKE_PHOTO)

    suspend fun startRecording(): Result<JSONObject> = command(MsgId.RECORD_START)

    suspend fun stopRecording(): Result<JSONObject> = command(MsgId.RECORD_STOP)

    suspend fun getRecordTimeSeconds(): Result<Int> =
        command(MsgId.GET_RECORD_TIME).map { it.optInt("param", 0) }

    suspend fun getBatteryLevel(): Result<Int> =
        command(MsgId.GET_BATTERY_LEVEL).map { it.optInt("param", -1) }

    /** Card capacity plus what the camera thinks still fits on it. */
    data class StorageStatus(
        val totalBytes: Long?,
        val freeBytes: Long?,
        val remainingPhotos: Int?,
        val remainingVideoSeconds: Int?,
    )

    /**
     * The camera answers GET_SPACE in **kilobytes** ("total" came back as
     * 30566400 for a 29.1 GB card), so values are scaled to bytes here;
     * reading them as bytes made a full card look like 0.0 GB. The "free"
     * reply also carries the remaining shot/second estimates, so this takes
     * two round trips rather than three.
     */
    suspend fun getStorageStatus(): StorageStatus {
        fun Long.kbToBytes(): Long? = takeIf { it > 0 }?.times(1024)

        val total = command(MsgId.GET_SPACE, type = "total").getOrNull()
            ?.optLong("param", -1)?.kbToBytes()
        val freeReply = command(MsgId.GET_SPACE, type = "free").getOrNull()
        return StorageStatus(
            totalBytes = total,
            freeBytes = freeReply?.optLong("param", -1)?.kbToBytes(),
            remainingPhotos = freeReply?.optInt("remain_photo_amount", -1)?.takeIf { it >= 0 },
            remainingVideoSeconds = freeReply?.optInt("remain_video_time", -1)?.takeIf { it >= 0 },
        )
    }

    /**
     * Changes the camera's own hotspot credentials. The exact shape of this
     * message isn't confirmed for this firmware — the caller surfaces the
     * camera's raw reply so a wrong guess is visible rather than silent.
     */
    suspend fun setWifi(ssid: String, password: String): Result<JSONObject> =
        command(MsgId.SET_WIFI) { put("ssid", ssid); put("password", password) }

    suspend fun getDeviceInfo(): Result<JSONObject> = command(MsgId.GET_DEVICEINFO)

    suspend fun getAllCurrentSettings(): Result<JSONObject> = command(MsgId.GET_ALL_CURRENT_SETTINGS)

    /**
     * Options the camera accepts for one setting right now. The key goes in
     * `param` (sending it as `type` is answered with rval=-7), and the reply
     * carries `options` plus `permission` ("settable" or "readonly" in the
     * current shooting mode).
     */
    suspend fun getSettingOptions(key: String): Result<JSONObject> =
        command(MsgId.GET_SINGLE_SETTING_OPTIONS, param = key)

    suspend fun setSetting(type: String, value: String): Result<JSONObject> =
        command(MsgId.SET_SETTING, type = type, param = value)

    // Confirmed against the real camera's GET_ALL_CURRENT_SETTINGS dump:
    // the shooting mode's real key is "mode_setting", not "camera_mode".
    suspend fun setCameraMode(mode: String): Result<JSONObject> = setSetting("mode_setting", mode)

    /**
     * Permanently removes a file from the camera's SD card. [path] is the HTTP
     * path (e.g. `/DCIM/100MEDIA/VID_xxx.MP4`); the control socket wants the
     * card's real mount point in front of it (`/tmp/SD0`). rval -26 means the
     * file isn't there — the camera removes a photo's RAW/proxy siblings along
     * with it, so by the time the second file is asked for it's already gone,
     * and that counts as done rather than as a failure.
     */
    suspend fun deleteFile(path: String): Result<JSONObject> {
        val r = command(MsgId.DELETE_FILE, param = "${CameraEndpoints.SDCARD_MOUNT}$path")
        val gone = (r.exceptionOrNull() as? CameraCommandException)?.rval == FILE_NOT_FOUND_RVAL
        return if (gone) Result.success(JSONObject()) else r
    }

    private suspend fun command(
        msgId: Int,
        type: String? = null,
        param: String? = null,
        extra: (JSONObject.() -> Unit)? = null,
    ): Result<JSONObject> = callLock.withLock {
        throttle()
        withContext(Dispatchers.IO) {
            runCatching {
                check(isConnected) { "Not connected to camera control socket" }
                exchange(
                    JSONObject().apply {
                        put("msg_id", msgId)
                        type?.let { put("type", it) }
                        param?.let { put("param", it) }
                        put("token", token)
                        extra?.invoke(this)
                    },
                )
            }.onFailure { failure ->
                // A refusal (rval != 0) means the camera is alive and talking —
                // tearing the socket down for that would turn every rejected
                // setting into a dropped connection. Only genuine IO failures
                // ("Broken pipe" when the Wi-Fi drifts away mid-session) leave
                // the socket in a zombie state where isConnected still reports
                // true locally, so only those get torn down — which is what
                // lets the next connect() correctly re-establish instead of
                // being fooled into a no-op.
                if (failure !is CameraCommandException) closeQuietly()
            }
        }
    }

    private fun closeQuietly() {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        reader = null
        writer = null
        socket = null
        token = 0
    }

    private suspend fun throttle() {
        val elapsed = System.currentTimeMillis() - lastSendAtMs
        if (elapsed in 0 until MIN_COMMAND_GAP_MS) {
            delay(MIN_COMMAND_GAP_MS - elapsed)
        }
    }

    /**
     * Sends one request and returns *its* reply, verified.
     *
     * The camera pushes unsolicited notifications (msg_id 7) at arbitrary
     * times — recording started/stopped, capture complete, card state. Reading
     * "the next JSON object" as the answer means one of those can be mistaken
     * for a reply, after which every later command reads the *previous*
     * command's answer and the session is silently off-by-one forever. So
     * replies are matched on msg_id and anything else is skipped.
     */
    private fun exchange(payload: JSONObject): JSONObject {
        val out = writer ?: error("Socket not open")
        val rd = reader ?: error("Socket not open")
        val expectedMsgId = payload.optInt("msg_id")

        out.write(payload.toString().toByteArray(Charsets.UTF_8))
        out.flush()
        lastSendAtMs = System.currentTimeMillis()

        repeat(MAX_SKIPPED_MESSAGES) {
            val reply = try {
                readOneJsonObject(rd)
            } catch (e: SocketTimeoutException) {
                throw IllegalStateException("Camera did not reply to msg_id=$expectedMsgId", e)
            }

            val replyMsgId = reply.optInt("msg_id", -1)
            if (replyMsgId != expectedMsgId) {
                Log.d(TAG, "Skipping unsolicited msg_id=$replyMsgId while awaiting $expectedMsgId: $reply")
                return@repeat
            }

            val rval = reply.optInt("rval", 0)
            if (rval != 0) {
                throw CameraCommandException(expectedMsgId, rval, payload.optString("type").ifBlank { null })
            }
            return reply
        }
        error("Camera kept sending unrelated messages instead of a reply to msg_id=$expectedMsgId")
    }

    /**
     * Reads a single balanced-brace JSON object off the stream (the camera
     * sends no delimiter). Braces inside string literals are ignored so a
     * setting value containing one can't unbalance the count.
     */
    private fun readOneJsonObject(rd: BufferedReader): JSONObject {
        val sb = StringBuilder()
        var depth = 0
        var started = false
        var inString = false
        var escaped = false

        while (true) {
            val c = rd.read()
            if (c == -1) break
            val ch = c.toChar()
            if (!started) {
                if (ch != '{') continue
                started = true
            }
            sb.append(ch)

            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return JSONObject(sb.toString())
                }
            }
        }
        error("Camera closed the connection mid-message")
    }

    private companion object {
        const val TAG = "AmbaSocketClient"
        const val CONNECT_TIMEOUT_MS = 4000
        const val READ_TIMEOUT_MS = 5000
        const val MIN_COMMAND_GAP_MS = 150L
        const val FILE_NOT_FOUND_RVAL = -26

        /** Guards against spinning forever if the camera only ever pushes notifications. */
        const val MAX_SKIPPED_MESSAGES = 8
    }
}

/** Known msg_id values for the Ambarella A12 control socket. */
object MsgId {
    const val SET_SETTING = 2
    const val GET_ALL_CURRENT_SETTINGS = 3
    const val GET_SPACE = 5
    const val NOTIFICATION = 7
    const val GET_SINGLE_SETTING_OPTIONS = 9
    const val GET_DEVICEINFO = 11
    const val GET_BATTERY_LEVEL = 13
    const val START_SESSION = 257
    const val STOP_SESSION = 258
    const val RECORD_START = 513
    const val RECORD_STOP = 514
    const val GET_RECORD_TIME = 515
    const val TAKE_PHOTO = 769
    const val SET_WIFI = 2055
    const val DELETE_FILE = 1281
}

object CameraEndpoints {
    const val HOST = "192.168.42.1"
    const val CONTROL_PORT = 7878
    const val HTTP_PORT = 80
    const val RTSP_PORT = 554
    const val RTSP_URL = "rtsp://$HOST/live"
    // HTTP paths start at /DCIM/… but the camera's filesystem has the SD card
    // mounted at /tmp/SD0 — used to translate paths for delete commands.
    const val SDCARD_MOUNT = "/tmp/SD0"
}

/**
 * GET_ALL_CURRENT_SETTINGS' "param" is a JSON *array* of single-key objects
 * (`[{"video_color":"Natural"},{"video_resolution":"3840x2160/30"},...]`),
 * not one flat object — confirmed against the real camera. Flattens it into
 * a plain key/value map for callers.
 */
fun parseSettingsArray(json: JSONObject): Map<String, String> {
    val arr = json.optJSONArray("param") ?: return emptyMap()
    val map = mutableMapOf<String, String>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        obj.keys().forEach { key -> map[key] = obj.optString(key) }
    }
    return map
}
