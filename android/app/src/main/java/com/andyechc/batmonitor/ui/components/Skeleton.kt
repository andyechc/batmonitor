package com.andyechc.batmonitor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andyechc.batmonitor.ui.theme.Theme
import com.andyechc.batmonitor.ui.theme.panel

/** Single shimmering dashboard skeleton for the connecting state. */
@Composable
fun BatteryDashboardSkeleton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsOff = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    } catch (_: Exception) {
        false
    }
    var sweep = 0.5f
    if (!animationsOff) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val animated by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "sweep",
        )
        sweep = animated
    }
    val shimmer = shimmerBrush(sweep)
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Hero.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBlock(shimmer, 92.dp, 25.dp, 25.dp)
            ShimmerBlock(shimmer, 190.dp, 100.dp, 20.dp)
            ShimmerBlock(shimmer, 150.dp, 20.dp, 10.dp)
        }
        // Metric grid.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerBlock(shimmer, 0.dp, 82.dp, 24.dp, Modifier.weight(1f))
                    ShimmerBlock(shimmer, 0.dp, 82.dp, 24.dp, Modifier.weight(1f))
                }
            }
        }
        // Cells.
        Column(
            modifier = Modifier.panel().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBlock(shimmer, 96.dp, 12.dp, 6.dp)
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerBlock(shimmer, 24.dp, 10.dp, 5.dp)
                    ShimmerBlock(shimmer, 0.dp, 10.dp, 5.dp, Modifier.weight(1f))
                    ShimmerBlock(shimmer, 54.dp, 10.dp, 5.dp)
                }
            }
        }
    }
}

private fun shimmerBrush(sweep: Float): Brush {
    val base = Theme.SurfaceHigh
    val shine = Color.White.copy(alpha = 0.13f)
    val center = 0.5f + sweep * 0.45f
    return Brush.linearGradient(
        0f to base,
        (center - 0.25f).coerceIn(0f, 1f) to base,
        center.coerceIn(0f, 1f) to shine,
        (center + 0.25f).coerceIn(0f, 1f) to base,
        1f to base,
    )
}

@Composable
private fun ShimmerBlock(
    brush: Brush,
    width: Dp,
    height: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    val m = if (width == 0.dp) modifier else modifier.width(width)
    Box(
        modifier = m
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(brush),
    )
}
