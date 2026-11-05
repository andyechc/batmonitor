package com.andyechc.batmonitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.ui.theme.Theme
import java.util.Locale

@Composable
fun CellBarsView(cells: List<Double>, modifier: Modifier = Modifier) {
    val low = cells.minOrNull() ?: 0.0
    val high = cells.maxOrNull() ?: 0.0
    val span = high - low
    val balanced = span < 0.02
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        cells.forEachIndexed { index, voltage ->
            val ratio = if (balanced) 1f else (0.2 + 0.8 * ((voltage - low) / span)).toFloat()
            val animated by animateFloatAsState(targetValue = ratio, label = "cell$index")
            val color = when {
                balanced -> Theme.Neutral
                voltage == high -> Theme.Charge
                voltage == low -> Theme.Discharge
                else -> Theme.Neutral
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "C${index + 1}",
                    fontSize = 12.sp,
                    color = Theme.Muted,
                    modifier = Modifier.width(28.dp),
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Theme.SurfaceHigh),
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth(animated)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = String.format(Locale.US, "%.3f V", voltage),
                    style = Theme.numeric(17),
                    modifier = Modifier.width(84.dp),
                )
            }
        }
    }
}
