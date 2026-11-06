package com.andyechc.batmonitor.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryStoreTest {
    private lateinit var context: Context

    private val prefs
        get() = context.getSharedPreferences("batmonitor.store", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs.edit().clear().commit()
    }

    /** Old installs saved the flag in the JSON; the field is gone, so the key is simply ignored. */
    @Test
    fun existingSavedBatteryDataDecodesWithoutDemoFlag() {
        prefs.edit().putString(
            BatteryStore.KEY_BATTERIES,
            "[{\"id\":\"A73B1487-8B90-4A74-9512-273B9F100002\"," +
                "\"name\":\"Existing battery\",\"addedAt\":0}," +
                "{\"id\":\"A73B1487-8B90-4A74-9512-273B9F100003\"," +
                "\"name\":\"Legacy flag\",\"addedAt\":12,\"isDemo\":true}]",
        ).commit()

        val batteries = BatteryStore(context).batteries

        assertEquals(2, batteries.size)
        assertEquals("Existing battery", batteries[0].name)
        assertEquals(0L, batteries[0].addedAt)
        assertEquals(UUID.fromString("A73B1487-8B90-4A74-9512-273B9F100002"), batteries[0].id)
        assertEquals("Legacy flag", batteries[1].name)
        assertEquals(12L, batteries[1].addedAt)
    }

    @Test
    fun legacyDemoBatteryIsDroppedOnLoad() {
        prefs.edit().putString(
            BatteryStore.KEY_BATTERIES,
            "[{\"id\":\"A73B1487-8B90-4A74-9512-273B9F100001\"," +
                "\"name\":\"BatMonitor · TEST\",\"addedAt\":0,\"isDemo\":true}," +
                "{\"id\":\"A73B1487-8B90-4A74-9512-273B9F100002\"," +
                "\"name\":\"Andy 72x45\",\"addedAt\":5}]",
        ).commit()
        prefs.edit().putString(
            BatteryStore.KEY_SNAPSHOTS,
            "{\"A73B1487-8B90-4A74-9512-273B9F100001\":" +
                "{\"soc\":72,\"voltage\":80.12,\"current\":0.0,\"receivedAt\":1}}",
        ).commit()

        val store = BatteryStore(context)

        assertEquals(listOf("Andy 72x45"), store.batteries.map { it.name })
        assertNull(store.snapshots[BatteryStore.LEGACY_DEMO_ID])
        // The ghost is also persisted away, so it cannot come back on the next launch.
        val raw = prefs.getString(BatteryStore.KEY_BATTERIES, "").orEmpty()
        assertTrue(!raw.contains("A73B1487-8B90-4A74-9512-273B9F100001"))
        assertTrue(raw.contains("Andy 72x45"))
    }

    @Test
    fun savedBatteryRoundTripKeepsIdNameAndDate() {
        val store = BatteryStore(context)
        val id = UUID.fromString("A73B1487-8B90-4A74-9512-273B9F100010")

        store.add(id, "Nueva")
        store.rename(id, "Renombrada")

        val reloaded = BatteryStore(context).batteries
        assertEquals(1, reloaded.size)
        assertEquals(id, reloaded[0].id)
        assertEquals("Renombrada", reloaded[0].name)
    }
}
