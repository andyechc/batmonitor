package com.andyechc.batmonitor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyechc.batmonitor.R

object Theme {
    val Background = Color(0xFF080808)
    val Surface = Color(0xFF151515)
    val SurfaceHigh = Color(0xFF202020)
    val Border = Color(0xFF2C2C2E)
    val Muted = Color(0xFF8E8E93)
    val Neutral = Color(0xFFD8D8DC)
    val Charge = Color(0xFF2EE6A6)
    val Discharge = Color(0xFFFFB547)
    val Danger = Color(0xFFFF453A)

    private val NumericFont = FontFamily(Font(R.font.mplus1p_extrabold, FontWeight.ExtraBold))

    /** M PLUS 1p ExtraBold for figures. */
    fun numeric(size: Int): TextStyle = TextStyle(
        fontFamily = NumericFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
        lineHeight = (size + 4).sp,
    )
}

private val BatMonitorColors = darkColorScheme(
    primary = Theme.Charge,
    onPrimary = Theme.Background,
    background = Theme.Background,
    onBackground = Color.White,
    surface = Theme.Surface,
    onSurface = Color.White,
    surfaceVariant = Theme.SurfaceHigh,
    onSurfaceVariant = Theme.Muted,
    error = Theme.Danger,
    onError = Color.White,
)

@Composable
fun BatMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BatMonitorColors,
        content = content,
    )
}

/** Distinct filled surface without outlines, like the iOS panels. */
fun Modifier.panel(cornerRadius: Dp = 24.dp): Modifier =
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(Theme.Surface)

/** Primary action: near-black text on the brand green fill. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Theme.Charge,
            contentColor = Theme.Background,
            disabledContainerColor = Theme.SurfaceHigh,
            disabledContentColor = Theme.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
        content = { content() },
    )
}

/** Secondary action: white text on a filled neutral surface. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Theme.SurfaceHigh,
            contentColor = Color.White,
            disabledContainerColor = Theme.SurfaceHigh,
            disabledContentColor = Theme.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
        content = { content() },
    )
}

/** Small "i" affordance next to values. Muted so it stays secondary. */
@Composable
fun InfoGlyph(modifier: Modifier = Modifier) {
    Text(
        text = "ⓘ",
        color = Theme.Muted,
        fontSize = 13.sp,
        modifier = modifier,
    )
}

/** Tinted status capsule, e.g. live / connecting / error. */
@Composable
fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            color = content,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
