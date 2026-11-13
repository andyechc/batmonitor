package com.andyechc.batmonitor.protocol.ant

/**
 * Pure mappings for the ANT enum fields of the real-time frame. They return
 * **indices** only; the UI resolves the text from its own string arrays, so the
 * protocol stays free of Android resources and is unit-testable.
 *
 * The bit tables are the ones from `decode_realtime()`'s `PROTECT_BITS` and
 * `WARNING_BITS`: 45 documented bits each.
 */
object AntEnums {
    /** Lowest `batteryType` value the firmware reports (ternary lithium). */
    const val BATTERY_TYPE_FIRST = 64241
    const val BATTERY_TYPE_LAST = 64246

    /** Highest documented protection/warning bit; anything above is ignored. */
    private const val LAST_BIT = 44

    /**
     * Index into the `ant_battery_types` string array, or -1 when the firmware
     * sent a value that is not in 64241..64246.
     */
    fun batteryTypeIndex(value: Int): Int =
        if (value in BATTERY_TYPE_FIRST..BATTERY_TYPE_LAST) value - BATTERY_TYPE_FIRST else -1

    /** Active protection bits, ascending. Index into `ant_protections`. */
    fun protectionIndices(mask: Long): List<Int> = activeBits(mask)

    /** Active warning bits, ascending. Index into `ant_warnings`. */
    fun warningIndices(mask: Long): List<Int> = activeBits(mask)

    private fun activeBits(mask: Long): List<Int> =
        (0..LAST_BIT).filter { bit -> ((mask ushr bit) and 1L) == 1L }
}
