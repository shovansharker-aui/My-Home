package com.mijia4k.app.modules.printer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import com.mijia4k.app.modules.printer.protocol.CatProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import kotlin.math.min

/**
 * The Bluetooth LE link to one thermal printer. Characteristics are picked the
 * way the stock app does it (the first write-only one and the first that
 * notifies, preferring the usual 0xAE30 service), the largest MTU is asked for,
 * and data goes out in MTU-sized writes about 40 ms apart, pausing whenever the
 * printer sends its "buffer full" flow-control notice.
 *
 * Callers must hold the Bluetooth permissions before using any of this.
 */
@SuppressLint("MissingPermission")
class PrinterConnection(private val context: Context) {
    sealed interface State {
        data object Idle : State
        data object Scanning : State
        data object Connecting : State
        data class Ready(val name: String, val address: String) : State
        data class Failed(val message: String) : State
    }

    data class Found(val name: String, val address: String, val rssi: Int)

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()

    private val _status = MutableStateFlow<CatProtocol.Status?>(null)
    val status: StateFlow<CatProtocol.Status?> = _status.asStateFlow()

    private val _battery = MutableStateFlow<Int?>(null)

    /** Charge in percent, when the printer reports one. */
    val battery: StateFlow<Int?> = _battery.asStateFlow()

    private val _progress = MutableStateFlow<Float?>(null)

    /** 0..1 while a job is being sent, null otherwise. */
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var deviceName = ""
    private var deviceAddress = ""

    @Volatile private var payload = 20
    @Volatile private var paused = false
    private val writeAck = Channel<Unit>(Channel.CONFLATED)
    private val writeMutex = Mutex()
    private var pending: CompletableDeferred<Result<Unit>>? = null
    private var rx = ByteArray(0)

    // ---- scanning ---------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            if (name.isBlank()) return
            val entry = Found(name.trim(), result.device.address, result.rssi)
            _found.value = (_found.value.filter { it.address != entry.address } + entry)
                .sortedWith(compareByDescending<Found> { looksLikePrinter(it.name) }.thenByDescending { it.rssi })
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

