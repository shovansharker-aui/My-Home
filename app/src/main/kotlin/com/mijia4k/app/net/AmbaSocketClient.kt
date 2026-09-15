package com.mijia4k.app.net

import kotlinx.coroutines.Dispatchers
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
 * Client for the Ambarella A12 JSON control socket exposed by this camera family
 * (same firmware lineage as SJCAM SJ8 Pro / Thieye T5e) on 192.168.42.1:7878.
 *
 * Protocol: one JSON object per request/response, no framing/newline, single
 * client connection at a time. A session token must be obtained via
 * [startSession] before any other command is accepted.
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

    suspend fun connectAndStartSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS
            socket = s
            writer = s.getOutputStream()
            reader = BufferedReader(InputStreamReader(s.getInputStream()))

            val response = sendRaw(JSONObject().apply {
                put("msg_id", MsgId.START_SESSION)
                put("token", 0)
            })
            token = response.optInt("param", 0)
            Unit
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching {
            if (isConnected) {
                sendRaw(JSONObject().apply {
                    put("msg_id", MsgId.STOP_SESSION)
                    put("token", token)
                })
            }
        }
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        reader = null
        writer = null
        socket = null
    }

    suspend fun takePhoto(): Result<JSONObject> = command(MsgId.TAKE_PHOTO)

    suspend fun startRecording(): Result<JSONObject> = command(MsgId.RECORD_START)

    suspend fun stopRecording(): Result<JSONObject> = command(MsgId.RECORD_STOP)

    suspend fun getRecordTimeSeconds(): Result<Int> =
        command(MsgId.GET_RECORD_TIME).map { it.optInt("param", 0) }

    suspend fun getBatteryLevel(): Result<Int> =
        command(MsgId.GET_BATTERY_LEVEL).map { it.optInt("param", -1) }

    suspend fun getStorageSpaceBytes(): Result<Long> =
        command(MsgId.GET_SPACE, type = "total").map { it.optLong("param", -1) }

    suspend fun getDeviceInfo(): Result<JSONObject> = command(MsgId.GET_DEVICEINFO)

    suspend fun getAllCurrentSettings(): Result<JSONObject> = command(MsgId.GET_ALL_CURRENT_SETTINGS)

    suspend fun getCurrentModeSettings(): Result<JSONObject> = command(MsgId.GET_CURRENT_MODE_SETTINGS)

    /** Value(s) the camera currently accepts for one setting — used to build option pickers. */
    suspend fun getSettingOptions(type: String): Result<JSONObject> =
        command(MsgId.GET_SINGLE_SETTING_OPTIONS, type = type)

    suspend fun getSetting(type: String): Result<JSONObject> =
        command(MsgId.GET_SETTING, type = type)

    suspend fun setSetting(type: String, value: String): Result<JSONObject> =
        command(MsgId.SET_SETTING, type = type, param = value)

    suspend fun setCameraMode(mode: String): Result<JSONObject> = setSetting("camera_mode", mode)

    suspend fun startViewfinder(): Result<JSONObject> =
        command(MsgId.BOSS_RESETVF, param = "none_force")

    suspend fun stopViewfinder(): Result<JSONObject> = command(MsgId.STOP_VF)

    private suspend fun command(
        msgId: Int,
        type: String? = null,
        param: String? = null,
    ): Result<JSONObject> = callLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                check(isConnected) { "Not connected to camera control socket" }
                throttle()
                sendRaw(JSONObject().apply {
                    put("msg_id", msgId)
                    type?.let { put("type", it) }
                    param?.let { put("param", it) }
                    put("token", token)
                })
            }
        }
    }

    private fun throttle() {
        val elapsed = System.currentTimeMillis() - lastSendAtMs
        if (elapsed in 0 until MIN_COMMAND_GAP_MS) {
            Thread.sleep(MIN_COMMAND_GAP_MS - elapsed)
        }
    }

    /** Sends one JSON object and reads back exactly one JSON object reply. */
    private fun sendRaw(payload: JSONObject): JSONObject {
        val out = writer ?: error("Socket not open")
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        out.write(body)
        out.flush()
        lastSendAtMs = System.currentTimeMillis()

        val rd = reader ?: error("Socket not open")
        return try {
            readOneJsonObject(rd)
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("Camera did not reply to msg_id=${payload.optInt("msg_id")}", e)
        }
    }

    /** Reads a single balanced-brace JSON object off the stream (no delimiter is sent by the camera). */
    private fun readOneJsonObject(rd: BufferedReader): JSONObject {
        val sb = StringBuilder()
        var depth = 0
        var started = false
        while (true) {
            val c = rd.read()
            if (c == -1) break
            val ch = c.toChar()
            if (!started) {
                if (ch != '{') continue
                started = true
            }
            sb.append(ch)
            if (ch == '{') depth++
            if (ch == '}') {
                depth--
                if (depth == 0) break
            }
        }
        check(sb.isNotEmpty()) { "Empty response from camera" }
        return JSONObject(sb.toString())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4000
        const val READ_TIMEOUT_MS = 5000
        const val MIN_COMMAND_GAP_MS = 600L
    }
}

/** Known msg_id values for the Ambarella A12 control socket. */
object MsgId {
    const val GET_SETTING = 1
    const val SET_SETTING = 2
    const val GET_ALL_CURRENT_SETTINGS = 3
    const val GET_SPACE = 5
    const val NOTIFICATION = 7
    const val GET_SINGLE_SETTING_OPTIONS = 9
    const val GET_DEVICEINFO = 11
    const val CAMERA_OFF = 12
    const val GET_BATTERY_LEVEL = 13
    const val START_SESSION = 257
    const val STOP_SESSION = 258
    const val BOSS_RESETVF = 259
    const val STOP_VF = 260
    const val RECORD_START = 513
    const val RECORD_STOP = 514
    const val GET_RECORD_TIME = 515
    const val TAKE_PHOTO = 769
    const val GET_CURRENT_MODE_SETTINGS = 2053
    const val SET_WIFI = 2055
}

object CameraEndpoints {
    const val HOST = "192.168.42.1"
    const val CONTROL_PORT = 7878
    const val HTTP_PORT = 80
    const val RTSP_PORT = 554
    const val RTSP_URL = "rtsp://$HOST/live"
}
