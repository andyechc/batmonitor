package com.andyechc.batmonitor.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.ble.BatteryReading
import com.andyechc.batmonitor.ble.BmsType
import com.andyechc.batmonitor.ble.MonitorStatus
import com.andyechc.batmonitor.protocol.ant.AntEnums
import com.andyechc.batmonitor.ui.components.BatteryDashboardSkeleton
import com.andyechc.batmonitor.ui.components.BiceaNativeBanner
import com.andyechc.batmonitor.ui.components.Card
import com.andyechc.batmonitor.ui.components.CellBarsView
import com.andyechc.batmonitor.ui.components.FluidBattery
import com.andyechc.batmonitor.ui.components.HelpTopic
import com.andyechc.batmonitor.ui.theme.InfoGlyph
import com.andyechc.batmonitor.ui.components.LabeledValueRow
import com.andyechc.batmonitor.ui.components.StatTile
import com.andyechc.batmonitor.ui.theme.StatusPill
import com.andyechc.batmonitor.ui.theme.Theme
import com.andyechc.batmonitor.ui.theme.panel
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@ReadOnlyComposable
private fun accentFor(current: Double): Color = when {
    current > 0.05 -> Theme.colors.charge
    current < -0.05 -> Theme.colors.discharge
    else -> Theme.colors.neutral
}

// MARK: - Overview

@Composable
fun OverviewTab(
    reading: BatteryReading,
    status: MonitorStatus,
    statusMessage: String?,
    onInfo: (HelpTopic) -> Unit,
    onProtections: () -> Unit = {},
    bmsType: BmsType = BmsType.XIAOXIANG,
) {
    val basic = reading.basic
    val accent = accentFor(basic.current)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusBadge(status = status, message = statusMessage)
        Spacer(modifier = Modifier.height(18.dp))
        FluidBattery(soc = basic.soc, current = basic.current, color = accent)
        Spacer(modifier = Modifier.height(18.dp))
        // State row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, labelRes, color) = when {
                basic.current > 0.05 -> Triple(Icons.Filled.FlashOn, R.string.device_state_charging, Theme.colors.charge)
                basic.current < -0.05 -> Triple(Icons.Filled.ArrowDownward, R.string.device_state_discharging, Theme.colors.discharge)
                else -> Triple(Icons.Filled.Pause, R.string.device_state_idle, Theme.colors.neutral)
            }
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(labelRes), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.clickable { onInfo(HelpTopic.SOC) }) { InfoGlyph() }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.device_capacity_remaining,
                String.format(Locale.US, "%.2f", basic.remainingCapacity),
                String.format(Locale.US, "%.0f", basic.nominalCapacity),
            ),
            fontSize = 15.sp,
            color = Theme.colors.muted,
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (basic.soc >= 100) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.colors.charge)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.device_fullycharged), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Theme.colors.charge)
            }
        } else {
            val missing = basic.nominalCapacity - basic.remainingCapacity
            if (basic.current > 0.05 && missing > 0) {
                val mins = (missing / basic.current * 60).roundToInt()
                val eta = if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = Theme.colors.charge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.device_timetofull, eta),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.colors.charge,
                    )
                }
            }
        }
    }
    // Stat grid.
    val power = abs(basic.totalVoltage * basic.current)
    val maxTemp = basic.temperatures.maxOrNull()
    val imbalance = if (reading.cells.isNotEmpty()) reading.cells.max() - reading.cells.min() else null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.device_stat_voltage),
                value = String.format(Locale.US, "%.2f", basic.totalVoltage),
                unit = "V",
                icon = Icons.Filled.Bolt,
                accent = Theme.colors.neutral,
                onInfo = { onInfo(HelpTopic.VOLTAGE) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.device_stat_current),
                value = String.format(Locale.US, "%+.2f", basic.current),
                unit = "A",
                icon = Icons.Filled.SwapHoriz,
                accent = accent,
                onInfo = { onInfo(HelpTopic.CURRENT) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.device_stat_power),
                value = String.format(Locale.US, "%.0f", power),
                unit = "W",
                icon = Icons.Filled.Speed,
                accent = accent,
                onInfo = { onInfo(HelpTopic.POWER) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.device_stat_cycles),
                // The ANT real-time frame has no cycle counter.
                value = if (bmsType == BmsType.ANT) "—" else "${basic.cycles}",
                icon = Icons.Filled.Refresh,
                accent = Theme.colors.neutral,
                onInfo = { onInfo(HelpTopic.CYCLES) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.device_stat_maxtemp),
                value = if (maxTemp != null) String.format(Locale.US, "%.1f", maxTemp) else "—",
                unit = if (maxTemp != null) "°C" else null,
                icon = Icons.Filled.Thermostat,
                accent = Theme.colors.neutral,
                onInfo = { onInfo(HelpTopic.MAX_TEMP) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.device_stat_imbalance),
                value = if (imbalance != null) String.format(Locale.US, "%.0f", imbalance * 1000) else "—",
                unit = if (imbalance != null) "mV" else null,
                icon = Icons.Filled.Scale,
                accent = Theme.colors.neutral,
                onInfo = { onInfo(HelpTopic.IMBALANCE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    // Protections summary. The ANT real-time frame has protection masks, but the
    // shared model only carries the XiaoXiang bit labels, so it is hidden there.
    if (bmsType == BmsType.XIAOXIANG) {
        val active = basic.protections.filter { it.active }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .panel()
                .clickable(onClick = onProtections)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active.isEmpty()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.colors.charge)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.device_noprotections),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Theme.colors.text,
                )
            } else {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.colors.danger)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.device_protectionsactive, active.size),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Theme.colors.text,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Theme.colors.muted)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    BiceaNativeBanner(modifier = Modifier.fillMaxWidth())
}

