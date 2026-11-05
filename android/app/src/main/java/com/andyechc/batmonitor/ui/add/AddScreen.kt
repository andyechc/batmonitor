package com.andyechc.batmonitor.ui.add

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.ble.BatteryScanner
import com.andyechc.batmonitor.ble.BleCentral
import com.andyechc.batmonitor.ble.BleError
import com.andyechc.batmonitor.ble.ScannedDevice
import com.andyechc.batmonitor.data.BatteryStore
import com.andyechc.batmonitor.ui.theme.SecondaryButton
import com.andyechc.batmonitor.ui.theme.Theme
import java.text.Normalizer
import kotlinx.coroutines.launch

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

private fun hasPermissions(context: Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun isBluetoothOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter?.state == BluetoothAdapter.STATE_ON
}

private fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()

@Composable
fun AddScreen(store: BatteryStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val scanner = remember { BatteryScanner() }
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasPermissions(context)) }
    var searchQuery by remember { mutableStateOf("") }
    val found by scanner.found.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()
    val error by scanner.error.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasPermissions(context) }

    LaunchedEffect(granted) {
        if (granted && isBluetoothOn(context)) {
            scope.launch { scanner.start() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Theme.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.device_cancel), tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.add_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.add_searchprompt), color = Theme.Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Theme.Muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Theme.Charge,
                unfocusedBorderColor = Theme.Border,
                cursorColor = Theme.Charge,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))

        StatusRow(
            isScanning = isScanning,
            error = error,
            bluetoothOn = isBluetoothOn(context),
            permissionsGranted = granted,
            onGrant = { permissionLauncher.launch(requiredPermissions()) },
        )

        val tokens = searchQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::fold)
        val filtered = if (tokens.isEmpty()) {
            found
        } else {
            found.filter { device ->
                val haystack = fold("${device.name} ${device.id} ${device.rssi} dBm")
                tokens.all { haystack.contains(it) }
            }
        }

        if (filtered.isNotEmpty()) {
            Text(
                text = stringResource(R.string.add_nearby),
                fontSize = 12.sp,
                color = Theme.Muted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { device ->
                DeviceRow(device = device, onSelect = {
                    scope.launch {
                        scanner.stop()
                        store.add(device.id, device.name)
                        onDone()
                    }
                })
            }
            if (!isScanning && found.isEmpty() && error == null) {
                item {
                    Text(
                        text = stringResource(R.string.add_empty),
                        fontSize = 15.sp,
                        color = Theme.Muted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            if (!isScanning && found.isNotEmpty() && searchQuery.isNotBlank() && filtered.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.add_nosearchresults),
                        fontSize = 15.sp,
                        color = Theme.Muted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
        if (!isScanning) {
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                onClick = {
                    if (!granted) {
                        permissionLauncher.launch(requiredPermissions())
                    } else {
                        scope.launch { scanner.start() }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.add_retry), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(
    isScanning: Boolean,
    error: String?,
    bluetoothOn: Boolean,
    permissionsGranted: Boolean,
    onGrant: () -> Unit,
) {
    when {
        !permissionsGranted -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.Danger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_needpermissions),
                    fontSize = 15.sp,
                    color = Theme.Danger,
                    modifier = Modifier.clickable(onClick = onGrant).weight(1f),
                )
            }
        }
        !bluetoothOn -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.Danger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_needbluetooth),
                    fontSize = 15.sp,
                    color = Theme.Danger,
                )
            }
        }
        isScanning -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Theme.Charge,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.add_scanning),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.add_scanhint),
                        fontSize = 13.sp,
                        color = Theme.Muted,
                    )
                }
            }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.Charge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_done),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Theme.Muted,
                )
            }
        }
    }
    if (error != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.Danger)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = error, fontSize = 15.sp, color = Theme.Danger)
        }
    }
}

@Composable
private fun DeviceRow(device: ScannedDevice, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Theme.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.BatteryStd, contentDescription = null, tint = Theme.Charge)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = device.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                text = "${device.rssi} dBm",
                fontSize = 12.sp,
                color = Theme.Muted,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Theme.Muted,
        )
    }
}
