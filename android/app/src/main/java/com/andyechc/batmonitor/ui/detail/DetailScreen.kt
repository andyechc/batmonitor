package com.andyechc.batmonitor.ui.detail

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.ble.BatteryMonitor
import com.andyechc.batmonitor.ble.BleCentral
import com.andyechc.batmonitor.ble.BatteryReading
import com.andyechc.batmonitor.ble.BleError
import com.andyechc.batmonitor.ble.MonitorStatus
import com.andyechc.batmonitor.data.BatteryStore
import com.andyechc.batmonitor.ui.components.BatteryDashboardSkeleton
import com.andyechc.batmonitor.ui.components.Card
import com.andyechc.batmonitor.ui.components.CellBarsView
import com.andyechc.batmonitor.ui.components.FluidBattery
import com.andyechc.batmonitor.ui.components.HelpTopic
import com.andyechc.batmonitor.ui.theme.InfoGlyph
import com.andyechc.batmonitor.ui.components.InfoSheet
import com.andyechc.batmonitor.ui.components.LabeledValueRow
import com.andyechc.batmonitor.ui.components.RenameSheet
import com.andyechc.batmonitor.ui.components.StatTile
import com.andyechc.batmonitor.ui.theme.StatusPill
import com.andyechc.batmonitor.ui.theme.Theme
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private enum class DetailTab(val titleRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Filled.Speed),
    CELLS(R.string.tab_cells, Icons.Filled.BatteryFull),
    STATUS(R.string.tab_status, Icons.Filled.Shield),
    CONTROL(R.string.tab_control, Icons.Filled.Tune),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(batteryId: UUID, store: BatteryStore, onBack: () -> Unit) {
    val context = LocalContext.current
    store.changes.collectAsState()
    val battery = store.battery(batteryId)
    val currentName = battery?.name ?: ""

    val monitor = remember(batteryId) { BatteryMonitor(context.applicationContext, batteryId) }
    val monitorStatus by monitor.status.collectAsState()
    val monitorReading by monitor.reading.collectAsState()
    val monitorMessage by monitor.message.collectAsState()

    var tab by rememberSaveable { mutableStateOf(DetailTab.OVERVIEW) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var pendingOff by remember { mutableStateOf<SwitchTarget?>(null) }
    var pendingRemove by remember { mutableStateOf(false) }
    var infoTopic by remember { mutableStateOf<HelpTopic?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isLive = monitor.isLive

    LaunchedEffect(batteryId) {
        // The saved MAC lets the first attempt connect straight away instead of rescanning.
        store.battery(batteryId)?.address?.let { BleCentral.rememberAddress(batteryId, it) }
        monitor.run { reading -> store.record(reading, batteryId) }
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try {
                action()
                message = context.getString(R.string.device_controlok)
            } catch (e: Exception) {
                message = if (e is BleError) BleCentralString(e) else e.message.orEmpty()
            } finally {
                busy = false
            }
        }
    }

    fun applySwitch(target: SwitchTarget) {
        val basic = monitorReading?.basic ?: return
        val (charge, discharge) = when (target) {
            SwitchTarget.CHARGE -> true to basic.dischargeOn
            SwitchTarget.DISCHARGE -> basic.chargeOn to true
        }
        perform { monitor.setSwitches(charge, discharge) }
    }

    Scaffold(
        containerColor = Theme.colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentName, fontWeight = FontWeight.SemiBold, color = Theme.colors.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Theme.colors.text)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.device_more), tint = Theme.colors.text)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier.background(Theme.colors.surfaceHigh),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.device_rename), color = Theme.colors.text) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = Theme.colors.text) },
                                onClick = { menuOpen = false; renaming = true },
                                colors = MenuDefaults.itemColors(textColor = Theme.colors.text),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.device_balance), color = Theme.colors.text) },
                                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, tint = Theme.colors.text) },
                                enabled = isLive,
                                onClick = { menuOpen = false; perform { monitor.balance() } },
                                colors = MenuDefaults.itemColors(textColor = Theme.colors.text, disabledTextColor = Theme.colors.muted),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.device_clearprotection), color = Theme.colors.text) },
                                leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null, tint = Theme.colors.text) },
                                enabled = isLive,
                                onClick = { menuOpen = false; perform { monitor.clearProtection() } },
                                colors = MenuDefaults.itemColors(textColor = Theme.colors.text, disabledTextColor = Theme.colors.muted),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.device_remove), color = Theme.colors.danger) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Theme.colors.danger) },
                                onClick = { menuOpen = false; pendingRemove = true },
                                colors = MenuDefaults.itemColors(textColor = Theme.colors.danger),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Theme.colors.background),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Floating tabs stay above the gesture / navigation bar.
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Theme.colors.surfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                DetailTab.entries.forEach { t ->
                    val selected = t == tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) Theme.colors.surface else Color.Transparent)
                            .clickable { tab = t }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            t.icon,
                            contentDescription = null,
                            tint = if (selected) Theme.colors.charge else Theme.colors.text,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            stringResource(t.titleRes),
                            fontSize = 12.sp,
                            color = if (selected) Theme.colors.charge else Theme.colors.muted,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val reading = monitorReading
        if (reading == null) {
            ConnectingView(status = monitorStatus, message = monitorMessage, modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                when (tab) {
                    DetailTab.OVERVIEW -> OverviewTab(
                        reading = reading,
                        status = monitorStatus,
                        statusMessage = monitorMessage,
                        onInfo = { infoTopic = it },
                    )
                    DetailTab.CELLS -> CellsTab(reading = reading, onInfo = { infoTopic = it })
                    DetailTab.STATUS -> StatusTab(reading = reading, onInfo = { infoTopic = it })
                    DetailTab.CONTROL -> ControlTab(
                        batteryId = batteryId,
                        reading = reading,
                        isLive = isLive,
                        busy = busy,
                        message = message,
                        currentName = currentName,
                        onSwitch = { target, on ->
                            if (on) {
                                applySwitch(target)
                            } else {
                                pendingOff = target
                            }
                        },
                        onBalance = { perform { monitor.balance() } },
                        onClear = { perform { monitor.clearProtection() } },
                        onRename = { renaming = true },
                        onRemove = { pendingRemove = true },
                        onInfo = { infoTopic = it },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    pendingOff?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingOff = null },
            containerColor = Theme.colors.surface,
            title = {
                Text(
                    if (target == SwitchTarget.DISCHARGE) stringResource(R.string.device_dischargeswitch)
                    else stringResource(R.string.device_chargeswitch),
                )
            },
            text = {
                Text(
                    if (target == SwitchTarget.DISCHARGE) stringResource(R.string.device_confirmdischargeoff)
                    else stringResource(R.string.device_confirmchargeoff),
                )
            },
            confirmButton = {
                TextButton(onClick = { applySwitch(target); pendingOff = null }) {
                    Text(stringResource(R.string.device_confirm), color = Theme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOff = null }) {
                    Text(stringResource(R.string.device_cancel), color = Theme.colors.text)
                }
            },
        )
    }

    if (pendingRemove) {
        AlertDialog(
            onDismissRequest = { pendingRemove = false },
            containerColor = Theme.colors.surface,
            title = { Text(stringResource(R.string.device_removetitle)) },
            text = { Text(stringResource(R.string.device_removeconfirm, currentName)) },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(batteryId)
                    pendingRemove = false
                    onBack()
                }) {
                    Text(stringResource(R.string.device_delete), color = Theme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = false }) {
                    Text(stringResource(R.string.device_cancel), color = Theme.colors.text)
                }
            },
        )
    }

    if (renaming) {
        RenameSheet(
            currentName = currentName,
            onDismiss = { renaming = false },
            onSave = { name ->
                monitor.rename(name)
                store.rename(batteryId, name)
                message = context.getString(R.string.device_renameok)
            },
        )
    }

    infoTopic?.let { topic ->
        InfoSheet(topic = topic, onDismiss = { infoTopic = null })
    }
}

private fun BleCentralString(error: BleError): String = try {
    com.andyechc.batmonitor.ble.BleCentral.string(error)
} catch (_: Exception) {
    error.toString()
}