@Composable
fun StatusBadge(status: MonitorStatus, message: String?) {
    when (status) {
        MonitorStatus.LIVE -> StatusPill(
            text = stringResource(R.string.device_live),
            container = Theme.colors.charge,
            content = Theme.colors.charge,
            leading = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.colors.charge, modifier = Modifier.size(14.dp)) },
        )
        MonitorStatus.CONNECTING -> StatusPill(
            text = stringResource(R.string.device_connectingpill),
            container = Theme.colors.muted,
            content = Theme.colors.muted,
            leading = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = Theme.colors.muted, modifier = Modifier.size(14.dp)) },
        )
        MonitorStatus.ERROR -> StatusPill(
            text = message ?: stringResource(R.string.device_noconnection),
            container = Theme.colors.danger,
            content = Theme.colors.danger,
            leading = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.colors.danger, modifier = Modifier.size(14.dp)) },
        )
    }
}

@Composable
fun ConnectingView(status: MonitorStatus, message: String?, modifier: Modifier = Modifier) {
    if (status == MonitorStatus.CONNECTING) {
        BatteryDashboardSkeleton(modifier = modifier.fillMaxSize())
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StatusBadge(status = status, message = message)
            if (status == MonitorStatus.ERROR) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.device_retryhint),
                    fontSize = 15.sp,
                    color = Theme.colors.muted,
                )
            }
        }
    }
}

// MARK: - Cells

