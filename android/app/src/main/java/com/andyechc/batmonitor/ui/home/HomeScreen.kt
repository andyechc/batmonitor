package com.andyechc.batmonitor.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.data.BatterySnapshot
import com.andyechc.batmonitor.data.BatteryStore
import com.andyechc.batmonitor.data.SavedBattery
import com.andyechc.batmonitor.ui.add.AddScreen
import com.andyechc.batmonitor.ui.components.BatLogo
import com.andyechc.batmonitor.ui.theme.PrimaryButton
import com.andyechc.batmonitor.ui.theme.SecondaryButton
import com.andyechc.batmonitor.ui.theme.Theme
import com.andyechc.batmonitor.ui.theme.panel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: BatteryStore, onOpenBattery: (SavedBattery) -> Unit) {
    store.changes.collectAsState()
    var showingAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedBattery?>(null) }
    val batteries = store.batteries

    Column(modifier = Modifier.fillMaxSize().background(Theme.colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Keep the header below the status bar; the app draws its own background behind it.
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showingAdd = true }) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_add), tint = Theme.colors.charge)
                }
            }
        }
        Text(
            text = stringResource(R.string.app_home),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Theme.colors.text,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (batteries.isEmpty()) {
            EmptyHome(
                onAdd = { showingAdd = true },
                onDemo = { onOpenBattery(SavedBattery.DEMO) },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.home_devices),
                        fontSize = 12.sp,
                        color = Theme.colors.muted,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
                items(batteries, key = { it.id }) { battery ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = battery
                            }
                            false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {},
                    ) {
                        BatterySummaryCard(
                            battery = battery,
                            snapshot = store.snapshot(battery.id),
                            onClick = { onOpenBattery(battery) },
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.demo_section),
                        fontSize = 12.sp,
                        color = Theme.colors.muted,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                    )
                }
                item {
                    DemoBatteryRow(
                        onClick = { onOpenBattery(SavedBattery.DEMO) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.demo_safetyhint),
                        fontSize = 13.sp,
                        color = Theme.colors.muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    if (showingAdd) {
        ModalBottomSheet(
            onDismissRequest = { showingAdd = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Theme.colors.background,
            contentColor = Theme.colors.text,
        ) {
            AddScreen(store = store, onDone = { showingAdd = false })
        }
    }

    pendingDelete?.let { battery ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Theme.colors.surface,
            title = { Text(stringResource(R.string.device_removetitle)) },
            text = { Text(stringResource(R.string.device_removeconfirm, battery.name)) },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(battery.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.device_delete), color = Theme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.device_cancel), color = Theme.colors.text)
                }
            },
        )
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit, onDemo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BatLogo(size = 120.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Theme.colors.text,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_text),
            fontSize = 15.sp,
            color = Theme.colors.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(onClick = onAdd) {
            Text(stringResource(R.string.home_add), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryButton(onClick = onDemo) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.demo_open), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BatterySummaryCard(battery: SavedBattery, snapshot: BatterySnapshot?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .panel(22.dp)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Theme.colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.BatteryStd, contentDescription = null, tint = Theme.colors.charge)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = battery.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Theme.colors.text)
                Text(text = stringResource(R.string.device_directbluetooth), fontSize = 13.sp, color = Theme.colors.muted)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Theme.colors.muted,
            )
        }
        if (snapshot != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Theme.colors.charge, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = String.format(Locale.US, "%.2f", snapshot.voltage),
                    style = Theme.numeric(17),
                    color = Theme.colors.text,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "V", fontSize = 15.sp, color = Theme.colors.muted)
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Theme.colors.muted, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(5.dp))
                val currentColor = when {
                    snapshot.current > 0.05 -> Theme.colors.charge
                    snapshot.current < -0.05 -> Theme.colors.discharge
                    else -> Theme.colors.muted
                }
                Text(
                    text = String.format(Locale.US, "%+.2f", snapshot.current),
                    style = Theme.numeric(17),
                    color = currentColor,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "A", fontSize = 15.sp, color = Theme.colors.muted)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "${snapshot.soc}", style = Theme.numeric(17), color = Theme.colors.text)
                Text(text = "%", style = Theme.numeric(17), color = Theme.colors.muted)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Theme.colors.surfaceHigh)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(snapshot.soc / 100f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Theme.colors.charge),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.device_lastreading,
                    DateUtils.getRelativeTimeSpanString(snapshot.receivedAt).toString(),
                ),
                fontSize = 12.sp,
                color = Theme.colors.muted,
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BluetoothSearching, contentDescription = null, tint = Theme.colors.charge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.device_norecentreading), fontSize = 15.sp, color = Theme.colors.muted)
            }
        }
    }
}

@Composable
private fun DemoBatteryRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .panel(22.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Theme.colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Theme.colors.muted)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = stringResource(R.string.demo_batteryname), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Theme.colors.text)
            Text(text = stringResource(R.string.demo_rowhint), fontSize = 13.sp, color = Theme.colors.muted)
        }
    }
}
