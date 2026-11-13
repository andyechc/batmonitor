package com.andyechc.batmonitor.protocol.ant

/**
 * Charge/discharge remaining time. The BMS only appends these four fields when
 * the payload is long enough; older firmware stops before them.
 */
data class AntRemainingTime(
    /** `newChTime`, seconds. */
    val newChargeTimeSeconds: Long,
    /** `chTimeInterval`, seconds. */
    val chargeTimeIntervalSeconds: Long,
    /** `chTimeRemain`, **minutes** left to finish the charge. */
    val chargeRemainingMinutes: Int,
    /** `disTimeRemain`, **minutes** left until the pack is empty. */
    val dischargeRemainingMinutes: Int,
)

/**
 * Decoded real-time snapshot of an ANT BMS (`cmd 0x11` response).
 *
 * All offsets in the wire format are absolute from the `0x7E` header and every
 * multi-byte value is little-endian. Fields follow the exact order of
 * `decode_realtime()` in the `ant-bms-ble` reference project. See its
 * PROTOCOL.md spec.
 */
data class AntRealtime(
    /** `sysOperatingAut`: permission level reported by the BMS. */
    val sysOperatingAut: Int,
    /** 0 none, 1 idle, 2 charging, 3 standby, 4 discharging, 5 fault. */
    val systemState: Int,
    /** `protectMask` | (`protectMask1` shl 32); bits 0..44, mapped by [AntEnums]. */
    val protectionMask: Long,
    /** `warningMask` | (`warningMask1` shl 32); bits 0..44, mapped by [AntEnums]. */
    val warningMask: Long,
    /** Cell voltages in volts. */
    val cells: List<Double>,
    /** NTC sensor temperatures in °C. */
    val temperatures: List<Double>,
    /** Power-stage (MOS) temperature in °C. */
    val mosTemperature: Double,
    /** Balancing-stage temperature in °C. */
    val balanceTemperature: Double,
    val totalVoltage: Double,
    /**
     * Amperes **with the protocol sign**: positive while discharging, as the
     * captured frames show (`systemState = 4` discharge with `+18 A` and
     * `packPower = +941 W`). [com.andyechc.batmonitor.ble.BleCentral] flips it
     * to the app convention (positive while charging).
     */
    val current: Double,
    val soc: Int,
    val soh: Int,
    /** `disMosState` enum index (see the `ant_dis_mos_states` string array). */
    val dischargeMosState: Int,
    /** `chMosState` enum index (see the `ant_ch_mos_states` string array). */
    val chargeMosState: Int,
    /** `balanceState` enum index (see the `ant_balance_states` string array). */
    val balanceState: Int,
    /** `packPhysicsAh` in Ah. */
    val nominalCapacity: Double,
    /** `packRemainAh` in Ah. */
    val remainingCapacity: Double,
    /** `packAllAh`: lifetime accumulated Ah, not the current capacity. */
    val lifetimeAh: Double,
    /** `packPower` in W with the protocol sign; positive while discharging. */
    val power: Int,
    /** `allTimerS`: uptime in seconds. */
    val uptimeSeconds: Long,
    /** `balanceStateBit`: per-cell balancing bitmap. */
    val balanceStateBits: Long,
    val cellVoltageMax: Double,
    /** Index of the highest cell (0-based). */
    val cellVoltageMaxIndex: Int,
    val cellVoltageMin: Double,
    /** Index of the lowest cell (0-based). */
    val cellVoltageMinIndex: Int,
    val cellVoltageDiff: Double,
    val cellVoltageAverage: Double,
    /** Raw u16 gate/line voltages, as the firmware reports them. */
    val voltageDs: Int,
    val voltageDischargeMos: Int,
    val voltageChargeMos: Int,
    val voltageNhMos: Int,
    /** 64241 ternary, 64242 LiFePO4, 64243 LTO, 64244 custom, 64245 none, 64246 sodium. */
    val batteryType: Int,
    /** `packAllDisAh`: lifetime accumulated discharge counter (raw). */
    val lifetimeDischargedAh: Long,
    /** `packAllChAh`: lifetime accumulated charge counter (raw). */
    val lifetimeChargedAh: Long,
    /** `allDisTimerS`: seconds spent discharging (raw). */
    val dischargeTimerSeconds: Long,
    /** `allChTimerS`: seconds spent charging (raw). */
    val chargeTimerSeconds: Long,
    /** Null when the firmware does not send the trailing time block. */
    val remainingTimes: AntRemainingTime?,
    val chargeMosOn: Boolean,
    val dischargeMosOn: Boolean,
    val balancing: Boolean,
)

