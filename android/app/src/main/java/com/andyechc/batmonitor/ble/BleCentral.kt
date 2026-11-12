package com.andyechc.batmonitor.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.protocol.BmsBasicInfo
import com.andyechc.batmonitor.protocol.BmsCodec
import com.andyechc.batmonitor.protocol.BmsCommand
import com.andyechc.batmonitor.protocol.BmsFrame
import com.andyechc.batmonitor.protocol.BmsFrameParser
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** CoreBluetooth-equivalent central: scan, connect, command, notify parsing. */
@SuppressLint("MissingPermission")
object BleCentral {
    private val serviceUuid: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    private val notifyUuid: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    private val writeUuid: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Device UUID (derived from MAC) -> MAC address, filled by scans. */
    private val knownAddresses = ConcurrentHashMap<UUID, String>()
    private val links = ConcurrentHashMap<UUID, GattLink>()

    private var scanCallback: ScanCallback? = null
    private var scanGeneration = 0
    private val _discoveries = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 64)
    val discoveries = _discoveries.asSharedFlow()

    /** Last platform scan failure, if any. Cleared on every new scan. */
    private val _scanError = MutableStateFlow<BleError?>(null)
    val scanError: StateFlow<BleError?> = _scanError.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun adapter(): BluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    fun string(key: BleError): String = when (key) {
        BleError.BluetoothOff -> appContext.getString(R.string.ble_bluetoothoff)
        BleError.Unauthorized -> appContext.getString(R.string.ble_unauthorized)
        BleError.NoConnect -> appContext.getString(R.string.ble_noconnect)
        BleError.NoResponse -> appContext.getString(R.string.ble_noresponse)
        BleError.NotConnected -> appContext.getString(R.string.ble_notconnected)
        BleError.WriteFailed -> appContext.getString(R.string.ble_writefailed)
        BleError.InvalidData -> appContext.getString(R.string.ble_invaliddata)
        BleError.ScanFailed -> appContext.getString(R.string.ble_scanfailed)
    }

    /** Wait until the adapter leaves unknown/resetting-like transient states. */
    suspend fun waitUntilSettled() {
        repeat(25) {
            val state = adapter().adapter?.state
            when (state) {
                BluetoothAdapter.STATE_ON -> return
                BluetoothAdapter.STATE_TURNING_ON -> Unit
                else -> throw BleError.BluetoothOff
            }
            delay(200)
        }
        val state = adapter().adapter?.state
        if (state != BluetoothAdapter.STATE_ON) throw BleError.BluetoothOff
    }

    /** True when the runtime permissions needed to see devices (and their names) are granted. */
    fun canScan(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScan() {
        val btAdapter = adapter().adapter ?: throw BleError.BluetoothOff
        if (btAdapter.state != BluetoothAdapter.STATE_ON) throw BleError.BluetoothOff
        if (!canScan()) throw BleError.Unauthorized
        val scanner = btAdapter.bluetoothLeScanner ?: throw BleError.BluetoothOff
        stopScan()
        _scanError.value = null
        val generation = ++scanGeneration
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (generation != scanGeneration) return
                val device = result.device ?: return
                val address = try {
                    device.address
                } catch (e: SecurityException) {
                    return
                }
                if (address.isEmpty()) return
                val advertisedName = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                val name = advertisedName ?: try {
                    device.name?.takeIf { it.isNotBlank() }
                } catch (e: SecurityException) {
                    null
                } ?: address
                val id = UUID.nameUUIDFromBytes(address.toByteArray(Charsets.UTF_8))
                knownAddresses[id] = address
                _discoveries.tryEmit(ScannedDevice(id = id, name = name, rssi = result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                if (generation != scanGeneration) return
                _scanError.value = BleError.ScanFailed
            }
        }
        scanCallback = cb
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        try {
            // No ScanFilter on purpose: XiaoXiang-compatible BMS units do not advertise the
            // FF00 service UUID, so a service filter hid every battery. The UI lists all
            // nearby BLE devices instead.
            scanner.startScan(emptyList(), settings, cb)
        } catch (e: SecurityException) {
            scanCallback = null
            throw BleError.Unauthorized
        } catch (e: Exception) {
            scanCallback = null
            _scanError.value = BleError.ScanFailed
            throw BleError.ScanFailed
        }
    }

    fun stopScan() {
        scanCallback?.let { cb ->
            try {
                adapter().adapter?.bluetoothLeScanner?.stopScan(cb)
            } catch (_: Exception) {
            }
        }
        scanCallback = null
    }

    suspend fun connect(id: UUID) {
        waitUntilSettled()
        disconnect(id)
        val address = knownAddresses[id] ?: throw BleError.NoConnect
        val device = adapter().adapter?.getRemoteDevice(address) ?: throw BleError.NoConnect
        val link = GattLink(device)
        links[id] = link
        try {
            withTimeout(20_000) { link.open() }
            // First-response workaround: the first notification after subscribing
            // is lost, so settle before the first command.
            delay(1_000)
        } catch (e: Exception) {
            teardown(id)
            throw (e as? BleError) ?: BleError.NoConnect
        }
    }

    fun disconnect(id: UUID) {
        teardown(id)
    }

    private fun teardown(id: UUID) {
        links.remove(id)?.close()
    }

    suspend fun readBattery(id: UUID): BatteryReading {
        val basicFrame = command(id, BmsCodec.readCommand(BmsCommand.BASIC_INFO), BmsCommand.BASIC_INFO)
        if (basicFrame.status != 0) throw BleError.InvalidData
        val basic = BmsCodec.parseBasicInfo(basicFrame.data) ?: throw BleError.InvalidData
        val cellFrame = command(id, BmsCodec.readCommand(BmsCommand.CELL_VOLTAGES), BmsCommand.CELL_VOLTAGES)
        if (cellFrame.status != 0) throw BleError.InvalidData
        val cells = BmsCodec.parseCellVoltages(cellFrame.data)
        return BatteryReading(basic = basic, cells = cells, receivedAt = System.currentTimeMillis())
    }

    suspend fun command(id: UUID, payload: List<Int>, cmd: Int, timeoutMs: Long = 3_000): BmsFrame {
        val link = links[id] ?: throw BleError.NotConnected
        return link.command(payload, cmd, timeoutMs)
    }

    private class GattLink(private val device: BluetoothDevice) {
        private var gatt: BluetoothGatt? = null
        private var writeChar: BluetoothGattCharacteristic? = null
        private val parser = BmsFrameParser()
        private val writeMutex = Mutex()
        private val waiters = ConcurrentHashMap<Int, CompletableDeferred<BmsFrame>>()
        private var nameWaiter: CompletableDeferred<BmsFrame>? = null
        private var openResult: CompletableDeferred<Boolean>? = null

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        requestMtu(gatt)
                        gatt.discoverServices()
                    } catch (_: Exception) {
                        openResult?.complete(false)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    failAll(BleError.NoConnect)
                    openResult?.let { if (!it.isCompleted) it.complete(false) }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    openResult?.complete(false)
                    return
                }
                val service = gatt.getService(serviceUuid)
                val notify = service?.getCharacteristic(notifyUuid)
                writeChar = service?.getCharacteristic(writeUuid)
                if (notify == null || writeChar == null) {
                    openResult?.complete(false)
                    return
                }
                try {
                    gatt.setCharacteristicNotification(notify, true)
                    val cccd = notify.getDescriptor(cccdUuid)
                    if (cccd != null) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(cccd)
                        }
                    } else {
                        openResult?.complete(true)
                    }
                } catch (_: Exception) {
                    openResult?.complete(false)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (descriptor.uuid == cccdUuid) {
                    openResult?.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                route(characteristic.value?.toList())
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                route(value.toList())
            }
        }

        private fun requestMtu(gatt: BluetoothGatt) {
            try {
                if (Build.VERSION.SDK_INT >= 21) gatt.requestMtu(247)
            } catch (_: Exception) {
            }
        }

        private fun route(raw: List<Byte>?) {
            if (raw.isNullOrEmpty()) return
            val bytes = raw.map { it.toInt() and 0xFF }
            if (bytes.size >= 4 && bytes[0] == 0xFF && bytes[1] == 0xAA) {
                val ack = BmsCodec.parseNameResponse(bytes)
                if (ack != null) {
                    nameWaiter?.let { if (!it.isCompleted) it.complete(ack) }
                }
                return
            }
            for (frame in parser.push(bytes)) {
                waiters[frame.cmd]?.let { if (!it.isCompleted) it.complete(frame) }
            }
        }

        private fun failAll(error: BleError) {
            for ((_, w) in waiters) if (!w.isCompleted) w.completeExceptionally(error)
            waiters.clear()
            nameWaiter?.let { if (!it.isCompleted) it.completeExceptionally(error) }
            nameWaiter = null
        }

        suspend fun open() {
            val result = CompletableDeferred<Boolean>()
            openResult = result
            gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw BleError.NoConnect
            if (result.await() != true) throw BleError.NoConnect
        }

        suspend fun command(payload: List<Int>, cmd: Int, timeoutMs: Long): BmsFrame {
            val char = writeChar ?: throw BleError.NotConnected
            val g = gatt ?: throw BleError.NotConnected
            val waiter = CompletableDeferred<BmsFrame>()
            waiters[cmd]?.let { if (!it.isCompleted) it.completeExceptionally(BleError.NoResponse) }
            val isName = payload.size >= 2 && payload[0] == 0xFF && payload[1] == 0xAA
            return try {
                if (isName) {
                    nameWaiter?.let { if (!it.isCompleted) it.completeExceptionally(BleError.NoResponse) }
                    nameWaiter = waiter
                } else {
                    waiters[cmd] = waiter
                }
                writeMutex.withLock {
                    val bytes = payload.map { it.toByte() }.toByteArray()
                    val ok = if (Build.VERSION.SDK_INT >= 33) {
                        val noResponse = (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                        val type = if (noResponse) {
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        } else {
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        }
                        g.writeCharacteristic(char, bytes, type) == 0
                    } else {
                        @Suppress("DEPRECATION")
                        char.writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        } else {
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        }
                        @Suppress("DEPRECATION")
                        char.value = bytes
                        @Suppress("DEPRECATION")
                        g.writeCharacteristic(char)
                    }
                    if (!ok) throw BleError.WriteFailed
                }
                withTimeout(timeoutMs) { waiter.await() }
            } finally {
                waiters.remove(cmd, waiter)
                if (nameWaiter === waiter) nameWaiter = null
            }
        }

        fun close() {
            failAll(BleError.NoConnect)
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (_: Exception) {
            }
            gatt = null
            writeChar = null
        }
    }
}
