package com.andyechc.batmonitor.ble

import android.content.Context
import com.andyechc.batmonitor.protocol.BmsCodec
import com.andyechc.batmonitor.protocol.BmsCommand
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MonitorStatus { CONNECTING, LIVE, ERROR }

class BatteryMonitor(private val context: Context, val id: UUID) {
    private val _status = MutableStateFlow(MonitorStatus.CONNECTING)
    val status: StateFlow<MonitorStatus> = _status.asStateFlow()

    private val _reading = MutableStateFlow<BatteryReading?>(null)
    val reading: StateFlow<BatteryReading?> = _reading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var connected = false
    private var job: Job? = null

    /** Poll and reconnect loop. Runs until the calling coroutine is cancelled. */
    suspend fun run(onReading: suspend (BatteryReading) -> Unit = {}) {
        try {
            while (true) {
                try {
                    if (!connected) {
                        _status.value = MonitorStatus.CONNECTING
                        BleCentral.connect(id)
                        connected = true
                    }
                    val reading = BleCentral.readBattery(id)
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
        if (!isLive) throw BleError.NotConnected
        val frame = BleCentral.command(id, payload, cmd)
        if (frame.status != 0) throw BleError.WriteFailed
    }

    suspend fun setSwitches(charge: Boolean, discharge: Boolean) {
        send(BmsCodec.switches(charge, discharge), BmsCommand.SWITCHES)
    }

    suspend fun balance() {
        send(BmsCodec.balance, BmsCommand.CONTROL)
    }

    suspend fun clearProtection() {
        send(BmsCodec.clearProtection, BmsCommand.CONTROL)
    }

    /** Writes the name to the BMS first; the caller saves it locally only on success. */
    suspend fun rename(to: String) {
        if (!isLive) throw BleError.NotConnected
        val frame = BleCentral.command(id, BmsCodec.bleName(to), BmsCommand.BLE_NAME)
        if (frame.status != 0) throw BleError.WriteFailed
    }

    fun messageFor(error: Throwable): String =
        (error as? BleError)?.let { BleCentral.string(it) }
            ?: error.message.orEmpty()
}
