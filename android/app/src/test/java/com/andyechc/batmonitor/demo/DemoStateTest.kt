package com.andyechc.batmonitor.demo

import com.andyechc.batmonitor.ble.MonitorStatus
import com.andyechc.batmonitor.data.SavedBattery
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoStateTest {
    @Test
    fun connectingStateShowsLoadingWithoutFakeReading() {
        assertEquals(MonitorStatus.CONNECTING, DemoState.CONNECTING.status)
        assertNull(DemoState.CONNECTING.reading)
    }

    @Test
    fun disconnectedStateKeepsAReading() {
        assertEquals(MonitorStatus.ERROR, DemoState.DISCONNECTED.status)
        assertNotNull(DemoState.DISCONNECTED.reading)
    }

    @Test
    fun chargingAndDischargingHaveOppositeCurrentSigns() {
        val charging = DemoState.CHARGING.reading!!
        val discharging = DemoState.DISCHARGING.reading!!
        assertTrue(charging.basic.current > 0)
        assertTrue(discharging.basic.current < 0)
    }

    @Test
    fun protectionScenarioSetsAnActiveProtection() {
        val reading = DemoState.PROTECTION.reading!!
        assertTrue(reading.basic.protections.any { it.active })
    }

    @Test
    fun existingSavedBatteryDataDecodesWithoutDemoFlag() {
        val o = JSONObject(
            "{\"id\":\"A73B1487-8B90-4A74-9512-273B9F100002\"," +
                "\"name\":\"Existing battery\",\"addedAt\":0}",
        )
        val battery = SavedBattery(
            id = java.util.UUID.fromString(o.getString("id")),
            name = o.getString("name"),
            addedAt = o.optLong("addedAt", 0L),
            isDemo = if (o.has("isDemo")) o.getBoolean("isDemo") else null,
        )
        assertFalse(battery.isDemo ?: false)
    }
}
