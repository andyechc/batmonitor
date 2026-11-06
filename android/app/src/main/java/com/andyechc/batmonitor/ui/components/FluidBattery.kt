package com.andyechc.batmonitor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.andyechc.batmonitor.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.sin

/** Code-drawn battery with a direction-following liquid wave, like iOS. */
@Composable
fun FluidBattery(
    soc: Int,
    current: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val level = soc.coerceIn(0, 100) / 100.0
    val context = LocalContext.current
    val animationsOff = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    } catch (_: Exception) {
        false
    }
    val animated = abs(current) > 0.05 && level < 1.0 && !animationsOff
    var phase = 0.8
    if (animated) {
        val transition = rememberInfiniteTransition(label = "wave")
        val direction = if (current > 0) 1f else -1f
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 4).toFloat(),
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
            label = "phase",
        )
        phase = (t * direction).toDouble()
    }
    val textMeasurer = rememberTextMeasurer()
    val textColor = Theme.colors.text
    val onAccent = Theme.colors.onCharge
    Box(
        modifier = modifier
            .width(199.dp)
            .height(100.dp)
            .semantics { contentDescription = "$soc%" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val bodyW = w - 9.dp.toPx()
            val pad = 7.dp.toPx()
            val inner = RoundRect(pad, pad, bodyW - pad, h - pad, CornerRadius(15.dp.toPx()))
            clipPath(Path().apply { addRoundRect(inner) }) {
                val label = "$soc%"
                val layout = textMeasurer.measure(label, Theme.numeric(34))
                val tx = (bodyW - layout.size.width) / 2f
                val ty = (h - layout.size.height) / 2f
                if (level >= 1.0) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.85f),
                        topLeft = Offset(pad, pad),
                        size = Size(bodyW - 2 * pad, h - 2 * pad),
                        cornerRadius = CornerRadius(15.dp.toPx()),
                    )
                    drawText(layout, color = onAccent, topLeft = Offset(tx, ty))
                } else {
                    val wave = Path()
                    val baseY = (h * (1 - level)).toFloat()
                    wave.moveTo(pad, h - pad)
                    var x = pad
                    while (x <= bodyW - pad) {
                        val y = baseY +
                            sin((x - pad) / 22.0 + phase).toFloat() * 5f +
                            sin((x - pad) / 11.0 + phase * 1.7).toFloat() * 3f
                        wave.lineTo(x, y)
                        x += 3f
                    }
                    wave.lineTo(bodyW - pad, h - pad)
                    wave.close()
                    drawPath(wave, color.copy(alpha = 0.85f))
                    drawText(layout, color = textColor, topLeft = Offset(tx, ty))
                    // Knockout figure over the liquid, like the iOS knockout text.
                    clipPath(wave) {
                        drawText(layout, color = onAccent, topLeft = Offset(tx, ty))
                    }
                }
            }
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(bodyW, h),
                cornerRadius = CornerRadius(20.dp.toPx()),
                style = Stroke(width = 3.dp.toPx()),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(bodyW, (h - 40.dp.toPx()) / 2),
                size = Size(9.dp.toPx(), 40.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}