/**
 * Builds ANT requests and decodes the real-time response. Pure Kotlin, no BLE
 * dependency, mirroring `ant_bms_ble`'s `protocol.py`.
 */
object AntCodec {
    const val REALTIME_REQUEST = 0x01
    const val REALTIME_RESPONSE = 0x11

    /**
     * MOS / balance control: `WriteData(addr = action, len = 0, cmd = 0x51)`.
     * The BMS answers with cmd `0x61` (see [responseCommand]). Never send any
     * other `0x51` addr: 10 starts a firmware upgrade and 11 is "stop".
     */
    const val MOS_CONTROL = 0x51
    const val ACTION_CLOSE_DISCHARGE = 0x01
    const val ACTION_OPEN_DISCHARGE = 0x03
    const val ACTION_CLOSE_CHARGE = 0x04
    const val ACTION_OPEN_CHARGE = 0x06
    const val ACTION_BALANCE_ON = 0x0D
    const val ACTION_BALANCE_OFF = 0x0E

    /** Header/reserved bytes before the cell block (offsets 0..33). */
    private const val CELLS_OFFSET = 34

    /**
     * Fixed tail after the cell and temperature blocks, in bytes: MOS/balance
     * temperature, pack voltage, current, SOC, SOH (12), MOS states (2),
     * balance state (2), capacity + power + timers + balance bits (24),
     * cell statistics (12), gate voltages (8), battery type (2) and the four
     * lifetime counters (16).
     */
    private const val FIXED_TAIL = 78

    /**
     * Extra bytes only present when `frame[5] > 2·temperatures + 2·cells + 106`:
     * `newChTime`, `chTimeInterval`, `chTimeRemain`, `disTimeRemain`.
     */
    private const val TRAILING_TIME = 12

    /** Real-time request: `ReadData(0, 190, 0x01)`, sent every poll. */
    fun realtimeRequest(): List<Int> {
        val head = listOf(0x7E, 0xA1, REALTIME_REQUEST, 0x00, 0x00, 0xBE)
        val crc = AntCrc.crc16(head, offset = 1, length = 5)
        return head + listOf(crc and 0xFF, (crc shr 8) and 0xFF, 0xAA, 0x55)
    }

    /** ANT answers a request command with the same command plus 0x10 (0x01 -> 0x11). */
    fun responseCommand(requestCommand: Int): Int = (requestCommand + 0x10) and 0xFF

    /**
     * Control frame: `WriteData(action, 0, 0x51)` — the addr **is** the action,
     * no payload. Ground-truth captures from the ESPHome implementation:
     * `7E A1 51 01 00 00 D8 E5 AA 55` (discharge off) and
     * `7E A1 51 0D 00 00 18 E6 AA 55` (balance on), among others.
     */
    fun controlRequest(action: Int): List<Int> {
        val head = listOf(0x7E, 0xA1, MOS_CONTROL, action and 0xFF, 0x00, 0x00)
        val crc = AntCrc.crc16(head, offset = 1, length = 5)
        return head + listOf(crc and 0xFF, (crc shr 8) and 0xFF, 0xAA, 0x55)
    }

