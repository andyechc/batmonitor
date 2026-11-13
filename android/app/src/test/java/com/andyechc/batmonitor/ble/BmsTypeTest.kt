package com.andyechc.batmonitor.ble

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Protocol-family detection: service UUIDs win, names are the fallback. */
class BmsTypeTest {
    private val id = UUID.fromString("A73B1487-8B90-4A74-9512-273B9F100201")

    @Test
    fun detectsAntNames() {
        assertEquals(BmsType.ANT, BmsType.detect("ANT-BLE22AAUB-N88H"))
        assertEquals(BmsType.ANT, BmsType.detect("ANT@BLE22AAUB-N88H"))
        assertEquals(BmsType.ANT, BmsType.detect("ANT"))
        assertEquals(BmsType.ANT, BmsType.detect("ant-ble22aaub-yce7"))
        // Not every name starting with "ANT" is a battery.
        assertNull(BmsType.detect("ANTENA"))
        assertNull(BmsType.detect("Android TV"))
    }

    @Test
    fun detectsXiaoxiangNames() {
        assertEquals(BmsType.XIAOXIANG, BmsType.detect("JBD"))
        assertEquals(BmsType.XIAOXIANG, BmsType.detect("XiaoXiang BMS"))
    }

    @Test
    fun serviceUuidWinsOverANameThatLooksLikeAnotherFamily() {
        assertEquals(
            BmsType.ANT,
            BmsType.detect("JBD", listOf(BmsType.ANT_SERVICE)),
        )
        assertEquals(
            BmsType.XIAOXIANG,
            BmsType.detect("ANT-BLE22AAUB", listOf(BmsType.XIAOXIANG_SERVICE)),
        )
    }

    @Test
    fun unknownDevicesStayUnknown() {
        assertNull(BmsType.detect("TV", emptyList()))
        assertNull(BmsType.detect(null, emptyList()))
    }

    @Test
    fun scanMergeKeepsALaterDetectedType() {
        var devices = mergeScannedDevices(
            emptyList(),
            ScannedDevice(id, "ANT@BLE22AAUB", -80),
        )
        assertEquals(null, devices.single().type)

        devices = mergeScannedDevices(
            devices,
            ScannedDevice(id, "ANT@BLE22AAUB", -75, type = BmsType.ANT),
        )
        assertEquals(BmsType.ANT, devices.single().type)

        // A later sighting without a detection must not erase it.
        devices = mergeScannedDevices(devices, ScannedDevice(id, "ANT@BLE22AAUB", -70))
        assertEquals(BmsType.ANT, devices.single().type)
    }

    @Test
    fun antDevicesAreLikelyBatteries() {
        assertEquals(true, isLikelyBattery(ScannedDevice(id, "ANT-BLE22AAUB", -80)))
        assertEquals(
            true,
            isLikelyBattery(ScannedDevice(id, "Unknown", -80, serviceUuids = listOf(BmsType.ANT_SERVICE))),
        )
    }

    @Test
    fun storedTypeDefaultsToXiaoxiang() {
        assertEquals(BmsType.XIAOXIANG, BmsType.fromStorage(null))
        assertEquals(BmsType.XIAOXIANG, BmsType.fromStorage(""))
        assertEquals(BmsType.XIAOXIANG, BmsType.fromStorage("garbage"))
        assertEquals(BmsType.ANT, BmsType.fromStorage("ANT"))
    }
}
