package com.andyechc.batmonitor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.ui.theme.PrimaryButton
import com.andyechc.batmonitor.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class HelpTopic(val titleRes: Int, val bodyRes: Int) {
    SOC(R.string.help_soc_title, R.string.help_soc_body),
    VOLTAGE(R.string.help_voltage_title, R.string.help_voltage_body),
    CURRENT(R.string.help_current_title, R.string.help_current_body),
    POWER(R.string.help_power_title, R.string.help_power_body),
    CYCLES(R.string.help_cycles_title, R.string.help_cycles_body),
    MAX_TEMP(R.string.help_maxtemp_title, R.string.help_maxtemp_body),
    IMBALANCE(R.string.help_imbalance_title, R.string.help_imbalance_body),
    CELLS(R.string.help_cells_title, R.string.help_cells_body),
    TEMPERATURES(R.string.help_temperatures_title, R.string.help_temperatures_body),
    PROTECTIONS(R.string.help_protections_title, R.string.help_protections_body),
    CHARGE(R.string.help_charge_title, R.string.help_charge_body),
    DISCHARGE(R.string.help_discharge_title, R.string.help_discharge_body),
    BALANCE(R.string.help_balance_title, R.string.help_balance_body),
    CLEAR(R.string.help_clear_title, R.string.help_clear_body),
}

/** Medium bottom sheet with a white info icon, title and body. No X: swipe to dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(topic: HelpTopic, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.Surface,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(topic.titleRes),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(topic.bodyRes),
                fontSize = 16.sp,
                color = Theme.Neutral,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

/** Rename sheet: name field first, local save only after the BMS acknowledges. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Unit,
) {
    var draft by remember { mutableStateOf(currentName) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val trimmed = draft.trim()
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.Surface,
        contentColor = Color.White,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = stringResource(R.string.device_renametitle),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(12.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
                placeholder = { Text(stringResource(R.string.device_renametitle)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Theme.Charge,
                    unfocusedBorderColor = Theme.Border,
                    cursorColor = Theme.Charge,
                ),
            )
            Text(
                text = stringResource(R.string.device_renamehint),
                fontSize = 13.sp,
                color = Theme.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (error != null) {
                Text(
                    text = error!!,
                    fontSize = 14.sp,
                    color = Theme.Danger,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            PrimaryButton(
                onClick = {
                    if (trimmed.isEmpty() || saving) return@PrimaryButton
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            onSave(trimmed)
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message.orEmpty()
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = trimmed.isNotEmpty() && !saving && trimmed != currentName,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (saving) "…" else stringResource(R.string.device_save),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}
