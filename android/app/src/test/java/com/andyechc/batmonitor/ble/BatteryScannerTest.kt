package com.andyechc.batmonitor.ble

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scan lists every BLE device (no service filter), so the list has to stay deduplicated while
 * the RSSI and the advertised name keep changing.
 */
class BatteryScannerTest {
    private val first = UUID.fromString("A73B1487-8B90-4A74-9512-273B9F100101")
    private val second = UUID.fromString("A73B1487-8B90-4A74-9512-273B9F100102")
    private val third = UUID.fromString("A73B1487-8B90-4A74-9512-273B9F100103")

    @Test
    fun newDevicesAreSortedByDescendingRssi() {
        var devices = emptyList<ScannedDevice>()
        devices = mergeScannedDevices(devices, ScannedDevice(first, "JBD", -70))
        devices = mergeScannedDevices(devices, ScannedDevice(second, "Band", -90))
        devices = mergeScannedDevices(devices, ScannedDevice(third, "TV", -80))

        assertEquals(listOf(first, third, second), devices.map { it.id })
    }

    @Test
    fun repeatedSightingUpdatesRssiWithoutDuplicating() {
        var devices = mergeScannedDevices(emptyList(), ScannedDevice(first, "JBD", -90))
        devices = mergeScannedDevices(devices, ScannedDevice(first, "JBD", -75))

        assertEquals(1, devices.size)
        assertEquals(-75, devices.single().rssi)
    }

    @Test
    fun nameFromALaterPacketReplacesTheAddressPlaceholder() {
        val mac = "46:48:EC:25:05:91"
        var devices = mergeScannedDevices(emptyList(), ScannedDevice(first, mac, -80))
        devices = mergeScannedDevices(devices, ScannedDevice(first, "JBD", -80))

        assertEquals("JBD", devices.single().name)
    }
}
