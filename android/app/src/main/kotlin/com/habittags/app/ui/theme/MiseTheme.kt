package com.habittags.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.habittags.app.R

/** Bricolage Grotesque, bundled as three static cuts (body, medium, display). */
val Mise = FontFamily(
    Font(R.font.mise_regular, FontWeight.Normal),
    Font(R.font.mise_medium, FontWeight.Medium),
    Font(R.font.mise_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Double, tracking: Double = 0.0) =
    TextStyle(fontFamily = Mise, fontWeight = weight, fontSize = size.sp, lineHeight = (size * line).sp, letterSpacing = tracking.em)

/** Three working sizes: display for names and countdowns, title, body; labels in sentence case. */
val MiseTypography = Typography(
    displayLarge = style(64, FontWeight.Bold, 1.0, -0.03),
    displayMedium = style(52, FontWeight.Bold, 1.0, -0.03),
    displaySmall = style(44, FontWeight.Bold, 1.02, -0.02),
    headlineLarge = style(36, FontWeight.Bold, 1.05, -0.02),
    headlineMedium = style(30, FontWeight.Bold, 1.1, -0.02),
    headlineSmall = style(26, FontWeight.Bold, 1.1, -0.01),
    titleLarge = style(22, FontWeight.Medium, 1.2),
    titleMedium = style(18, FontWeight.Medium, 1.25),
    titleSmall = style(16, FontWeight.Medium, 1.3),
    bodyLarge = style(17, FontWeight.Normal, 1.45),
    bodyMedium = style(16, FontWeight.Normal, 1.45),
    bodySmall = style(14, FontWeight.Normal, 1.4),
    labelLarge = style(16, FontWeight.Medium, 1.2),
    labelMedium = style(14, FontWeight.Medium, 1.2),
    labelSmall = style(13, FontWeight.Medium, 1.2),
)

private val Light = lightColorScheme(
    primary = MiseColors.ACCENT.toColor(),
    onPrimary = Color.White,
    primaryContainer = MiseColors.SURFACE.toColor(),
    onPrimaryContainer = MiseColors.INK.toColor(),
    secondary = MiseColors.INK.toColor(),
    onSecondary = MiseColors.BASE.toColor(),
    background = MiseColors.BASE.toColor(),
    onBackground = MiseColors.INK.toColor(),
    surface = MiseColors.BASE.toColor(),
    onSurface = MiseColors.INK.toColor(),
    surfaceVariant = MiseColors.SURFACE.toColor(),
    onSurfaceVariant = MiseColors.INK_SOFT.toColor(),
    surfaceContainer = MiseColors.SURFACE.toColor(),
    surfaceContainerLow = MiseColors.SURFACE.toColor(),
    surfaceContainerHigh = MiseColors.SURFACE.toColor(),
    surfaceContainerHighest = MiseColors.SURFACE.toColor(),
    secondaryContainer = MiseColors.SURFACE.toColor(),
    onSecondaryContainer = MiseColors.INK.toColor(),
    outline = MiseColors.INK_SOFT.toColor(),
    outlineVariant = MiseColors.SURFACE.toColor(),
    error = MiseColors.ACCENT.toColor(),
    errorContainer = SpaceColour.Tomato.tint.toColor(),
    onErrorContainer = MiseColors.INK.toColor(),
)

private val Dark = darkColorScheme(
    primary = MiseColors.ACCENT_ON_DARK.toColor(),
    onPrimary = MiseColors.BASE_DARK.toColor(),
    primaryContainer = MiseColors.SURFACE_DARK.toColor(),
    onPrimaryContainer = MiseColors.INK_DARK.toColor(),
    secondary = MiseColors.INK_DARK.toColor(),
    onSecondary = MiseColors.BASE_DARK.toColor(),
    background = MiseColors.BASE_DARK.toColor(),
    onBackground = MiseColors.INK_DARK.toColor(),
    surface = MiseColors.BASE_DARK.toColor(),
    onSurface = MiseColors.INK_DARK.toColor(),
    surfaceVariant = MiseColors.SURFACE_DARK.toColor(),
    onSurfaceVariant = MiseColors.INK_SOFT_DARK.toColor(),
    surfaceContainer = MiseColors.SURFACE_DARK.toColor(),
    surfaceContainerLow = MiseColors.SURFACE_DARK.toColor(),
    surfaceContainerHigh = MiseColors.SURFACE_DARK.toColor(),
    surfaceContainerHighest = MiseColors.SURFACE_DARK.toColor(),
    secondaryContainer = MiseColors.SURFACE_DARK.toColor(),
    onSecondaryContainer = MiseColors.INK_DARK.toColor(),
    outline = MiseColors.INK_SOFT_DARK.toColor(),
    outlineVariant = MiseColors.SURFACE_DARK.toColor(),
    error = MiseColors.ACCENT_ON_DARK.toColor(),
    errorContainer = SpaceColour.Tomato.tintDark.toColor(),
    onErrorContainer = MiseColors.INK_DARK.toColor(),
)

/** Whether the screen is dark; space cards pick their tint from it. */
val LocalDark = compositionLocalOf { false }

@Composable
fun MiseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = MiseTypography, content = content)
    }
}

/** Idle card background for a space colour, light or dark. */
@Composable
@ReadOnlyComposable
fun SpaceColour.tintColor(): Color = (if (LocalDark.current) tintDark else tint).toColor()

/** True when the user turned animations off (developer options or accessibility); motion is skipped. */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
