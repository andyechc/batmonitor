package com.andyechc.batmonitor.ble

import com.andyechc.batmonitor.protocol.BmsBasicInfo
import java.util.UUID

data class ScannedDevice(val id: UUID, val name: String, val rssi: Int)

data class BatteryReading(val basic: BmsBasicInfo, val cells: List<Double>, val receivedAt: Long)

sealed class BleError : Exception() {
    data object BluetoothOff : BleError()
    data object Unauthorized : BleError()
    data object NoConnect : BleError()
    data object NoResponse : BleError()
    data object NotConnected : BleError()
    data object WriteFailed : BleError()
    data object InvalidData : BleError()
}
