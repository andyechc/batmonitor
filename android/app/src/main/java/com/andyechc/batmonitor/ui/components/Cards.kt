package com.andyechc.batmonitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.ui.theme.InfoGlyph
import com.andyechc.batmonitor.ui.theme.Theme
import com.andyechc.batmonitor.ui.theme.panel

/** Section card: optional uppercase title with icon and info affordance. */
@Composable
fun Card(
    title: String? = null,
    icon: ImageVector? = null,
    onInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panel()
            .padding(18.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Theme.colors.muted,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    text = title.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.colors.muted,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (onInfo != null) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.clickable(onClick = onInfo),
                    ) { InfoGlyph() }
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 14.dp))
        }
        content()
    }
}

@Composable
fun LabeledValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 16.sp, color = Theme.colors.muted)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, style = Theme.numeric(17), color = Theme.colors.text)
    }
}
