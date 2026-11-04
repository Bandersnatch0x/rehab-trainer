package com.rehabtrainer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 适老化字号。
 *
 * 基准字号已高于 Material 默认值；[fontScale] 为应用内额外缩放系数
 * （后续由设置页写入），系统「字体大小」设置仍会在此之上继续生效。
 */
fun elderlyTypography(fontScale: Float = 1f): Typography {
    require(fontScale > 0f) { "fontScale 必须为正数" }

    fun scaled(baseSize: Float): TextUnit = (baseSize * fontScale).sp

    return Typography(
        displayLarge = TextStyle(
            fontSize = scaled(46f),
            lineHeight = scaled(56f),
            fontWeight = FontWeight.Bold
        ),
        headlineLarge = TextStyle(
            fontSize = scaled(36f),
            lineHeight = scaled(46f),
            fontWeight = FontWeight.Bold
        ),
        headlineMedium = TextStyle(
            fontSize = scaled(30f),
            lineHeight = scaled(40f),
            fontWeight = FontWeight.SemiBold
        ),
        titleLarge = TextStyle(
            fontSize = scaled(28f),
            lineHeight = scaled(38f),
            fontWeight = FontWeight.SemiBold
        ),
        titleMedium = TextStyle(
            fontSize = scaled(24f),
            lineHeight = scaled(34f),
            fontWeight = FontWeight.Medium
        ),
        bodyLarge = TextStyle(
            fontSize = scaled(22f),
            lineHeight = scaled(34f)
        ),
        bodyMedium = TextStyle(
            fontSize = scaled(20f),
            lineHeight = scaled(30f)
        ),
        labelLarge = TextStyle(
            fontSize = scaled(22f),
            lineHeight = scaled(30f),
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = TextStyle(
            fontSize = scaled(18f),
            lineHeight = scaled(26f),
            fontWeight = FontWeight.Medium
        )
    )
}
