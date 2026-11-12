package com.andyechc.batmonitor.ble

import com.andyechc.batmonitor.protocol.BmsBasicInfo
import java.util.UUID

/**
 * A device seen in a scan. [id] is a name-based UUID kept for the saved-battery key; [address]
 * is the real BLE MAC that Android needs to open the GATT link later. [serviceUuids] is what the
 * advertisement claimed, used to tell a BMS apart from the rest of the neighbourhood.
 */
data class ScannedDevice(
    val id: UUID,
    val name: String,
    val rssi: Int,
    val address: String = "",
    val serviceUuids: List<UUID> = emptyList(),
)

data class BatteryReading(val basic: BmsBasicInfo, val cells: List<Double>, val receivedAt: Long)

sealed class BleError : Exception() {
    data object BluetoothOff : BleError()
    data object Unauthorized : BleError()
    data object NoConnect : BleError()
    data object NoResponse : BleError()
    data object NotConnected : BleError()
    data object WriteFailed : BleError()
    data object InvalidData : BleError()
    data object ScanFailed : BleError()
}
