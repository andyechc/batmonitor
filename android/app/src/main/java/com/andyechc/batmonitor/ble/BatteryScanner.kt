package com.andyechc.batmonitor.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Adds a new device, or refreshes the one already listed: the RSSI changes constantly and the
 * advertised name often arrives in a later packet (scan response), after the first sighting.
 */
internal fun mergeScannedDevices(
    current: List<ScannedDevice>,
    device: ScannedDevice,
): List<ScannedDevice> {
    val existing = current.firstOrNull { it.id == device.id }
    val merged = if (existing == null) {
        current + device
    } else {
        val name = when {
            device.name.isBlank() -> existing.name
            existing.name.isBlank() -> device.name
            else -> device.name
        }
        val address = device.address.ifBlank { existing.address }
        // The type often arrives with the service UUIDs in a later packet.
        val type = device.type ?: existing.type
        current.map {
            if (it.id == device.id) {
                it.copy(name = name, rssi = device.rssi, address = address, type = type)
            } else {
                it
            }
        }
    }
    // Descending numeric order: strongest signal (closest) first.
    return merged.sortedByDescending { it.rssi }
}

/** Heuristic: devices that look like a BMS (iOS only ever sees FF00-advertisers; on Android
 *  XiaoXiang/JBD units often advertise without that UUID, so we also match by name). */
fun isLikelyBattery(device: ScannedDevice): Boolean {
    // Service UUIDs and names recognized by the protocol layer (FF00 or FFE0).
    if (BmsType.detect(device.name, device.serviceUuids) != null) return true
    val name = device.name.lowercase()
    val keywords = listOf("spp", "bms", "battery", "andy")
    return keywords.any { name.contains(it) }
}

class BatteryScanner {
    private val _found = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val found: StateFlow<List<ScannedDevice>> = _found.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Runs a 12 s scan, collecting into [found]. Returns when done or cancelled. */
    suspend fun start() = coroutineScope {
        stop()
        _found.value = emptyList()
        _error.value = null
        try {
            BleCentral.startScan()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = (e as? BleError)?.let { BleCentral.string(it) } ?: e.message
            return@coroutineScope
        }
        _isScanning.value = true
        val collectJob = launch {
            launch {
                BleCentral.discoveries.collect { device ->
                    _found.value = mergeScannedDevices(_found.value, device)
                }
            }
            launch {
                // A rejected scan reports nothing else, so surface it instead of staying silent.
                BleCentral.scanError.collect { error ->
                    if (error != null) {
                        _error.value = BleCentral.string(error)
                        _isScanning.value = false
                        BleCentral.stopScan()
                    }
                }
            }
        }
        try {
            delay(12_000)
        } finally {
            collectJob.cancel()
            stop()
        }
    }

    fun stop() {
        BleCentral.stopScan()
        _isScanning.value = false
    }
}
