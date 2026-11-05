package com.rehabtrainer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.data.local.AppPreferences
import com.rehabtrainer.app.data.local.SettingsRuntime

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

/** 高对比度浅色：次级文字与边框压到近黑，白底上 ≥ 7:1。 */
private val HighContrastLightColors = LightColors.copy(
    surfaceVariant = HighContrastSurfaceVariantLight,
    onSurfaceVariant = HighContrastOnSurfaceVariantLight,
    outline = HighContrastOutlineLight,
    outlineVariant = HighContrastOutlineVariantLight
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

private val HighContrastDarkColors = DarkColors.copy(
    surfaceVariant = HighContrastSurfaceVariantDark,
    onSurfaceVariant = HighContrastOnSurfaceVariantDark,
    outline = HighContrastOutlineDark,
    outlineVariant = HighContrastOutlineVariantDark
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
 * @param highContrast 高对比度配色；为 null 时读取设置页持久化值，
 *   设置页切换后通过 [LocalSettingsRevision] 触发重组。
 */
@Composable
fun RehabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    highContrast: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val revision = SettingsRuntime.revision
    // 设置页写入偏好后 revision 自增，这里以它为 key 重新读取。
    val storedHighContrast = remember(revision) { AppPreferences.isHighContrastEnabled(context) }

    val effectiveHighContrast = highContrast ?: storedHighContrast
    val colorScheme = when {
        darkTheme && effectiveHighContrast -> HighContrastDarkColors
        darkTheme -> DarkColors
        effectiveHighContrast -> HighContrastLightColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalElderlyDimens provides ElderlyDimens()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = elderlyTypography(fontScale),
            shapes = ElderlyShapes,
            content = content
        )
    }
}
