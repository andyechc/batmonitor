package com.andyechc.batmonitor.ble

import android.content.Context
import com.andyechc.batmonitor.protocol.BmsCodec
import android.util.Log
import com.andyechc.batmonitor.protocol.BmsCommand
import com.andyechc.batmonitor.protocol.ant.AntCodec
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MonitorStatus { CONNECTING, LIVE, ERROR }

class BatteryMonitor(
    private val context: Context,
    val id: UUID,
    val type: BmsType = BmsType.XIAOXIANG,
) {
    private val _status = MutableStateFlow(MonitorStatus.CONNECTING)
    val status: StateFlow<MonitorStatus> = _status.asStateFlow()

    private val _reading = MutableStateFlow<BatteryReading?>(null)
    val reading: StateFlow<BatteryReading?> = _reading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var connected = false
    private var job: Job? = null

    /**
     * Poll and reconnect loop. Runs until the calling coroutine is cancelled.
     * Debug demo batteries never touch BLE: they get synthetic readings.
     */
    suspend fun run(onReading: suspend (BatteryReading) -> Unit = {}) {
        // The demo shows the connect skeleton once, like a real connect; while
        // polling it stays LIVE so the controls never flicker disabled.
        var demoConnected = false
        try {
            while (true) {
                if (DemoBattery.isActive(context, id)) {
                    try {
                        if (!demoConnected) {
                            _status.value = MonitorStatus.CONNECTING
                            delay(800) // a brief skeleton, like a real connect
                            demoConnected = true
                        }
                        val demo = DemoBattery.reading(context, id)
                        _reading.value = demo
                        _status.value = MonitorStatus.LIVE
                        _message.value = null
                        onReading(demo)
                        delay(2_000)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        demoConnected = false
                        _status.value = MonitorStatus.ERROR
                        _message.value = e.message
                        delay(4_000)
                    }
                    continue
                }
                try {
                    if (!connected) {
                        _status.value = MonitorStatus.CONNECTING
                        BleCentral.connect(id, type)
                        connected = true
                    }
                    val reading = BleCentral.readBattery(id, type)
                    _reading.value = reading
                    _status.value = MonitorStatus.LIVE
                    _message.value = null
                    onReading(reading)
                    delay(2_000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    connected = false
                    BleCentral.disconnect(id)
                    _message.value = (e as? BleError)?.let { BleCentral.string(it) } ?: e.message
                    _status.value = MonitorStatus.ERROR
                    delay(4_000)
                }
            }
        } finally {
            connected = false
            BleCentral.disconnect(id)
        }
    }

    val isLive: Boolean get() = _status.value == MonitorStatus.LIVE

    private suspend fun send(payload: List<Int>, cmd: Int) {
        // Switches, balance and clear-protection are XiaoXiang-only commands. The UI
        // disables them for ANT, and this guard keeps any other caller honest.
        if (type != BmsType.XIAOXIANG) throw BleError.Unsupported
        if (DemoBattery.isActive(context, id)) return // demo: local state only
        if (!isLive) throw BleError.NotConnected
        val frame = BleCentral.command(id, payload, cmd)
        if (frame.status != 0) throw BleError.WriteFailed
    }

    suspend fun setSwitches(charge: Boolean, discharge: Boolean) {
        if (DemoBattery.isActive(context, id)) {
            DemoBattery.chargeOn = charge
            DemoBattery.dischargeOn = discharge
            return
        }
        send(BmsCodec.switches(charge, discharge), BmsCommand.SWITCHES)
    }

    /** ANT: move the charge or discharge MOS switch (see [AntCodec.mosAction]). */
    suspend fun antSetMos(charge: Boolean, on: Boolean) = antControl(AntCodec.mosAction(charge, on))

    /** ANT: turn the manual balancer on or off. */
    suspend fun antSetBalance(on: Boolean) = antControl(AntCodec.balanceAction(on))

    private suspend fun antControl(action: Int) {
        if (type != BmsType.ANT) throw BleError.Unsupported
        if (DemoBattery.isActive(context, id)) {
            DemoBattery.applyAntControl(action)
            return
        }
        if (!isLive) throw BleError.NotConnected
        Log.i(TAG, "ANT control action=0x%02X".format(action))
        // The ack echoes the request command + 0x10 (0x51 -> 0x61).
        BleCentral.antCommand(id, AntCodec.controlRequest(action), AntCodec.responseCommand(AntCodec.MOS_CONTROL))
        Log.i(TAG, "ANT control action=0x%02X acknowledged".format(action))
    }

    suspend fun balance() {
        send(BmsCodec.balance, BmsCommand.CONTROL)
    }

    suspend fun clearProtection() {
        send(BmsCodec.clearProtection, BmsCommand.CONTROL)
    }

    /** Writes the name to the BMS first; the caller saves it locally only on success. */
    suspend fun rename(to: String) {
        // ANT has no advertised-name write in its protocol, and the demo has no
        // BMS to write to, so the rename stays local in both cases.
        if (type != BmsType.XIAOXIANG || DemoBattery.isActive(context, id)) return
        if (!isLive) throw BleError.NotConnected
        val frame = BleCentral.command(id, BmsCodec.bleName(to), BmsCommand.BLE_NAME)
        if (frame.status != 0) throw BleError.WriteFailed
    }

    fun messageFor(error: Throwable): String =
        (error as? BleError)?.let { BleCentral.string(it) }
            ?: error.message.orEmpty()

    private companion object {
        const val TAG = "BmsControl"
    }
}
