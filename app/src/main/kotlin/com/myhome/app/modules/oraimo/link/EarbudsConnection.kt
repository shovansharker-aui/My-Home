package com.myhome.app.modules.oraimo.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.myhome.app.modules.oraimo.protocol.EarbudsProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The link to one pair of oraimo earbuds: an RFCOMM socket on the earbuds'
 * control service, a reader that turns incoming messages into [info], and a few
 * commands. The earbuds must already be paired with the phone.
 *
 * Callers must hold the Bluetooth permissions before using any of this.
 */
@SuppressLint("MissingPermission")
class EarbudsConnection(private val context: Context) {
    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data class Ready(val name: String, val address: String) : State
        data class Failed(val message: String) : State
    }

    data class Info(
        val name: String? = null,
        val left: Int? = null,
        val right: Int? = null,
        val case: Int? = null,
        val eqPreset: Int? = null,
        val gameMode: Boolean? = null,
        val eqGains: List<Int>? = null,
    )

    data class Paired(val name: String, val address: String)

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info.asStateFlow()

    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private var socket: BluetoothSocket? = null
    private var reader: Job? = null
    private var seq = 0
    private val writeLock = Any()

    /** Everything already paired with the phone, earbuds first. */
    fun paired(): List<Paired> =
        runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .map { Paired(it.name ?: it.address, it.address) }
            .sortedByDescending { it.name.contains("oraimo", ignoreCase = true) }

    suspend fun connect(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            ?: return@withContext Result.failure(IOException("Bluetooth is not available"))
        close()
        _state.value = State.Connecting
        _info.value = Info()
        try {
            adapter?.cancelDiscovery()
            val s = device.createRfcommSocketToServiceRecord(EarbudsProtocol.SERVICE_UUID)
            s.connect()
            socket = s
            seq = 0
            startReader(s)
            _state.value = State.Ready(device.name ?: "Earbuds", address)
            send(EarbudsProtocol.GROUP_GET, EarbudsProtocol.CMD_REQUEST, EarbudsProtocol.helloPayload())
            delay(120)
            refresh()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "connect failed", e)
            close()
            val msg = "Waiting for your earbuds — open the case, or put one in your ear"
            _state.value = State.Failed(msg)
            Result.failure(IOException(msg))
        }
    }

    fun disconnect() {
        close()
        _info.value = Info()
        _state.value = State.Idle
    }

    private fun close() {
        reader?.cancel()
        reader = null
        runCatching { socket?.close() }
        socket = null
    }

    private fun startReader(s: BluetoothSocket) {
        reader = scope.launch {
            var pending = ByteArray(0)
            val buf = ByteArray(1024)
            try {
                while (isActive) {
                    val n = s.inputStream.read(buf)
                    if (n < 0) break
                    val (messages, rest) = EarbudsProtocol.parse(pending + buf.copyOf(n))
                    pending = rest
                    messages.forEach(::handle)
                }
            } catch (_: IOException) {
                // fall through to the disconnect handling
            }
            if (socket === s) {
                close()
                _state.value = State.Idle
            }
        }
    }

    private fun handle(m: EarbudsProtocol.Message) {
        Log.d(TAG, "rx grp=%02x cmd=%02x %s".format(m.group, m.cmd, m.payload.joinToString("") { "%02x".format(it) }))
        val carriesItems = (m.group == EarbudsProtocol.GROUP_GET && m.cmd == EarbudsProtocol.CMD_REPLY) ||
            (m.group == EarbudsProtocol.GROUP_NOTIFY && m.cmd == EarbudsProtocol.CMD_NOTIFY)
        if (!carriesItems) return
        val items = EarbudsProtocol.items(m.payload)
        var cur = _info.value
        items[EarbudsProtocol.TAG_BATTERY]?.takeIf { it.size >= 3 }?.let { v ->
            fun pct(i: Int) = (v[i].toInt() and 0x7F).takeIf { it in 1..100 }
            cur = cur.copy(left = pct(0), right = pct(1), case = pct(2))
        }
        items[EarbudsProtocol.TAG_NAME]?.let { cur = cur.copy(name = String(it, Charsets.UTF_8)) }
        items[EarbudsProtocol.TAG_EQ]?.takeIf { it.size >= 9 }?.let { v ->
            cur = cur.copy(eqPreset = v[1].toInt() and 0xFF, eqGains = (2..8).map { v[it].toInt() })
        }
        items[EarbudsProtocol.TAG_GAME_MODE]?.takeIf { it.isNotEmpty() }?.let { cur = cur.copy(gameMode = it[0].toInt() != 0) }
        _info.value = cur
    }

    private fun send(group: Int, cmd: Int, payload: ByteArray) {
        val s = socket ?: return
        runCatching {
            synchronized(writeLock) {
                s.outputStream.write(EarbudsProtocol.frame(seq, group, cmd, payload))
                s.outputStream.flush()
                seq = (seq + 1) and 0x0F
            }
        }
    }

    fun refresh() = send(EarbudsProtocol.GROUP_GET, EarbudsProtocol.CMD_REQUEST, EarbudsProtocol.infoPayload())

    fun setEq(preset: Int) {
        _info.value = _info.value.copy(eqPreset = preset)
        send(EarbudsProtocol.GROUP_SET, EarbudsProtocol.CMD_REQUEST, EarbudsProtocol.eqPayload(preset))
    }

    fun setCustomEq(bands: List<Int>) {
        _info.value = _info.value.copy(eqPreset = EarbudsProtocol.CUSTOM_PRESET, eqGains = bands)
        send(EarbudsProtocol.GROUP_SET, EarbudsProtocol.CMD_REQUEST, EarbudsProtocol.customEqPayload(bands))
    }

    fun setGameMode(on: Boolean) {
        _info.value = _info.value.copy(gameMode = on)
        send(EarbudsProtocol.GROUP_GAME, EarbudsProtocol.CMD_REQUEST, EarbudsProtocol.gamePayload(on))
    }

    companion object {
        private const val TAG = "EarbudsLink"
    }
}
