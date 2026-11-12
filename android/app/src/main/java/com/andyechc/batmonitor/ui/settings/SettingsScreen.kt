package com.andyechc.batmonitor.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.R
import com.andyechc.batmonitor.ui.components.BatLogo
import com.andyechc.batmonitor.ui.components.UpdateDialog
import com.andyechc.batmonitor.ui.theme.Theme
import com.andyechc.batmonitor.ui.theme.panel
import com.andyechc.batmonitor.update.UpdateCheck
import kotlinx.coroutines.launch

/** Settings: a manual update check plus the About entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateCheck.AvailableUpdate?>(null) }
    val versionName = remember(context) { appVersionName(context) }

    fun checkForUpdates() {
        if (checking) return
        scope.launch {
            checking = true
            when (val result = UpdateCheck.checkNow(context.applicationContext)) {
                is UpdateCheck.Result.Update -> update = result.update
                UpdateCheck.Result.UpToDate -> toast(context, context.getString(R.string.settings_latest))
                UpdateCheck.Result.Failed -> toast(context, context.getString(R.string.settings_updateerror))
            }
            checking = false
        }
    }

    Scaffold(
        containerColor = Theme.colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold, color = Theme.colors.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Theme.colors.text)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Theme.colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .panel(22.dp),
            ) {
                SettingsRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = stringResource(R.string.settings_updates),
                    subtitle = stringResource(R.string.settings_version, versionName),
                    enabled = !checking,
                    onClick = { checkForUpdates() },
                    trailing = {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Theme.colors.muted,
                            )
                        }
                    },
                )
                Divider(color = Theme.colors.border, modifier = Modifier.padding(start = 72.dp))
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.device_privacy),
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://andyechc.is-a.dev/batmonitor/privacy.html"))
                                    .apply { addCategory(Intent.CATEGORY_BROWSABLE) },
                            )
                        } catch (_: Exception) {
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Theme.colors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                Divider(color = Theme.colors.border, modifier = Modifier.padding(start = 72.dp))
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about_title),
                    onClick = onOpenAbout,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Theme.colors.muted,
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    update?.let { available ->
        UpdateDialog(update = available, onDismiss = { update = null })
    }
}

/** About: brand, short author copy and the developer site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }

    Scaffold(
        containerColor = Theme.colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.SemiBold, color = Theme.colors.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Theme.colors.text)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Theme.colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            BatLogo(size = 96.dp)
            Text(text = stringResource(R.string.app_name), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Theme.colors.text)
            Text(
                text = stringResource(R.string.settings_version, versionName),
                fontSize = 13.sp,
                color = Theme.colors.muted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .panel(22.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_body),
                    fontSize = 15.sp,
                    color = Theme.colors.text,
                )
                Row(
                    modifier = Modifier.clickable { openSite(context) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Theme.colors.charge, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.about_site),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Theme.colors.charge,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Theme.colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Theme.colors.charge, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = Theme.colors.text)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 13.sp, color = Theme.colors.muted)
            }
        }
        trailing()
    }
}

private fun appVersionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (_: Exception) {
    ""
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun openSite(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://andyechc.is-a.dev/"))
                .apply { addCategory(Intent.CATEGORY_BROWSABLE) },
        )
    } catch (_: Exception) {
    }
}
