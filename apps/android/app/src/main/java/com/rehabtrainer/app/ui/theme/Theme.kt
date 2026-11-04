package com.rehabtrainer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = OnBrandBlue,
    primaryContainer = BrandBlueContainer,
    onPrimaryContainer = OnBrandBlueContainer,
    secondary = AccentGreen,
    onSecondary = OnAccentGreen,
    secondaryContainer = AccentGreenContainer,
    onSecondaryContainer = OnAccentGreenContainer,
    tertiary = WarningAmber,
    onTertiary = OnWarningAmber,
    tertiaryContainer = WarningContainer,
    onTertiaryContainer = OnWarningContainer,
    error = DangerRed,
    onError = OnDangerRed,
    errorContainer = DangerContainer,
    onErrorContainer = OnDangerContainer,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = OnBrandBlueDark,
    primaryContainer = BrandBlueContainerDark,
    onPrimaryContainer = OnBrandBlueContainerDark,
    secondary = AccentGreenLight,
    onSecondary = OnAccentGreenDark,
    secondaryContainer = AccentGreenContainerDark,
    onSecondaryContainer = OnAccentGreenContainerDark,
    tertiary = WarningAmberLight,
    onTertiary = OnWarningAmberDark,
    tertiaryContainer = WarningContainerDark,
    onTertiaryContainer = OnWarningContainerDark,
    error = DangerRedLight,
    onError = OnDangerRedDark,
    errorContainer = DangerContainerDark,
    onErrorContainer = OnDangerContainerDark,
    background = DarkPaper,
    onBackground = DarkInk,
    surface = DarkPaper,
    onSurface = DarkInk,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

private val ElderlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 适老化主题入口。
 *
 * @param fontScale 应用内字号缩放，默认 1.0；后续由设置页持久化后传入。
 */
@Composable
fun RehabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalElderlyDimens provides ElderlyDimens()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = elderlyTypography(fontScale),
            shapes = ElderlyShapes,
            content = content
        )
    }
}