    /** Control action for one MOS switch move (charge or discharge, on or off). */
    fun mosAction(charge: Boolean, on: Boolean): Int = when {
        charge && on -> ACTION_OPEN_CHARGE
        charge -> ACTION_CLOSE_CHARGE
        on -> ACTION_OPEN_DISCHARGE
        else -> ACTION_CLOSE_DISCHARGE
    }

    /** Control action for the manual balancer switch. */
    fun balanceAction(on: Boolean): Int = if (on) ACTION_BALANCE_ON else ACTION_BALANCE_OFF

    /**
     * Decodes a complete `0x11` frame, or null when it is not a valid real-time
     * response. The frame layout is fixed after the per-cell and per-sensor
     * blocks, so a short payload is rejected instead of yielding garbage.
     */
    fun decodeRealtime(frame: List<Int>): AntRealtime? {
        if (frame.size < 10) return null
        if (frame[0] != 0x7E || frame[1] != 0xA1 || frame[2] != REALTIME_RESPONSE) return null
        val payloadLength = frame[5] and 0xFF
        if (payloadLength <= 0 || frame.size < 6 + payloadLength) return null

        val sysOperatingAut = frame[6] and 0xFF
        val systemState = frame[7] and 0xFF
        val temperatureCount = frame[8] and 0xFF
        val cellCount = frame[9] and 0xFF
        if (cellCount !in 1..32 || temperatureCount !in 0..16) return null

        // Everything up to the last lifetime counter must be inside the frame.
        val fixedEnd = CELLS_OFFSET + 2 * cellCount + 2 * temperatureCount + FIXED_TAIL
        if (frame.size < fixedEnd) return null

        val protectionMask = u32(frame, 10) or (u32(frame, 14) shl 32)
        val warningMask = u32(frame, 18) or (u32(frame, 22) shl 32)

        val cells = (0 until cellCount).map { i ->
            u16(frame, CELLS_OFFSET + 2 * i).toDouble() / 1000
        }

        var offset = CELLS_OFFSET + 2 * cellCount
        val temperatures = (0 until temperatureCount).map { i ->
            s16(frame, offset + 2 * i).toDouble()
        }
        offset += 2 * temperatureCount

        val mosTemperature = s16(frame, offset).toDouble()
        offset += 2
        val balanceTemperature = s16(frame, offset).toDouble()
        offset += 2
        val totalVoltage = u16(frame, offset).toDouble() / 100
        offset += 2
        val current = s16(frame, offset).toDouble() / 10
        offset += 2
        val soc = u16(frame, offset)
        offset += 2
        val soh = u16(frame, offset)
        offset += 2
        val dischargeMosState = frame[offset] and 0xFF
        offset += 1
        val chargeMosState = frame[offset] and 0xFF
        offset += 1
        val balanceState = u16(frame, offset)
        offset += 2
        val nominalCapacity = u32(frame, offset).toDouble() / 1e6
        offset += 4
        val remainingCapacity = u32(frame, offset).toDouble() / 1e6
        offset += 4
        val lifetimeAh = u32(frame, offset).toDouble() / 1e3
        offset += 4
        val power = s32(frame, offset)
        offset += 4
        val uptimeSeconds = u32(frame, offset)
        offset += 4
        val balanceStateBits = u32(frame, offset)
        offset += 4
        val cellVoltageMax = u16(frame, offset).toDouble() / 1000
        offset += 2
        val cellVoltageMaxIndex = u16(frame, offset)
        offset += 2
        val cellVoltageMin = u16(frame, offset).toDouble() / 1000
        offset += 2
        val cellVoltageMinIndex = u16(frame, offset)
        offset += 2
        val cellVoltageDiff = u16(frame, offset).toDouble() / 1000
        offset += 2
        val cellVoltageAverage = u16(frame, offset).toDouble() / 1000
        offset += 2
        val voltageDs = u16(frame, offset)
        offset += 2
        val voltageDischargeMos = u16(frame, offset)
        offset += 2
        val voltageChargeMos = u16(frame, offset)
        offset += 2
        val voltageNhMos = u16(frame, offset)
        offset += 2
        val batteryType = u16(frame, offset)
        offset += 2
        val lifetimeDischargedAh = u32(frame, offset)
        offset += 4
        val lifetimeChargedAh = u32(frame, offset)
        offset += 4
        val dischargeTimerSeconds = u32(frame, offset)
        offset += 4
        val chargeTimerSeconds = u32(frame, offset)
        offset += 4

        val remainingTimes =
            if (payloadLength > 2 * temperatureCount + 2 * cellCount + 106 &&
                offset + TRAILING_TIME <= frame.size
            ) {
                val newChargeTime = u32(frame, offset)
                offset += 4
                val chargeInterval = u32(frame, offset)
                offset += 4
                val chargeRemaining = u16(frame, offset)
                offset += 2
                val dischargeRemaining = u16(frame, offset)
                AntRemainingTime(
                    newChargeTimeSeconds = newChargeTime,
                    chargeTimeIntervalSeconds = chargeInterval,
                    chargeRemainingMinutes = chargeRemaining,
                    dischargeRemainingMinutes = dischargeRemaining,
                )
            } else {
                null
            }

        return AntRealtime(
            sysOperatingAut = sysOperatingAut,
            systemState = systemState,
            protectionMask = protectionMask,
            warningMask = warningMask,
            cells = cells,
            temperatures = temperatures,
            mosTemperature = mosTemperature,
            balanceTemperature = balanceTemperature,
            totalVoltage = totalVoltage,
            current = current,
            soc = soc,
            soh = soh,
            dischargeMosState = dischargeMosState,
            chargeMosState = chargeMosState,
            balanceState = balanceState,
            nominalCapacity = nominalCapacity,
            remainingCapacity = remainingCapacity,
            lifetimeAh = lifetimeAh,
            power = power,
            uptimeSeconds = uptimeSeconds,
            balanceStateBits = balanceStateBits,
            cellVoltageMax = cellVoltageMax,
            cellVoltageMaxIndex = cellVoltageMaxIndex,
            cellVoltageMin = cellVoltageMin,
            cellVoltageMinIndex = cellVoltageMinIndex,
            cellVoltageDiff = cellVoltageDiff,
            cellVoltageAverage = cellVoltageAverage,
            voltageDs = voltageDs,
            voltageDischargeMos = voltageDischargeMos,
            voltageChargeMos = voltageChargeMos,
            voltageNhMos = voltageNhMos,
            batteryType = batteryType,
            lifetimeDischargedAh = lifetimeDischargedAh,
            lifetimeChargedAh = lifetimeChargedAh,
            dischargeTimerSeconds = dischargeTimerSeconds,
            chargeTimerSeconds = chargeTimerSeconds,
            remainingTimes = remainingTimes,
            // Index 1 is "open" (MOS conducting); 0 is closed.
            chargeMosOn = chargeMosState == 1,
            dischargeMosOn = dischargeMosState == 1,
            balancing = balanceState != 0 || balanceStateBits != 0L,
        )
    }

    private fun u16(bytes: List<Int>, offset: Int): Int =
        (bytes[offset] and 0xFF) or ((bytes[offset + 1] and 0xFF) shl 8)

    /** Signed u16 (`UnsignToSign` in the vendor app). */
    private fun s16(bytes: List<Int>, offset: Int): Int {
        val value = u16(bytes, offset)
        return if (value > 32767) value - 65536 else value
    }

    private fun u32(bytes: List<Int>, offset: Int): Long =
        (u16(bytes, offset).toLong()) or (u16(bytes, offset + 2).toLong() shl 16)

    /** `packPower` is signed: negative while charging. */
    private fun s32(bytes: List<Int>, offset: Int): Int = u32(bytes, offset).toInt()
}
