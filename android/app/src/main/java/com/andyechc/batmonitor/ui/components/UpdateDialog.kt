package com.andyechc.batmonitor.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.ui.theme.Theme
import com.andyechc.batmonitor.update.UpdateCheck
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Offers the newer build published on the landing site; "Ahora no" simply closes it. */
@Composable
fun UpdateDialog(update: UpdateCheck.AvailableUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Theme.colors.surface,
        title = {
            Text(
                text = stringResource(R.string.update_title, update.versionName),
                color = Theme.colors.text,
            )
        },
        text = {
            Text(text = update.notes, color = Theme.colors.muted)
        },
        confirmButton = {
            TextButton(onClick = {
                startDownload(context, update.downloadUrl)
                onDismiss()
            }) {
                Text(stringResource(R.string.update_download), color = Theme.colors.charge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later), color = Theme.colors.text)
            }
        },
    )
}

private fun startDownload(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // No browser installed: staying silent is better than crashing over an optional update.
    }
}
