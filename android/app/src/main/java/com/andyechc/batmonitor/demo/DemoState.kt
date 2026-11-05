package com.andyechc.batmonitor.demo

import com.andyechc.batmonitor.ble.BatteryReading
import com.andyechc.batmonitor.ble.MonitorStatus
import com.andyechc.batmonitor.protocol.BmsBasicInfo
import com.andyechc.batmonitor.protocol.BmsProtection

/** BLE-free UI fixture, mirroring the iOS DemoBatteryState exactly. */
enum class DemoState {
    CHARGING,
    DISCHARGING,
    IDLE,
    CONNECTING,
    DISCONNECTED,
    PROTECTION,
    ;

    val titleKey: String
        get() = when (this) {
            CHARGING -> "demo_state_charging"
            DISCHARGING -> "demo_state_discharging"
            IDLE -> "demo_state_idle"
            CONNECTING -> "demo_state_connecting"
            DISCONNECTED -> "demo_state_disconnected"
            PROTECTION -> "demo_state_protection"
        }

    val status: MonitorStatus
        get() = when (this) {
            CONNECTING -> MonitorStatus.CONNECTING
            DISCONNECTED -> MonitorStatus.ERROR
            else -> MonitorStatus.LIVE
        }

    val reading: BatteryReading?
        get() {
            if (this == CONNECTING) return null
            val (soc, voltage, current, remaining, protectionActive) = when (this) {
                CHARGING -> Quint(64, 81.42, 8.4, 26.88, false)
                DISCHARGING -> Quint(57, 79.68, -3.2, 23.94, false)
                IDLE, CONNECTING -> Quint(100, 80.36, 0.0, 41.98, false)
                DISCONNECTED -> Quint(72, 80.12, 0.0, 30.24, false)
                PROTECTION -> Quint(91, 82.10, -1.8, 38.22, true)
            }
            val protections = (0 until 13).map { index ->
                BmsProtection(labelKey = "protection.$index", active = protectionActive && index == 0)
            }
            val baseCell = voltage / 24
            val cells = (0 until 24).map { index ->
                baseCell + ((index % 5) - 2) * (if (protectionActive) 0.012 else 0.002)
            }
            val basic = BmsBasicInfo(
                totalVoltage = voltage,
                current = current,
                remainingCapacity = remaining,
                nominalCapacity = 42.0,
                cycles = 37,
                productionDate = "2025-07-19",
                soc = soc,
                firmwareVersion = "7.13",
                chargeOn = true,
                dischargeOn = true,
                cellCount = 24,
                balancing = List(24) { false },
                protections = protections,
                temperatures = if (protectionActive) {
                    listOf(45.2, 39.4, 38.9)
                } else {
                    listOf(27.4, 27.8, 28.1)
                },
            )
            return BatteryReading(basic = basic, cells = cells, receivedAt = System.currentTimeMillis())
        }

    private data class Quint(
        val soc: Int,
        val voltage: Double,
        val current: Double,
        val remaining: Double,
        val protectionActive: Boolean,
    )
}
