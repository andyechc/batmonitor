package com.andyechc.batmonitor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Semantic palette. Every screen reads these tokens instead of literal colors, so the whole app
 * flips with the system theme while keeping the same layout, shapes and role of each color.
 *
 * `charge` is the brand accent (charging, SOC, primary action), `discharge` marks discharging /
 * warnings, `danger` errors and destructive actions, `neutral` idle data and figures, `muted`
 * secondary labels, `text` primary text, `onCharge` text drawn on the brand green fill.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val muted: Color,
    val neutral: Color,
    val charge: Color,
    val discharge: Color,
    val danger: Color,
    val text: Color,
    val onCharge: Color,
    /** Thumb drawn on a saturated accent track (switches); white on both palettes. */
    val switchThumb: Color,
    val shimmer: Color,
)

/**
 * Dark palette: the original design, unchanged.
 *
 * background 080808 · surface 151515 · surfaceHigh 202020 · border 2C2C2E · muted 8E8E93 ·
 * neutral D8D8DC · charge 2EE6A6 · discharge FFB547 · danger FF453A · text FFFFFF · onCharge 080808 ·
 * switchThumb FFFFFF.
 */
val DarkColors = AppColors(
    background = Color(0xFF080808),
    surface = Color(0xFF151515),
    surfaceHigh = Color(0xFF202020),
    border = Color(0xFF2C2C2E),
    muted = Color(0xFF8E8E93),
    neutral = Color(0xFFD8D8DC),
    charge = Color(0xFF2EE6A6),
    discharge = Color(0xFFFFB547),
    danger = Color(0xFFFF453A),
    text = Color(0xFFFFFFFF),
    onCharge = Color(0xFF080808),
    switchThumb = Color(0xFFFFFFFF),
    shimmer = Color(0x21FFFFFF),
)

/**
 * Light palette: the same hierarchy on near-white neutrals, with the accents darkened until they
 * read on light surfaces (WCAG AA, ratios measured against `surface` / `background`).
 *
 * background F5F5F7 (17.3:1 with text) · surface FFFFFF · surfaceHigh E8E8ED · border D6D6DC ·
 * muted 63636B (5.95:1) · neutral 3C3C43 (10.9:1) · text 111114 (18.9:1) ·
 * charge 057451 (5.80:1, replaces 2EE6A6 which is 1.7:1 on white; white text on it 5.80:1) ·
 * discharge 96560A (5.78:1) · danger C42A21 (5.68:1) · onCharge FFFFFF · switchThumb FFFFFF ·
 * shimmer black 6%.
 */
val LightColors = AppColors(
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE8E8ED),
    border = Color(0xFFD6D6DC),
    muted = Color(0xFF63636B),
    neutral = Color(0xFF3C3C43),
    charge = Color(0xFF057451),
    discharge = Color(0xFF96560A),
    danger = Color(0xFFC42A21),
    text = Color(0xFF111114),
    onCharge = Color(0xFFFFFFFF),
    switchThumb = Color(0xFFFFFFFF),
    shimmer = Color(0x10000000),
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

/** Reactive color + type tokens. Colors change with the system theme. */
object Theme {
    /** Current palette for this composition (dark or light). */
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    private val NumericFont = FontFamily(Font(R.font.mplus1p_extrabold, FontWeight.ExtraBold))

    /** M PLUS 1p ExtraBold for figures. Color-free, so it also works inside draw lambdas. */
    fun numeric(size: Int): TextStyle = TextStyle(
        fontFamily = NumericFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
        lineHeight = (size + 4).sp,
    )
}

private fun batColorScheme(colors: AppColors, dark: Boolean) = run {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = colors.charge,
        onPrimary = colors.onCharge,
        primaryContainer = colors.charge,
        onPrimaryContainer = colors.onCharge,
        secondary = colors.neutral,
        onSecondary = colors.background,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.surface,
        onSurface = colors.text,
        surfaceVariant = colors.surfaceHigh,
        onSurfaceVariant = colors.muted,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surfaceHigh,
        surfaceContainerHighest = colors.surfaceHigh,
        outline = colors.border,
        outlineVariant = colors.border,
        error = colors.danger,
        onError = Color.White,
    )
}

@Composable
fun BatMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = batColorScheme(colors, darkTheme),
            content = content,
        )
    }
}

/** Distinct filled surface without outlines, like the iOS panels. */
@Composable
fun Modifier.panel(cornerRadius: Dp = 24.dp): Modifier {
    val colors = Theme.colors
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(colors.surface)
}

/** Primary action: dark text on the brand green fill (white text once the green is darkened). */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = Theme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.charge,
            contentColor = colors.onCharge,
            disabledContainerColor = colors.surfaceHigh,
            disabledContentColor = colors.muted,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
        content = { content() },
    )
}

/** Secondary action: primary text on a filled neutral surface. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = Theme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceHigh,
            contentColor = colors.text,
            disabledContainerColor = colors.surfaceHigh,
            disabledContentColor = colors.muted,
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
        color = Theme.colors.muted,
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
