package com.andyechc.batmonitor.ble

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.andyechc.batmonitor.data.BatteryStore
import com.andyechc.batmonitor.protocol.BmsBasicInfo
import com.andyechc.batmonitor.protocol.BmsProtection
import com.andyechc.batmonitor.protocol.ant.AntCodec
import com.andyechc.batmonitor.protocol.ant.AntRemainingTime
import com.andyechc.batmonitor.protocol.ant.AntRealtime
import java.util.UUID

/**
 * Debug-only demo batteries so the detail screens can be exercised without a
 * real BMS. Seeded into the store when the app is debuggable (debug builds);
 * stripped again on a non-debuggable install, so release builds never show a
 * demo entry. The monitor branch in [BatteryMonitor.run] feeds synthetic
 * readings every 2 s and never touches BLE.
 */
object DemoBattery {
    /** Distinct from the retired legacy demo UUID the store drops on load. */
    val ANT_ID: UUID = UUID.fromString("5a9f3a62-7b1d-4c2e-9a44-2f0d6f1d0001")
    val XIAOXIANG_ID: UUID = UUID.fromString("5a9f3a62-7b1d-4c2e-9a44-2f0d6f1d0002")

    /** Demo state driven by the Control tab switches (XiaoXiang demo only). */
    @Volatile var chargeOn: Boolean = true
    @Volatile var dischargeOn: Boolean = true

    /** Demo state driven by the Control tab (ANT demo). */
    @Volatile var antChargeOn: Boolean = true
    @Volatile var antDischargeOn: Boolean = true
    @Volatile var antBalancing: Boolean = false

    private val demoIds = listOf(ANT_ID, XIAOXIANG_ID)

    fun isDemo(id: UUID): Boolean = id in demoIds

    fun isActive(context: Context, id: UUID): Boolean =
        isDebuggable(context) && isDemo(id)

    /** Adds the demo entries (idempotent) on debug installs; removes them otherwise. */
    fun seed(store: BatteryStore, context: Context) {
        if (isDebuggable(context)) {
            store.add(ANT_ID, "ANT demo · ANT-BLE22AAUB", type = BmsType.ANT)
            store.add(XIAOXIANG_ID, "Andy 72x45 · demo", type = BmsType.XIAOXIANG)
        } else {
            demoIds.forEach { if (store.battery(it) != null) store.remove(it) }
        }
    }

    /** Applies an ANT control action to the demo state (see [AntCodec] actions). */
    fun applyAntControl(action: Int) {
        Log.i(TAG, "ANT demo control action=0x%02X".format(action))
        when (action) {
            AntCodec.ACTION_OPEN_CHARGE -> antChargeOn = true
            AntCodec.ACTION_CLOSE_CHARGE -> antChargeOn = false
            AntCodec.ACTION_OPEN_DISCHARGE -> antDischargeOn = true
            AntCodec.ACTION_CLOSE_DISCHARGE -> antDischargeOn = false
            AntCodec.ACTION_BALANCE_ON -> antBalancing = true
            AntCodec.ACTION_BALANCE_OFF -> antBalancing = false
        }
    }

    /** The next synthetic snapshot for a demo battery; ticks make the values breathe. */
    fun reading(context: Context, id: UUID): BatteryReading {
        val tick = (System.currentTimeMillis() / 1000).toInt()
        val wobble = (tick % 10) - 5 // ±5 halves so every poll shows a small change
        return if (id == ANT_ID) antReading(tick, wobble) else xiaoxiangReading(tick, wobble)
    }

    private fun antReading(tick: Int, wobble: Int): BatteryReading {
        val cells = List(16) { i ->
            val base = if (i == 10) 3.227 else 3.268
            base + wobble * 0.0002
        }
        // With the discharge MOS open the pack keeps discharging; closing it
        // stops the current within a poll, like the real BMS would.
        val discharging = antDischargeOn
        val current = if (discharging) 18.0 + wobble * 0.1 else 0.0
        val power = if (discharging) 941 + wobble * 5 else 0
        val ant = AntRealtime(
            sysOperatingAut = 1,
            systemState = when {
                discharging -> 4 // discharging
                antChargeOn -> 3 // standby
                else -> 1 // idle
            },
            protectionMask = 0L,
            warningMask = (1L shl 4) or (1L shl 19) or (1L shl 23) or (1L shl 24),
            cells = cells,
            temperatures = listOf(27.0, 29.0, 25.0, 30.0),
            mosTemperature = 46.0,
            balanceTemperature = 28.0,
            totalVoltage = 52.29 + wobble * 0.01,
            // Protocol sign: positive while discharging.
            current = current,
            soc = 72,
            soh = 98,
            dischargeMosState = if (antDischargeOn) 1 else 0, // 1 open, 0 closed
            chargeMosState = if (antChargeOn) 1 else 0,
            balanceState = if (antBalancing) 5 else 0, // 0 off, 5 manual balancing
            nominalCapacity = 105.0,
            remainingCapacity = 75.6,
            lifetimeAh = 2450.0,
            power = power,
            uptimeSeconds = 18316890L + tick,
            balanceStateBits = if (antBalancing) 0x0FL else 0L, // balancing cells 1-4
            cellVoltageMax = cells.max(),
            cellVoltageMaxIndex = cells.indexOf(cells.max()),
            cellVoltageMin = cells.min(),
            cellVoltageMinIndex = cells.indexOf(cells.min()),
            cellVoltageDiff = cells.max() - cells.min(),
            cellVoltageAverage = cells.average(),
            voltageDs = 52,
            voltageDischargeMos = 52,
            voltageChargeMos = 0,
            voltageNhMos = 0,
            batteryType = 64242, // LiFePO4
            lifetimeDischargedAh = 4120,
            lifetimeChargedAh = 4180,
            dischargeTimerSeconds = 2250000,
            chargeTimerSeconds = 1980000,
            remainingTimes = AntRemainingTime(
                newChargeTimeSeconds = 0,
                chargeTimeIntervalSeconds = 0,
                chargeRemainingMinutes = 0,
                dischargeRemainingMinutes = if (discharging) 251 else 0,
            ),
            chargeMosOn = antChargeOn,
            dischargeMosOn = antDischargeOn,
            balancing = antBalancing,
        )
        return BleCentral.antReadingForDemo(ant)
    }

    private fun xiaoxiangReading(tick: Int, wobble: Int): BatteryReading {
        val cells = List(24) { i -> 3.312 + (i % 3) * 0.004 + wobble * 0.0002 }
        val basic = BmsBasicInfo(
            totalVoltage = 79.42 + wobble * 0.02,
            // App convention: positive while charging, negative while discharging.
            current = -8.4 + wobble * 0.1,
            remainingCapacity = 37.8,
            nominalCapacity = 45.0,
            cycles = 118,
            productionDate = "2024-03-15",
            soc = 84,
            firmwareVersion = "3.2",
            chargeOn = chargeOn,
            dischargeOn = dischargeOn,
            cellCount = cells.size,
            balancing = List(cells.size) { it % 7 == 0 },
            protections = (0 until 13).map { BmsProtection("protection.$it", false) },
            temperatures = listOf(26.5, 27.1, 28.0, 27.8),
        )
        return BatteryReading(
            basic = basic,
            cells = cells,
            receivedAt = System.currentTimeMillis(),
            antRealtime = null,
        )
    }

    private const val TAG = "BatMonitorDemo"

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
