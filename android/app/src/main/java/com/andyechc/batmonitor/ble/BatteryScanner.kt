package com.andyechc.batmonitor.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            BleCentral.discoveries.collect { device ->
                val current = _found.value
                if (current.none { it.id == device.id }) {
                    _found.value = (current + device).sortedBy { it.rssi }
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
