package com.andyechc.batmonitor.ble

import com.andyechc.batmonitor.protocol.BmsBasicInfo
import com.andyechc.batmonitor.protocol.ant.AntRealtime
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
    /** Protocol family detected from the advertisement; null while unknown. */
    val type: BmsType? = null,
)

data class BatteryReading(
    val basic: BmsBasicInfo,
    val cells: List<Double>,
    val receivedAt: Long,
    /**
     * Full ANT real-time snapshot (`cmd 0x11`) when the battery is an ANT BMS;
     * null for XiaoXiang. The fields the shared [basic] model does not carry
     * (protection/warning masks, enums, remaining times) live here.
     */
    val antRealtime: AntRealtime? = null,
)

sealed class BleError : Exception() {
    data object BluetoothOff : BleError()
    data object Unauthorized : BleError()
    data object NoConnect : BleError()
    data object NoResponse : BleError()
    data object NotConnected : BleError()
    data object WriteFailed : BleError()
    data object InvalidData : BleError()
    data object ScanFailed : BleError()

    /** The feature exists for XiaoXiang/JBD but not for this BMS family (ANT). */
    data object Unsupported : BleError()

    /** An ANT unit still on the old protocol (`AA 55` frames), which we cannot decode. */
    data object LegacyAnt : BleError()
}