    private fun looksLikePrinter(name: String): Boolean {
        val n = name.uppercase()
        return listOf("PRINTER", "MX0", "MX1", "GB0", "GT0", "MINI", "CAT").any { n.contains(it) }
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
        val result = withTimeoutOrNull(20_000) { done.await() }
            ?: Result.failure(IOException("The printer did not answer"))
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
        notifyChar = null
        paused = false
        rx = ByteArray(0)
        _status.value = null
        _battery.value = null
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                deviceName = g.device.name ?: "Printer"
                if (!g.requestMtu(247)) g.discoverServices()
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

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            payload = (mtu - 3).coerceIn(20, 240)
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (!pickCharacteristics(g)) {
                pending?.complete(Result.failure(IOException("This device does not look like a supported printer")))
                pending = null
                return
            }
            val notify = notifyChar!!
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD)
            if (cccd == null) {
                becomeReady()
                return
            }
            val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = value
                    g.writeDescriptor(cccd)
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            becomeReady()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeAck.trySend(Unit)
        }

        @Deprecated("Used below API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            onNotification(c.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onNotification(value)
        }
    }

    private fun pickCharacteristics(g: BluetoothGatt): Boolean {
        val services = g.services.sortedByDescending { it.uuid == SERVICE_AE30 }
        for (service in services) {
            for (c in service.characteristics) {
                val p = c.properties
                val writable = p and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                if (writeChar == null && writable && p and BluetoothGattCharacteristic.PROPERTY_READ == 0) writeChar = c
                if (notifyChar == null &&
                    p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                ) {
                    notifyChar = c
                }
            }
            if (writeChar != null && notifyChar != null) break
        }
        Log.d(TAG, "write=${writeChar?.uuid} notify=${notifyChar?.uuid} payload=$payload")
        return writeChar != null && notifyChar != null
    }

    private fun becomeReady() {
        _state.value = State.Ready(deviceName, deviceAddress)
        scope.launch {
            delay(600)
            runCatching { writeMutex.withLock { writeChunk(CatProtocol.infoRequest()) } }
        }
        pending?.complete(Result.success(Unit))
        pending = null
    }

    // ---- notifications ----------------------------------------------------

    private fun onNotification(value: ByteArray) {
        rx += value
        while (true) {
            val start = (0 until rx.size - 1).firstOrNull { rx[it] == 0x51.toByte() && rx[it + 1] == 0x78.toByte() }
            if (start == null) {
                rx = ByteArray(0)
                return
            }
            if (rx.size - start < 8) return
            val len = (rx[start + 4].toInt() and 0xFF) or ((rx[start + 5].toInt() and 0xFF) shl 8)
            val total = 8 + len
            if (rx.size - start < total) return
            val cmd = rx[start + 2].toInt() and 0xFF
            val data = rx.copyOfRange(start + 6, start + 6 + len)
            rx = rx.copyOfRange(start + total, rx.size)
            handleFrame(cmd, data)
        }
    }

    private fun handleFrame(cmd: Int, data: ByteArray) {
        Log.d(TAG, "rx cmd=%02x data=%s".format(cmd, data.joinToString("") { "%02x".format(it) }))
        when (cmd) {
            CatProtocol.CMD_FLOW -> paused = data.isNotEmpty() && data[0].toInt() != 0
            CatProtocol.CMD_STATUS -> if (data.isNotEmpty()) _status.value = CatProtocol.Status(data[0].toInt() and 0xFF)
            CatProtocol.CMD_INFO -> if (data.isNotEmpty()) (data[0].toInt() and 0xFF).takeIf { it in 0..100 }?.let { _battery.value = it }
        }
    }

    // ---- writing ----------------------------------------------------------

    private suspend fun writeChunk(chunk: ByteArray) {
        val g = gatt ?: throw IOException("Not connected")
        val c = writeChar ?: throw IOException("Not connected")
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        writeAck.tryReceive()
        var accepted = false
        repeat(60) {
            if (!accepted) {
                accepted = if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(c, chunk, type) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        c.writeType = type
                        c.value = chunk
                        g.writeCharacteristic(c)
                    }
                }
                if (!accepted) delay(25)
            }
        }
        if (!accepted) throw IOException("The printer stopped accepting data")
        withTimeout(4_000) { writeAck.receive() }
    }

    /** Asks the printer for its charge; the answer lands in [battery]. */
    suspend fun refreshBattery() {
        runCatching { writeMutex.withLock { writeChunk(CatProtocol.infoRequest()) } }
    }

    /** Asks the printer for its status; the answer lands in [status]. */
    suspend fun refreshStatus() {
        runCatching { writeMutex.withLock { writeChunk(CatProtocol.statusRequest()) } }
    }

    /** Sends a whole job and waits for the printer to finish it. */
    suspend fun print(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value !is State.Ready) return@withContext Result.failure(IOException("Printer is not connected"))
        try {
            _status.value = null
            _progress.value = 0f
            writeMutex.withLock {
                var offset = 0
                while (offset < bytes.size) {
                    while (paused) delay(30)
                    val end = min(offset + payload, bytes.size)
                    writeChunk(bytes.copyOfRange(offset, end))
                    offset = end
                    _progress.value = offset.toFloat() / bytes.size
                    delay(WRITE_GAP_MS)
                }
            }
            awaitFinished()
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _progress.value = null
        }
    }

    /** Polls until the printer reports idle twice in a row, or fails on paper-out / overheating. */
    private suspend fun awaitFinished(): Result<Unit> {
        var idleCount = 0
        repeat(120) {
            refreshStatus()
            delay(500)
            val s = _status.value
            if (s != null) {
                if (s.paperOut) return Result.failure(IOException("Out of paper"))
                if (s.overheated) return Result.failure(IOException("Printer is too hot, let it cool down"))
                idleCount = if (!s.busy) idleCount + 1 else 0
                if (idleCount >= 2) return Result.success(Unit)
            }
        }
        return Result.failure(IOException("The printer did not report that it finished"))
    }

    companion object {
        private const val TAG = "PrinterBle"
        private const val WRITE_GAP_MS = 40L
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val SERVICE_AE30: UUID = UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb")
    }
}
