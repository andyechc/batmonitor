package com.andyechc.batmonitor.data

import android.content.Context
import android.content.SharedPreferences
import com.andyechc.batmonitor.ble.BatteryReading
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedBattery(
    val id: UUID,
    val name: String,
    val addedAt: Long,
    /**
     * BLE MAC of the BMS. Android identifies a peripheral by its address, so it has to be stored:
     * without it a fresh process cannot connect until a scan finds the battery again.
     */
    val address: String? = null,
)

data class BatterySnapshot(
    val soc: Int,
    val voltage: Double,
    val current: Double,
    val receivedAt: Long,
)

/** Saved batteries plus last real readings, mirroring the iOS UserDefaults keys. */
class BatteryStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("batmonitor.store", Context.MODE_PRIVATE)

    /** Bumped on every mutation so Compose screens refresh. */
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0)

    var batteries: List<SavedBattery> = loadBatteries()
        private set

    var snapshots: Map<UUID, BatterySnapshot> = loadSnapshots()
        private set

    init {
        // Migration: the retired demo battery must never survive as a ghost BLE device.
        if (batteries.any { it.id == LEGACY_DEMO_ID } || snapshots.containsKey(LEGACY_DEMO_ID)) {
            batteries = batteries.filterNot { it.id == LEGACY_DEMO_ID }
            snapshots = snapshots - LEGACY_DEMO_ID
            save()
            saveSnapshots()
        }
    }

    fun battery(id: UUID): SavedBattery? = batteries.firstOrNull { it.id == id }

    fun snapshot(id: UUID): BatterySnapshot? = snapshots[id]

    fun record(reading: BatteryReading, id: UUID) {
        if (battery(id) == null) return
        snapshots = snapshots + (id to BatterySnapshot(
            soc = reading.basic.soc,
            voltage = reading.basic.totalVoltage,
            current = reading.basic.current,
            receivedAt = reading.receivedAt,
        ))
        saveSnapshots()
    }

    fun add(id: UUID, name: String, address: String? = null) {
        if (battery(id) != null) return
        batteries = batteries + SavedBattery(
            id = id,
            name = name,
            addedAt = System.currentTimeMillis(),
            address = address,
        )
        save()
    }

    fun rename(id: UUID, to: String) {
        batteries = batteries.map { if (it.id == id) it.copy(name = to) else it }
        save()
    }

    fun remove(id: UUID) {
        batteries = batteries.filterNot { it.id == id }
        snapshots = snapshots - id
        save()
        saveSnapshots()
    }

    private fun save() {
        val array = JSONArray()
        for (b in batteries) {
            val o = JSONObject()
            o.put("id", b.id.toString())
            o.put("name", b.name)
            o.put("addedAt", b.addedAt)
            b.address?.let { o.put("address", it) }
            array.put(o)
        }
        prefs.edit().putString(KEY_BATTERIES, array.toString()).apply()
        changes.value += 1
    }

    private fun saveSnapshots() {
        val o = JSONObject()
        for ((id, s) in snapshots) {
            val v = JSONObject()
            v.put("soc", s.soc)
            v.put("voltage", s.voltage)
            v.put("current", s.current)
            v.put("receivedAt", s.receivedAt)
            o.put(id.toString(), v)
        }
        prefs.edit().putString(KEY_SNAPSHOTS, o.toString()).apply()
        changes.value += 1
    }

    private fun loadBatteries(): List<SavedBattery> {
        val raw = prefs.getString(KEY_BATTERIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                // Older builds also wrote "isDemo"; the key is simply ignored now, and a missing
                // key never was a requirement, so old saves keep loading as real batteries.
                SavedBattery(
                    id = UUID.fromString(o.getString("id")),
                    name = o.getString("name"),
                    addedAt = o.optLong("addedAt", 0L),
                    address = o.optString("address", "").takeIf { it.isNotBlank() },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadSnapshots(): Map<UUID, BatterySnapshot> {
        val raw = prefs.getString(KEY_SNAPSHOTS, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = mutableMapOf<UUID, BatterySnapshot>()
            for (key in o.keys()) {
                val v = o.getJSONObject(key)
                out[UUID.fromString(key)] = BatterySnapshot(
                    soc = v.getInt("soc"),
                    voltage = v.getDouble("voltage"),
                    current = v.getDouble("current"),
                    receivedAt = v.getLong("receivedAt"),
                )
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val KEY_BATTERIES = "batmonitor.batteries.v2"
        const val KEY_SNAPSHOTS = "batmonitor.snapshots.v1"

        /** UUID of the removed demo battery; older installs may still have it saved. */
        val LEGACY_DEMO_ID: UUID = UUID.fromString("A73B1487-8B90-4A74-9512-273B9F100001")
    }
}
