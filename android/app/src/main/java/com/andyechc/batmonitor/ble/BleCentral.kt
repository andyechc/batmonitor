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
import android.util.Log
import androidx.core.content.ContextCompat
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.protocol.BmsBasicInfo
import com.andyechc.batmonitor.protocol.BmsCodec
import com.andyechc.batmonitor.protocol.BmsCommand
import com.andyechc.batmonitor.protocol.BmsFrame
import com.andyechc.batmonitor.protocol.BmsFrameParser
import com.andyechc.batmonitor.protocol.ant.AntCodec
import com.andyechc.batmonitor.protocol.ant.AntFrame
import com.andyechc.batmonitor.protocol.ant.AntFrameParser
import com.andyechc.batmonitor.protocol.ant.AntRealtime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.withTimeoutOrNull

/** CoreBluetooth-equivalent central: scan, connect, command, notify parsing. */
@SuppressLint("MissingPermission")
object BleCentral {
    /** logcat tag for every BLE decision; `adb logcat -s BatMonitor` shows the whole story. */
    const val TAG = "BatMonitor"

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
        BleError.Unsupported -> appContext.getString(R.string.device_unsupported)
        BleError.LegacyAnt -> appContext.getString(R.string.device_legacy_ant)
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
        val loggedAddresses = HashSet<String>()
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
                val record = result.scanRecord
                // One line per address (this phone drops debug-level logs, so it must be I):
                // it shows what each device advertises, which is how the BMS filter is tuned.
                if (loggedAddresses.add(address)) {
                    Log.i(
                        TAG,
                        "scan $address name=$name rssi=${result.rssi} " +
                            "services=${record?.serviceUuids} " +
                            "serviceData=${record?.serviceData?.keys} " +
                            "manufacturer=${record?.manufacturerSpecificData?.let { m ->
                                (0 until m.size()).map { index -> "%04X".format(m.keyAt(index)) }
                            }} " +
                            "tx=${record?.txPowerLevel}",
                    )
                }
                val serviceUuids = record?.serviceUuids?.map { it.uuid } ?: emptyList()
                _discoveries.tryEmit(
                    ScannedDevice(
                        id = id,
                        name = name,
                        rssi = result.rssi,
                        address = address,
                        serviceUuids = serviceUuids,
                        type = BmsType.detect(name, serviceUuids),
                    ),
                )
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

    /** Restores a saved MAC so a fresh process can connect without scanning first. */
    fun rememberAddress(id: UUID, address: String) {
        if (address.isNotBlank()) knownAddresses[id] = address
    }

    /**
     * Android can only open a GATT link by MAC address, so a saved battery whose address is
     * unknown has to be found again. iOS does the same in `resolvePeripheral` by rescanning.
     */
    private suspend fun resolveAddress(id: UUID): String {
        knownAddresses[id]?.let { return it }
        Log.i(TAG, "no address for $id yet, scanning to resolve it")
        startScan()
        try {
            withTimeoutOrNull(6_000) {
                while (knownAddresses[id] == null) delay(200)
            }
        } finally {
            stopScan()
        }
        return knownAddresses[id] ?: throw BleError.NoConnect
    }

    suspend fun connect(id: UUID, type: BmsType = BmsType.XIAOXIANG) {
        waitUntilSettled()
        disconnect(id)
        val address = resolveAddress(id)
        val device = adapter().adapter?.getRemoteDevice(address) ?: throw BleError.NoConnect
        // Old-protocol ANT packs answer with `AA 55` frames we cannot decode. The vendor
        // app asks the user to confirm; we cannot, so the battery is reported instead.
        if (type == BmsType.ANT && isLegacyAntDevice(device)) throw BleError.LegacyAnt
        Log.i(TAG, "connect start $address type=$type")

        var lastError: BleError = BleError.NoConnect
        // Android's known GATT_ERROR (133) is only cured by throwing the client away and
        // starting over with a brand-new BluetoothGatt object.
        for (attempt in 1..2) {
            // The first attempt follows iOS (20 s); the retry gets less so the screen settles.
            val budget = if (attempt == 1) 20_000L else 12_000L
            val link = GattLink(id, device, type)
            links[id] = link
            try {
                withTimeout(budget) { link.open() }
                Log.i(TAG, "connect ok on attempt $attempt")
                // First-response workaround: the first notification after subscribing is
                // lost, so settle before the first command.
                delay(1_500)
                return
            } catch (e: TimeoutCancellationException) {
                lastError = BleError.NoConnect
                Log.w(TAG, "connect timeout on attempt $attempt")
            } catch (e: kotlinx.coroutines.CancellationException) {
                teardown(id)
                throw e
            } catch (e: Exception) {
                lastError = (e as? BleError) ?: BleError.NoConnect
                Log.w(TAG, "connect failed on attempt $attempt: $e")
            }
            teardown(id)
            delay(600)
        }
        throw lastError
    }

    fun disconnect(id: UUID) {
        teardown(id)
    }

    private fun teardown(id: UUID) {
        links.remove(id)?.close()
    }

    suspend fun readBattery(id: UUID, type: BmsType = BmsType.XIAOXIANG): BatteryReading {
        val link = links[id] ?: throw BleError.NotConnected
        return when (type) {
            BmsType.XIAOXIANG -> {
                val basicFrame = command(id, BmsCodec.readCommand(BmsCommand.BASIC_INFO), BmsCommand.BASIC_INFO)
                if (basicFrame.status != 0) throw BleError.InvalidData
                val basic = BmsCodec.parseBasicInfo(basicFrame.data) ?: throw BleError.InvalidData
                val cellFrame = command(id, BmsCodec.readCommand(BmsCommand.CELL_VOLTAGES), BmsCommand.CELL_VOLTAGES)
                if (cellFrame.status != 0) throw BleError.InvalidData
                val cells = BmsCodec.parseCellVoltages(cellFrame.data)
                BatteryReading(basic = basic, cells = cells, receivedAt = System.currentTimeMillis())
            }
            BmsType.ANT -> {
                // The advertised name is only guaranteed once connected, so check again.
                if (link.isLegacyAnt) throw BleError.LegacyAnt
                // One request returns the whole snapshot (cells, SOC, current, temperatures).
                val frame = link.commandAnt(
                    AntCodec.realtimeRequest(),
                    AntCodec.REALTIME_RESPONSE,
                    timeoutMs = 3_000,
                )
                val data = AntCodec.decodeRealtime(frame.bytes) ?: throw BleError.InvalidData
                antReading(data)
            }
        }
    }

    /** Reads the device name safely; a missing permission just means "unknown". */
    private fun isLegacyAntDevice(device: BluetoothDevice): Boolean = try {
        BmsType.isLegacyAnt(device.name)
    } catch (e: SecurityException) {
        false
    }

    /**
     * Maps an ANT snapshot onto the app's shared reading model. Fields the ANT
     * protocol does not expose here (cycles, protections, FET bits, balancing
     * cells) are left empty/zero; the UI hides or disables those sections for ANT.
     */
    private fun antReading(data: AntRealtime): BatteryReading {        val basic = BmsBasicInfo(
            totalVoltage = data.totalVoltage,
            // ANT reports current with the opposite sign (positive while discharging);
            // the rest of the app treats positive as charging.
            current = -data.current,
            remainingCapacity = data.remainingCapacity,
            nominalCapacity = data.nominalCapacity,
            cycles = 0,
            productionDate = "",
            soc = data.soc,
            firmwareVersion = "",
            chargeOn = data.chargeMosOn,
            dischargeOn = data.dischargeMosOn,
            cellCount = data.cells.size,
            balancing = emptyList(),
            protections = emptyList(),
            temperatures = data.temperatures,
        )
        return BatteryReading(
            basic = basic,
            cells = data.cells,
            receivedAt = System.currentTimeMillis(),
            antRealtime = data,
        )
    }

    suspend fun command(id: UUID, payload: List<Int>, cmd: Int, timeoutMs: Long = 3_000): BmsFrame {
        val link = links[id] ?: throw BleError.NotConnected
        return link.command(payload, cmd, timeoutMs)
    }

    /** Debug demo entry point for the synthetic ANT battery (see [DemoBattery]). */
    internal fun antReadingForDemo(data: AntRealtime): BatteryReading = antReading(data)

    /** ANT control write: one frame, ack frame back (cmd = request + 0x10). */
    suspend fun antCommand(id: UUID, payload: List<Int>, responseCmd: Int, timeoutMs: Long = 3_000): AntFrame {
        val link = links[id] ?: throw BleError.NotConnected
        return link.commandAnt(payload, responseCmd, timeoutMs)
    }

    private class GattLink(
        private val id: UUID,
        private val device: BluetoothDevice,
        private val type: BmsType,
    ) {
        /** ANT only: the device advertises an old-protocol name (see [BmsType.isLegacyAnt]). */
        val isLegacyAnt: Boolean get() = isLegacyAntDevice(device)

        @Volatile private var gatt: BluetoothGatt? = null
        @Volatile private var writeChar: BluetoothGattCharacteristic? = null
        @Volatile private var notifyChar: BluetoothGattCharacteristic? = null
        private val parser: BmsFrameParser? =
            if (type == BmsType.XIAOXIANG) BmsFrameParser() else null
        private val antParser: AntFrameParser? =
            if (type == BmsType.ANT) AntFrameParser() else null
        private val writeMutex = Mutex()
        private val waiters = ConcurrentHashMap<Int, CompletableDeferred<BmsFrame>>()
        private val antWaiters = ConcurrentHashMap<Int, CompletableDeferred<AntFrame>>()
        @Volatile private var nameWaiter: CompletableDeferred<BmsFrame>? = null

        // One barrier per handshake step, each resolved by the matching GATT callback.
        private val stateChanged = CompletableDeferred<Int>()
        private val servicesReady = CompletableDeferred<Boolean>()
        private val mtuSettled = CompletableDeferred<Int>()
        private val notifyReady = CompletableDeferred<Boolean>()

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.i(
                    TAG,
                    "onConnectionStateChange status=$status (0x%02X) state=$newState".format(status),
                )
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        // status 0 here; a non-zero status arrives together with a disconnect.
                        if (!stateChanged.isCompleted) stateChanged.complete(status)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (!stateChanged.isCompleted) stateChanged.complete(status)
                        resolveDeadHandshake()
                        failAll(BleError.NoConnect)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "onServicesDiscovered failed status=$status")
                    servicesReady.complete(false)
                    return
                }
                val service = gatt.getService(type.serviceUuid)
                notifyChar = service?.getCharacteristic(type.notifyUuid)
                writeChar = service?.getCharacteristic(type.writeUuid)
                // ANT has a single FFE1 characteristic for both directions; XiaoXiang
                // needs FF01 (notify) and FF02 (write) to be present.
                val found = notifyChar != null && writeChar != null
                Log.i(
                    TAG,
                    "services discovered ($type): notify=${notifyChar != null} " +
                        "write=${writeChar != null} " +
                        "all=${gatt.services?.map { it.uuid.toString().substring(4, 8) }}",
                )
                servicesReady.complete(found)
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.i(TAG, "onMtuChanged mtu=$mtu status=$status")
                mtuSettled.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 0)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                Log.i(TAG, "onDescriptorWrite ${descriptor.uuid} status=$status")
                if (descriptor.uuid == cccdUuid && !notifyReady.isCompleted) {
                    notifyReady.complete(status == BluetoothGatt.GATT_SUCCESS)
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

        /**
         * Connect and subscribe in the order the stack actually accepts:
         * connect -> settle -> discoverServices -> MTU -> CCCD. Asking for the MTU before the
         * discovery used to make discoverServices() return false and the link never opened.
         */
        suspend fun open() {
            val g = try {
                // TRANSPORT_LE is explicit: on a dual-mode device the default transport can
                // pick BR/EDR, which this BMS does not speak.
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } catch (e: SecurityException) {
                Log.w(TAG, "connectGatt denied: $e")
                null
            } catch (e: Exception) {
                Log.w(TAG, "connectGatt failed: $e")
                null
            } ?: throw BleError.NoConnect
            gatt = g
            Log.i(TAG, "connectGatt(TRANSPORT_LE) -> $g")

            val status = try {
                withTimeout(12_000) { stateChanged.await() }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "no onConnectionStateChange within 12 s")
                throw BleError.NoConnect
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "connection refused status=$status")
                throw BleError.NoConnect
            }
            // Ask for a high connection priority: some BMS (JBD/XiaoXiang) drop the link
            // within a second when the connection stays at the default low interval.
            try {
                val ok = g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                Log.i(TAG, "requestConnectionPriority(HIGH) -> $ok")
            } catch (e: Exception) {
                Log.w(TAG, "requestConnectionPriority failed: $e")
            }
            if (!discover(g)) throw BleError.NoConnect
            negotiateMtu(g)
            if (!enableNotifications(g)) throw BleError.NoConnect
        }

        private suspend fun discover(g: BluetoothGatt): Boolean {
            // Give the link a moment: some stacks reject discovery issued straight from the
            // CONNECTED callback.
            delay(250)
            flushServiceCache(g)
            repeat(3) { attempt ->
                val started = try {
                    g.discoverServices()
                } catch (e: Exception) {
                    Log.w(TAG, "discoverServices threw: $e")
                    false
                }
                Log.i(TAG, "discoverServices attempt ${attempt + 1} -> $started")
                if (started) {
                    when (withTimeoutOrNull(6_000) { servicesReady.await() }) {
                        true -> return true
                        // The device answered but has no expected service/characteristics: wrong device.
                        false -> return false
                        // No callback at all: worth one more request.
                        null -> Log.w(TAG, "no onServicesDiscovered callback")
                    }
                }
                delay(500)
            }
            return false
        }

        /** Hidden-API cache flush: the usual cure for a stale GATT database (status 129/133). */
        private fun flushServiceCache(g: BluetoothGatt) {
            try {
                val method = g.javaClass.getMethod("refresh")
                Log.i(TAG, "refresh() -> ${method.invoke(g)}")
            } catch (e: Exception) {
                Log.i(TAG, "refresh() unavailable: ${e.javaClass.simpleName}")
            }
        }

        private suspend fun negotiateMtu(g: BluetoothGatt) {
            // ANT app forces MTU 512 to reduce notification fragmentation on the 152-byte
            // status frame; XiaoXiang fits in 247 (default-ish) and a larger MTU is harmless
            // but some stacks refuse it.
            val wanted = if (type == BmsType.ANT) 512 else 247
            val requested = try {
                g.requestMtu(wanted)
            } catch (e: Exception) {
                false
            }
            if (!requested) {
                Log.i(TAG, "requestMtu($wanted) refused")
                return
            }
            Log.i(TAG, "mtu settled: ${withTimeoutOrNull(2_000) { mtuSettled.await() }}")
        }

        private suspend fun enableNotifications(g: BluetoothGatt): Boolean {
            val notify = notifyChar ?: return false
            val registered = try {
                g.setCharacteristicNotification(notify, true)
            } catch (e: Exception) {
                false
            }
            Log.i(TAG, "setCharacteristicNotification -> $registered")
            if (!registered) return false
            val cccd = notify.getDescriptor(cccdUuid)
            if (cccd == null) {
                Log.w(TAG, "${type.notifyUuid} has no CCCD; notifications stay unsubscribed")
                return true
            }
            val written = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == 0
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            } catch (e: Exception) {
                Log.w(TAG, "writeDescriptor threw: $e")
                false
            }
            Log.i(TAG, "writeDescriptor(CCCD) -> $written")
            if (!written) return false
            val ready = withTimeoutOrNull(5_000) { notifyReady.await() }
            Log.i(TAG, "notifications ready: $ready")
            return ready == true
        }

        private fun resolveDeadHandshake() {
            if (!servicesReady.isCompleted) servicesReady.complete(false)
            if (!mtuSettled.isCompleted) mtuSettled.complete(0)
            if (!notifyReady.isCompleted) notifyReady.complete(false)
        }

        private fun route(raw: List<Byte>?) {
            if (raw.isNullOrEmpty()) return
            val bytes = raw.map { it.toInt() and 0xFF }
            val ant = antParser
            if (ant != null) {
                for (frame in ant.push(bytes)) {
                    antWaiters[frame.cmd]?.let { if (!it.isCompleted) it.complete(frame) }
                }
                return
            }
            if (bytes.size >= 4 && bytes[0] == 0xFF && bytes[1] == 0xAA) {
                val ack = BmsCodec.parseNameResponse(bytes)
                if (ack != null) {
                    nameWaiter?.let { if (!it.isCompleted) it.complete(ack) }
                }
                return
            }
            val frames = parser?.push(bytes) ?: return
            for (frame in frames) {
                waiters[frame.cmd]?.let { if (!it.isCompleted) it.complete(frame) }
            }
        }

        private fun failAll(error: BleError) {
            for ((_, w) in waiters) if (!w.isCompleted) w.completeExceptionally(error)
            waiters.clear()
            for ((_, w) in antWaiters) if (!w.isCompleted) w.completeExceptionally(error)
            antWaiters.clear()
            nameWaiter?.let { if (!it.isCompleted) it.completeExceptionally(error) }
            nameWaiter = null
        }

        suspend fun command(payload: List<Int>, cmd: Int, timeoutMs: Long): BmsFrame {
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
                write(payload)
                try {
                    withTimeout(timeoutMs) { waiter.await() }
                } catch (e: TimeoutCancellationException) {
                    // A plain withTimeout would surface as CancellationException and kill the
                    // monitor loop; a missing answer is a normal protocol error.
                    throw BleError.NoResponse
                }
            } finally {
                waiters.remove(cmd, waiter)
                if (nameWaiter === waiter) nameWaiter = null
            }
        }

        /** ANT: one write, one complete frame back on the same characteristic. */
        suspend fun commandAnt(payload: List<Int>, responseCmd: Int, timeoutMs: Long): AntFrame {
            // A lost notification leaves half a frame behind; start every poll clean so a
            // truncated burst cannot stall the reassembler.
            antParser?.reset()
            val waiter = CompletableDeferred<AntFrame>()
            antWaiters[responseCmd]?.let {
                if (!it.isCompleted) it.completeExceptionally(BleError.NoResponse)
            }
            return try {
                antWaiters[responseCmd] = waiter
                write(payload)
                try {
                    withTimeout(timeoutMs) { waiter.await() }
                } catch (e: TimeoutCancellationException) {
                    throw BleError.NoResponse
                }
            } finally {
                antWaiters.remove(responseCmd, waiter)
            }
        }

        private suspend fun write(payload: List<Int>) {
            val char = writeChar ?: throw BleError.NotConnected
            val g = gatt ?: throw BleError.NotConnected
            writeMutex.withLock {
                val bytes = payload.map { it.toByte() }.toByteArray()
                val ok = if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(char, bytes, writeType(char)) == 0
                } else {
                    @Suppress("DEPRECATION")
                    char.writeType = writeType(char)
                    @Suppress("DEPRECATION")
                    char.value = bytes
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(char)
                }
                if (!ok) throw BleError.WriteFailed
            }
        }

        /**
         * ANT expects a write **with response** on FFE1; the XiaoXiang stack keeps
         * its original no-response preference, which is what its hardware accepted.
         */
        private fun writeType(char: BluetoothGattCharacteristic): Int {
            val noResponse =
                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            val withResponse =
                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            return if (type == BmsType.ANT && withResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else if (noResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
        }

        fun close() {
            Log.i(TAG, "closing link $id")
            resolveDeadHandshake()
            failAll(BleError.NoConnect)
            val g = gatt
            gatt = null
            writeChar = null
            notifyChar = null
            try {
                g?.disconnect()
                g?.close()
            } catch (_: Exception) {
            }
        }
    }
}
