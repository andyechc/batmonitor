package com.andyechc.batmonitor.ble

import java.util.UUID

private val XIAOXIANG_SERVICE_UUID: UUID =
    UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
private val XIAOXIANG_NOTIFY_UUID: UUID =
    UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
private val XIAOXIANG_WRITE_UUID: UUID =
    UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")

private val ANT_SERVICE_UUID: UUID =
    UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
private val ANT_CHARACTERISTIC_UUID: UUID =
    UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

/**
 * BMS protocol families the app can read.
 *
 * - [XIAOXIANG]: XiaoXiang/JBD, service `FF00`, notify `FF01`, write `FF02`,
 *   frames `DD A5 ... 77`.
 * - [ANT]: ANT BMS, service `FFE0`, one bidirectional characteristic `FFE1`,
 *   frames `7E A1 ... AA 55` with CRC-16/Modbus.
 */
enum class BmsType(
    val serviceUuid: UUID,
    val notifyUuid: UUID,
    val writeUuid: UUID,
) {
    XIAOXIANG(XIAOXIANG_SERVICE_UUID, XIAOXIANG_NOTIFY_UUID, XIAOXIANG_WRITE_UUID),
    ANT(ANT_SERVICE_UUID, ANT_CHARACTERISTIC_UUID, ANT_CHARACTERISTIC_UUID),
    ;

    companion object {
        val XIAOXIANG_SERVICE: UUID get() = XIAOXIANG_SERVICE_UUID
        val ANT_SERVICE: UUID get() = ANT_SERVICE_UUID

        /**
         * Detects the protocol family from the advertised service UUIDs first
         * (unambiguous) and from the device name second. Returns null when the
         * device does not look like a supported BMS.
         *
         * ANT units advertise as `ANT` or `ANT-BLE...` / `ANT@BLE...`
         * (e.g. `ANT@BLE22AAUB-N88H`).
         */
        fun detect(name: String?, serviceUuids: List<UUID> = emptyList()): BmsType? {
            if (serviceUuids.any { it == ANT_SERVICE_UUID }) return ANT
            if (serviceUuids.any { it == XIAOXIANG_SERVICE_UUID }) return XIAOXIANG
            val normalized = name?.trim()?.uppercase() ?: return null
            if (isAntName(normalized)) return ANT
            if (isXiaoxiangName(normalized)) return XIAOXIANG
            return null
        }

        /** Reads the persisted `type` field; older saves have none and were XiaoXiang. */
        fun fromStorage(value: String?): BmsType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: XIAOXIANG

        /**
         * True for an ANT unit that still speaks the **old** protocol (`AA 55`
         * frames), which this app cannot decode.
         *
         * The vendor app asks the user to confirm the old protocol for a
         * 10-character `ANT-BLE...` name or when the name carries a `-` right
         * after the prefix (`ANT-BLE-2212A`). We cannot ask, so those packs are
         * rejected with [BleError.LegacyAnt] instead. Numeric short codes
         * (`ANT-BLE1234`) are old units too; the new protocol names carry
         * letters (`ANT-BLE22AAUB-N88H`).
         */
        fun isLegacyAnt(name: String?): Boolean {
            val trimmed = name?.trim() ?: return false
            if (!trimmed.startsWith(ANT_PREFIX)) return false
            // Same rule as the vendor app: the legacy-protocol prompt appears
            // for 10-character names or when the character at index 10 is '-'.
            if (trimmed.length == 10) return true
            return trimmed.length > 10 && trimmed[10] == '-'
        }

        private const val ANT_PREFIX = "ANT-BLE"

        private fun isAntName(name: String): Boolean =
            name == "ANT" ||
                name.startsWith("ANT-") ||
                name.startsWith("ANT@") ||
                name.startsWith("ANT_") ||
                name.startsWith("ANT BLE") ||
                name.startsWith("ANTBLE")

        private fun isXiaoxiangName(name: String): Boolean {
            val lowered = name.lowercase()
            val keywords = listOf("jbd", "xiaoxiang", "xiao xiang", "jikong", "jk bms")
            return keywords.any { lowered.contains(it) }
        }
    }
}