@Composable
fun CellsTab(reading: BatteryReading, onInfo: (HelpTopic) -> Unit) {
    val cells = reading.cells
    if (cells.isNotEmpty()) {
        val low = cells.min()
        val high = cells.max()
        Card(title = stringResource(R.string.device_cellrange), icon = Icons.Filled.SwapVert, onInfo = { onInfo(HelpTopic.CELLS) }) {
            LabeledValueRow(
                label = stringResource(R.string.device_lowest),
                value = String.format(Locale.US, "%.3f V", low),
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledValueRow(
                label = stringResource(R.string.device_highest),
                value = String.format(Locale.US, "%.3f V", high),
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledValueRow(
                label = stringResource(R.string.device_stat_imbalance),
                value = String.format(Locale.US, "%.0f mV", (high - low) * 1000),
            )
        }
        Card(
            title = "${stringResource(R.string.device_cells)} · ${cells.size}",
            icon = Icons.Filled.BatteryFull,
            onInfo = { onInfo(HelpTopic.CELLS) },
        ) {
            CellBarsView(cells = cells)
        }
    }
    if (reading.basic.temperatures.isNotEmpty()) {
        Card(title = stringResource(R.string.device_temperatures), icon = Icons.Filled.Thermostat, onInfo = { onInfo(HelpTopic.TEMPERATURES) }) {
            reading.basic.temperatures.forEachIndexed { index, temperature ->
                LabeledValueRow(
                    label = "${stringResource(R.string.device_sensor)} ${index + 1}",
                    value = String.format(Locale.US, "%.1f °C", temperature),
                )
                if (index < reading.basic.temperatures.size - 1) Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// MARK: - Status

@Composable
fun protectionLabel(context: android.content.Context, index: Int): String {
    val id = context.resources.getIdentifier("protection_$index", "string", context.packageName)
    return if (id != 0) context.getString(id) else "protection.$index"
}

@Composable
fun StatusTab(
    reading: BatteryReading,
    onInfo: (HelpTopic) -> Unit,
    bmsType: BmsType = BmsType.XIAOXIANG,
) {
    val context = LocalContext.current
    val ant = reading.antRealtime
    // XiaoXiang sends its protection bits inside the basic-info frame.
    if (bmsType == BmsType.XIAOXIANG) {
        Card(
            title = stringResource(R.string.device_protections),
            icon = Icons.Filled.Shield,
            onInfo = { onInfo(HelpTopic.PROTECTIONS) },
        ) {
            val active = reading.basic.protections.filter { it.active }
            if (active.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.colors.charge)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.device_noprotections),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Theme.colors.text,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    active.forEach { protection ->
                        val index = reading.basic.protections.indexOf(protection)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.colors.danger)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = protectionLabel(context, index),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = Theme.colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
    // ANT sends two 64-bit masks; the label of every active bit comes from the
    // ant_protections / ant_warnings string arrays (same order as the protocol).
    if (bmsType == BmsType.ANT && ant != null) {
        AntMaskCard(
            title = stringResource(R.string.device_protections),
            icon = Icons.Filled.Shield,
            indices = AntEnums.protectionIndices(ant.protectionMask),
            labels = context.resources.getStringArray(R.array.ant_protections),
            okText = stringResource(R.string.device_noprotections),
            onInfo = { onInfo(HelpTopic.PROTECTIONS) },
        )
        AntMaskCard(
            title = stringResource(R.string.device_warnings),
            icon = Icons.Filled.Warning,
            indices = AntEnums.warningIndices(ant.warningMask),
            labels = context.resources.getStringArray(R.array.ant_warnings),
            okText = stringResource(R.string.ant_nowarnings),
        )
    }
    Card(title = stringResource(R.string.device_equipment), icon = Icons.Filled.Memory) {
        val basic = reading.basic
        // Firmware, manufacture date, cycles and balancing cells come from the
        // XiaoXiang basic-info frame, which ANT does not have.
        if (bmsType == BmsType.XIAOXIANG) {
            LabeledValueRow(label = stringResource(R.string.device_firmware), value = basic.firmwareVersion)
            Spacer(modifier = Modifier.height(10.dp))
            LabeledValueRow(label = stringResource(R.string.device_manufactured), value = basic.productionDate)
            Spacer(modifier = Modifier.height(10.dp))
            LabeledValueRow(label = stringResource(R.string.device_stat_cycles), value = "${basic.cycles}")
            Spacer(modifier = Modifier.height(10.dp))
        }
        LabeledValueRow(
            label = stringResource(R.string.device_nominalcapacity),
            value = String.format(Locale.US, "%.0f Ah", basic.nominalCapacity),
        )
        if (bmsType == BmsType.ANT && ant != null) {
            val systemStates = context.resources.getStringArray(R.array.ant_system_states)
            val batteryTypes = context.resources.getStringArray(R.array.ant_battery_types)
            val chargeMosStates = context.resources.getStringArray(R.array.ant_ch_mos_states)
            val dischargeMosStates = context.resources.getStringArray(R.array.ant_dis_mos_states)
            val balanceStates = context.resources.getStringArray(R.array.ant_balance_states)
            val remaining = ant.remainingTimes
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledValueRow(
                    label = stringResource(R.string.device_systemstate),
                    value = stateLabel(systemStates, ant.systemState),
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_batterytype),
                    value = stateLabel(batteryTypes, AntEnums.batteryTypeIndex(ant.batteryType)),
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_soh),
                    value = "${ant.soh} %",
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_chmosstate),
                    value = stateLabel(chargeMosStates, ant.chargeMosState),
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_dismosstate),
                    value = stateLabel(dischargeMosStates, ant.dischargeMosState),
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_balancestate),
                    value = stateLabel(balanceStates, ant.balanceState),
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_mostemperature),
                    value = String.format(Locale.US, "%.0f °C", ant.mosTemperature),
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_balancetemperature),
                    value = String.format(Locale.US, "%.0f °C", ant.balanceTemperature),
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_chargeremaining),
                    value = remaining?.let { formatRemainingMinutes(it.chargeRemainingMinutes) } ?: "—",
                )
                LabeledValueRow(
                    label = stringResource(R.string.device_dischargeremaining),
                    value = remaining?.let { formatRemainingMinutes(it.dischargeRemainingMinutes) } ?: "—",
                )
            }
        }
        if (bmsType == BmsType.XIAOXIANG) {
            Spacer(modifier = Modifier.height(10.dp))
            LabeledValueRow(
                label = stringResource(R.string.device_balancingcells),
                value = "${basic.balancing.count { it }}",
            )
        }
    }

}

/** Resolves an enum index against a string array; out-of-range values show a dash. */
private fun stateLabel(labels: Array<String>, index: Int): String =
    if (index in labels.indices) labels[index] else "—"

/** Minutes to `Xd Xh Xm`, the same division the vendor app uses. */
private fun formatRemainingMinutes(minutes: Int): String {
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    val rest = minutes % 60
    return if (days > 0) "$days d $hours h $rest m" else "$hours h $rest m"
}

