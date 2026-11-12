package com.andyechc.batmonitor.ui.add

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.ble.BatteryScanner
import com.andyechc.batmonitor.ble.ScannedDevice
import com.andyechc.batmonitor.ble.isLikelyBattery
import com.andyechc.batmonitor.data.BatteryStore
import com.andyechc.batmonitor.ui.theme.SecondaryButton
import com.andyechc.batmonitor.ui.theme.Theme
import java.text.Normalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()

/** True while the system permission prompt can still be shown (i.e. not "don't ask again"). */
private fun systemPromptAvailable(context: Context, activity: Activity?): Boolean =
    requiredPermissions().any { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
            activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } == true
    }

@Composable
fun AddScreen(
    store: BatteryStore,
    onDone: () -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scanner = remember { BatteryScanner() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionsGranted by remember { mutableStateOf(hasPermissions(context)) }
    var bluetoothOn by remember { mutableStateOf(isBluetoothOn(context)) }
    var locationOn by remember { mutableStateOf(isLocationOn(context)) }
    // False after "don't ask again": the system prompt no longer appears, only Settings helps.
    var canRequestAgain by remember {
        mutableStateOf(hasPermissions(context) || systemPromptAvailable(context, activity))
    }
    // Guards the automatic start so returning from Settings never restarts a finished scan.
    var hasStarted by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllDevices by remember { mutableStateOf(false) }

    val found by scanner.found.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()
    val error by scanner.error.collectAsState()

    fun startWhenReady() {
        if (scanJob?.isActive == true) return
        if (!hasPermissions(context) || !isBluetoothOn(context) || !isLocationOn(context)) return
        hasStarted = true
        scanJob = scope.launch { scanner.start() }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        locationOn = isLocationOn(context)
        if (locationOn) startWhenReady()
    }

    // Home asks for the permissions before opening this sheet, so at this point the only work
    // left is to scan when everything is already in place.
    LaunchedEffect(Unit) {
        startWhenReady()
    }

    // Coming back from the permission dialog, Settings or a Bluetooth prompt re-reads the real state.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasPermissions(context)
                permissionsGranted = granted
                if (granted) {
                    canRequestAgain = true
                } else {
                    canRequestAgain = systemPromptAvailable(context, activity)
                }
                bluetoothOn = isBluetoothOn(context)
                locationOn = isLocationOn(context)
                if (!hasStarted) startWhenReady()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        activity?.startActivity(intent)
            ?: context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun scanNow() {
        when {
            // The sheet closes itself first: a system dialog on top of it can swallow the tap
            // that dismisses the dialog and close the sheet.
            !permissionsGranted -> onRequestPermissions()
            !bluetoothOn -> onEnableBluetooth()
            !locationOn -> locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> scope.launch { scanner.start() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.add_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.colors.text,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Theme.colors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.device_cancel), tint = Theme.colors.text)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text(stringResource(R.string.add_searchprompt), color = Theme.colors.muted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Theme.colors.muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Theme.colors.text,
                unfocusedTextColor = Theme.colors.text,
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = Theme.colors.surfaceHigh,
                unfocusedContainerColor = Theme.colors.surfaceHigh,
                cursorColor = Theme.colors.charge,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))

        val blocked = !permissionsGranted || !bluetoothOn || !locationOn
        when {
            blocked -> BlockerPanel(
                permissionsGranted = permissionsGranted,
                canRequestAgain = canRequestAgain,
                bluetoothOn = bluetoothOn,
                locationOn = locationOn,
                onGrant = onRequestPermissions,
                onOpenSettings = ::openAppSettings,
                onEnableBluetooth = onEnableBluetooth,
                onOpenLocation = { locationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )
            else -> ScanStatus(isScanning = isScanning, error = error)
        }

        val tokens = searchQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::fold)
        val baseFound = if (showAllDevices) found else found.filter { isLikelyBattery(it) }
        val filtered = if (tokens.isEmpty()) {
            baseFound
        } else {
            baseFound.filter { device ->
                val haystack = fold("${device.name} ${device.id} ${device.rssi} dBm")
                tokens.all { haystack.contains(it) }
            }
        }

        if (found.isNotEmpty() && !showAllDevices && found.any { !isLikelyBattery(it) }) {
            // Offer to reveal the rest of the BLE neighbourhood, mirroring iOS search.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.add_showall, found.count { !isLikelyBattery(it) }),
                    fontSize = 13.sp,
                    color = Theme.colors.muted,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showAllDevices = true }) {
                    Text(stringResource(R.string.add_showallaction), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (filtered.isNotEmpty()) {
            Text(
                text = stringResource(R.string.add_nearby),
                fontSize = 12.sp,
                color = Theme.colors.muted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { device ->
                DeviceRow(device = device, onSelect = {
                    scope.launch {
                        scanner.stop()
                        store.add(device.id, device.name, device.address)
                        onDone()
                    }
                })
            }
            if (!isScanning && found.isEmpty() && error == null && !blocked) {
                item {
                    Text(
                        text = stringResource(R.string.add_empty),
                        fontSize = 15.sp,
                        color = Theme.colors.muted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            if (!isScanning && found.isNotEmpty() && searchQuery.isNotBlank() && filtered.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.add_nosearchresults),
                        fontSize = 15.sp,
                        color = Theme.colors.muted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
        if (!isScanning) {
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                onClick = { scanNow() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.add_retry), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Shown instead of the scan status while the app cannot scan: missing permissions, Bluetooth off
 * or (pre-12) location services off. Each case offers the system flow that fixes it.
 */
@Composable
private fun BlockerPanel(
    permissionsGranted: Boolean,
    canRequestAgain: Boolean,
    bluetoothOn: Boolean,
    locationOn: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    val message = when {
        !permissionsGranted -> stringResource(R.string.add_permissionsdenied)
        !bluetoothOn -> stringResource(R.string.add_needbluetooth)
        else -> stringResource(R.string.add_needlocation)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.colors.danger)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = message, fontSize = 15.sp, color = Theme.colors.danger)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                !permissionsGranted -> {
                    if (canRequestAgain) {
                        SecondaryButton(onClick = onGrant) {
                            Text(stringResource(R.string.add_grantpermission), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    SecondaryButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.add_opensettings), fontWeight = FontWeight.SemiBold)
                    }
                }
                !bluetoothOn -> {
                    SecondaryButton(onClick = onEnableBluetooth) {
                        Text(stringResource(R.string.add_enablebluetooth), fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> {
                    SecondaryButton(onClick = onOpenLocation) {
                        Text(stringResource(R.string.add_enablelocation), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStatus(isScanning: Boolean, error: String?) {
    when {
        isScanning -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Theme.colors.charge,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.add_scanning),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Theme.colors.text,
                    )
                    Text(
                        text = stringResource(R.string.add_scanhint),
                        fontSize = 13.sp,
                        color = Theme.colors.muted,
                    )
                }
            }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.colors.charge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_done),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Theme.colors.muted,
                )
            }
        }
    }
    if (error != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.colors.danger)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = error, fontSize = 15.sp, color = Theme.colors.danger)
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
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Theme.colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.BatteryStd, contentDescription = null, tint = Theme.colors.charge)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = device.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Theme.colors.text)
            Text(
                text = "${device.rssi} dBm",
                fontSize = 12.sp,
                color = Theme.colors.muted,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Theme.colors.muted,
        )
    }
}
