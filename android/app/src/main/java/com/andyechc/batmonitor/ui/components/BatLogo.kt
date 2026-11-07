package com.andyechc.batmonitor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andyechc.batmonitor.ui.theme.Theme
import kotlinx.coroutines.delay

/** Brand bat drawn in code. Wings flap briefly every few seconds. */
@Composable
fun BatLogo(size: Dp = 120.dp, flapUp: Boolean = false) {
    val context = LocalContext.current
    val animationsOff = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    } catch (_: Exception) {
        false
    }
    val flapAnim = remember { Animatable(if (flapUp) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (flapUp || animationsOff) return@LaunchedEffect
        while (true) {
            delay(3_500)
            repeat(2) {
                flapAnim.animateTo(1f, tween(180))
                flapAnim.animateTo(0f, tween(220))
                delay(200)
            }
        }
    }
    val flap = flapAnim.value
    val density = LocalDensity.current
    val boxPx = remember(size, density) { with(density) { size.toPx() } }
    val colors = Theme.colors
    Canvas(
        modifier = Modifier.size(size),
    ) {
        withTransform({
            translate(left = boxPx / 2, top = boxPx / 2)
            // NOTE: pivot must be Zero; the default (canvas center) shifts the drawing.
            scale(boxPx / 1024f, boxPx / 1024f, pivot = Offset.Zero)
            translate(left = -512f, top = -512f)
        }) {
            withTransform({ rotate(-24f * flap, Offset(470f, 503f)) }) {
                val wing = wingPath(mirrored = false)
                drawPath(wing, colors.surfaceHigh)
                drawPath(wing, colors.charge, style = Stroke(width = 10f))
            }
            withTransform({ rotate(24f * flap, Offset(554f, 503f)) }) {
                val wing = wingPath(mirrored = true)
                drawPath(wing, colors.surfaceHigh)
                drawPath(wing, colors.charge, style = Stroke(width = 10f))
            }
            withTransform({ translate(left = 0f, top = -50f * flap) }) {
                val earL = earPath(floatArrayOf(440f, 440f), floatArrayOf(416f, 372f), floatArrayOf(484f, 418f))
                drawPath(earL, colors.surfaceHigh)
                drawPath(earL, colors.charge, style = Stroke(width = 10f))
                val earR = earPath(floatArrayOf(584f, 440f), floatArrayOf(608f, 372f), floatArrayOf(540f, 418f))
                drawPath(earR, colors.surfaceHigh)
                drawPath(earR, colors.charge, style = Stroke(width = 10f))
                drawRoundRect(
                    colors.charge,
                    Offset(482f, 398f), Size(60f, 40f), CornerRadius(10f, 10f),
                )
                drawRoundRect(
                    colors.surfaceHigh,
                    Offset(426f, 430f), Size(172f, 264f), CornerRadius(40f, 40f),
                )
                drawRoundRect(
                    colors.charge,
                    Offset(426f, 430f), Size(172f, 264f), CornerRadius(40f, 40f),
                    style = Stroke(width = 12f),
                )
                clipPath(
                    Path().apply {
                        addRoundRect(RoundRect(438f, 442f, 438f + 148f, 442f + 240f, CornerRadius(30f, 30f)))
                    },
                ) {
                    drawRect(colors.charge, Offset(438f, 562f), Size(148f, 130f))
                }
                drawCircle(colors.charge, radius = 11f, center = Offset(478f, 506f))
                drawCircle(colors.charge, radius = 11f, center = Offset(546f, 506f))
            }
        }
    }
}

private fun wingPath(mirrored: Boolean): Path {
    fun mx(x: Float): Float = if (mirrored) 1024f - x else x
    return Path().apply {
        moveTo(mx(470f), 440f)
        cubicTo(mx(380f), 330f, mx(210f), 300f, mx(96f), 382f)
        cubicTo(mx(172f), 392f, mx(206f), 430f, mx(224f), 472f)
        cubicTo(mx(254f), 452f, mx(286f), 462f, mx(306f), 494f)
        cubicTo(mx(326f), 474f, mx(356f), 484f, mx(374f), 524f)
        cubicTo(mx(404f), 504f, mx(440f), 514f, mx(470f), 566f)
        close()
    }
}

private fun earPath(p1: FloatArray, p2: FloatArray, p3: FloatArray): Path {
    return Path().apply {
        moveTo(p1[0], p1[1])
        lineTo(p2[0], p2[1])
        lineTo(p3[0], p3[1])
        close()
    }
}
