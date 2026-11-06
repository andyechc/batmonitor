package com.andyechc.batmonitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.ui.theme.InfoGlyph
import com.andyechc.batmonitor.ui.theme.Theme
import com.andyechc.batmonitor.ui.theme.panel

@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String? = null,
    icon: ImageVector,
    accent: Color = Theme.colors.text,
    onInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panel()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = accent)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 13.sp, color = Theme.colors.muted)
            Spacer(modifier = Modifier.weight(1f))
            if (onInfo != null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.clickable(onClick = onInfo),
                ) { InfoGlyph() }
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = Theme.numeric(29), color = accent)
            if (unit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = unit, fontSize = 15.sp, color = Theme.colors.muted)
            }
        }
    }
}
