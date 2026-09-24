package com.mijia4k.app.modules.led.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

/**
 * The Bluetooth LE link to one LED controller. Commands are queued by "kind"
 * (colour, brightness, …) so a dragged slider only ever sends the latest value
 * instead of flooding the controller.
 *
 * Callers must hold the Bluetooth permissions before using any of this.
 */
@SuppressLint("MissingPermission")
class LedConnection(private val context: Context) {
    sealed interface State {
        data object Idle : State
        data object Scanning : State
        data object Connecting : State
        data class Ready(val name: String, val address: String) : State
        data class Failed(val message: String) : State
    }

    data class Found(val name: String, val address: String, val rssi: Int)

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()

    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var deviceAddress = ""
    private var pending: CompletableDeferred<Result<Unit>>? = null

    private val queue = LinkedHashMap<Int, ByteArray>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val writeAck = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in wake) {
                while (true) {
                    val next = synchronized(queue) {
                        val key = queue.keys.firstOrNull() ?: return@synchronized null
                        queue.remove(key)
                    } ?: break
                    runCatching { write(next) }
                    delay(WRITE_GAP_MS)
                }
            }
        }
    }

    // ---- scanning ---------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            if (name.isBlank()) return
            val entry = Found(name.trim(), result.device.address, result.rssi)
            _found.value = (_found.value.filter { it.address != entry.address } + entry)
                .sortedWith(compareByDescending<Found> { looksLikeLed(it.name) }.thenByDescending { it.rssi })
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = State.Failed("Scan failed ($errorCode)")
        }
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _state.value = State.Failed("Bluetooth is off")
            return
        }
        _found.value = emptyList()
        _state.value = State.Scanning
        scanner.startScan(scanCallback)
    }

    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (_state.value is State.Scanning) _state.value = State.Idle
    }

    private fun looksLikeLed(name: String): Boolean {
        val n = name.uppercase()
        return listOf("ELK", "BLEDOM", "LED", "MELK", "TRIONES", "LOTUS").any { n.contains(it) }
    }

    // ---- connecting -------------------------------------------------------

    suspend fun connect(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            ?: return@withContext Result.failure(IOException("Bluetooth is not available"))
        stopScan()
        closeGatt()
        _state.value = State.Connecting
        val done = CompletableDeferred<Result<Unit>>()
        pending = done
        deviceAddress = address
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        val result = withTimeoutOrNull(15_000) { done.await() }
            ?: Result.failure(IOException("The light did not answer"))
        if (result.isFailure) {
            closeGatt()
            _state.value = State.Failed(result.exceptionOrNull()?.message ?: "Could not connect")
        }
        result
    }

    fun disconnect() {
        closeGatt()
        _state.value = State.Idle
    }

    private fun closeGatt() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        writeChar = null
        synchronized(queue) { queue.clear() }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val wasReady = _state.value is State.Ready
                pending?.complete(Result.failure(IOException("Disconnected (status $status)")))
                pending = null
                if (wasReady) {
                    closeGatt()
                    _state.value = State.Idle
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val c = g.getService(SERVICE)?.getCharacteristic(WRITE)
                ?: g.services.flatMap { it.characteristics }.firstOrNull {
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
                        it.uuid.toString().startsWith("0000fff")
                }
            if (c == null) {
                pending?.complete(Result.failure(IOException("This does not look like a supported LED controller")))
                pending = null
                return
            }
            writeChar = c
            _state.value = State.Ready(g.device.name ?: "LED strip", deviceAddress)
            pending?.complete(Result.success(Unit))
            pending = null
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeAck.trySend(Unit)
        }
    }

    // ---- writing ----------------------------------------------------------

    private suspend fun write(bytes: ByteArray) {
        val g = gatt ?: return
        val c = writeChar ?: return
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        repeat(20) {
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = type
                    c.value = bytes
                    g.writeCharacteristic(c)
                }
            }
            if (ok) return
            delay(20)
        }
    }

    /** Queues a command; a newer one with the same [kind] replaces one not yet sent. */
    fun send(kind: Int, bytes: ByteArray) {
        if (_state.value !is State.Ready) return
        synchronized(queue) { queue[kind] = bytes }
        wake.trySend(Unit)
    }

    companion object {
        private const val WRITE_GAP_MS = 35L
        private val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val WRITE: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

        const val KIND_POWER = 0
        const val KIND_COLOR = 1
        const val KIND_BRIGHTNESS = 2
        const val KIND_EFFECT = 3
        const val KIND_SPEED = 4
    }
}