/** A protection or warning card: one red row per active bit, or a green all-clear. */
@Composable
private fun AntMaskCard(
    title: String,
    icon: ImageVector,
    indices: List<Int>,
    labels: Array<String>,
    okText: String,
    onInfo: (() -> Unit)? = null,
) {
    Card(title = title, icon = icon, onInfo = onInfo) {
        if (indices.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.colors.charge)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = okText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Theme.colors.text,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                indices.forEach { index ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Theme.colors.danger)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = labels.getOrNull(index) ?: "$index",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = Theme.colors.text,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Control

@Composable
fun ControlTab(
    batteryId: UUID,
    reading: BatteryReading,
    isLive: Boolean,
    busy: Boolean,
    message: String?,
    currentName: String,
    onSwitch: (SwitchTarget, Boolean) -> Unit,
    onBalance: () -> Unit,
    onAntBalance: (Boolean) -> Unit = {},
    onClear: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onInfo: (HelpTopic) -> Unit,
    bmsType: BmsType = BmsType.XIAOXIANG,
) {
    val basic = reading.basic
    val ant = reading.antRealtime
    // Both BMS types move the MOS switches; balance is a toggle for ANT and a
    // one-shot action for XiaoXiang. Clear-protection exists only on XiaoXiang.
    val isXiaoxiang = bmsType == BmsType.XIAOXIANG
    Card(title = stringResource(R.string.device_switches), icon = Icons.Filled.Power) {
        SwitchRow(
            label = stringResource(R.string.device_chargeswitch),
            icon = Icons.Filled.FlashOn,
            on = basic.chargeOn,
            enabled = isLive && !busy,
            onInfo = { onInfo(HelpTopic.CHARGE) },
            onChange = { onSwitch(SwitchTarget.CHARGE, it) },
        )
        Spacer(modifier = Modifier.height(14.dp))
        SwitchRow(
            label = stringResource(R.string.device_dischargeswitch),
            icon = Icons.Filled.ArrowDownward,
            on = basic.dischargeOn,
            enabled = isLive && !busy,
            onInfo = { onInfo(HelpTopic.DISCHARGE) },
            onChange = { onSwitch(SwitchTarget.DISCHARGE, it) },
        )
    }
    Card(title = stringResource(R.string.device_maintenance), icon = Icons.Filled.Build) {
        if (isXiaoxiang) {
            ActionRow(
                label = stringResource(R.string.device_balance),
                icon = Icons.Filled.Tune,
                enabled = isLive && !busy,
                onInfo = { onInfo(HelpTopic.BALANCE) },
                onClick = onBalance,
            )
        } else {
            SwitchRow(
                label = stringResource(R.string.device_balance),
                icon = Icons.Filled.Tune,
                on = ant?.balancing == true,
                enabled = isLive && !busy,
                onInfo = { onInfo(HelpTopic.BALANCE) },
                onChange = onAntBalance,
            )
        }
        if (isXiaoxiang) {
            Spacer(modifier = Modifier.height(14.dp))
            ActionRow(
                label = stringResource(R.string.device_clearprotection),
                icon = Icons.Filled.Shield,
                enabled = isLive && !busy,
                onInfo = { onInfo(HelpTopic.CLEAR) },
                onClick = onClear,
            )
        }
        if (busy) {
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Theme.colors.charge, strokeWidth = 3.dp)
        }
        if (message != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, fontSize = 13.sp, color = Theme.colors.muted)
        }
    }
    Card(title = stringResource(R.string.device_name), icon = Icons.Filled.Edit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Theme.colors.text,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Edit, contentDescription = null, tint = Theme.colors.muted)
        }
    }
    OutlinedButton(
        onClick = onRemove,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.danger),
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = Theme.colors.danger)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.device_remove), color = Theme.colors.danger, fontSize = 17.sp)
    }

}

@Composable
private fun SwitchRow(
    label: String,
    icon: ImageVector,
    on: Boolean,
    enabled: Boolean,
    onInfo: () -> Unit,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (enabled) Theme.colors.text else Theme.colors.muted)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 17.sp,
            color = if (enabled) Theme.colors.text else Theme.colors.muted,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(modifier = Modifier.clickable(onClick = onInfo)) { InfoGlyph() }
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Theme.colors.charge,
                checkedThumbColor = Theme.colors.switchThumb,
                uncheckedTrackColor = Theme.colors.surfaceHigh,
                uncheckedThumbColor = Theme.colors.muted,
            ),
        )
    }
}

enum class SwitchTarget { CHARGE, DISCHARGE }

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onInfo: () -> Unit,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) Theme.colors.text else Theme.colors.muted)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 17.sp,
                color = if (enabled) Theme.colors.text else Theme.colors.muted,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Theme.colors.muted,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(modifier = Modifier.clickable(onClick = onInfo)) { InfoGlyph() }
    }
}
