package com.rehabtrainer.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 适老化尺寸约束。
 *
 * [minTouchTarget] 是硬约束：主要操作按钮与导航条目不得小于 80dp，
 * 用于减少老年用户与帕金森患者的精细点击失败。
 */
data class ElderlyDimens(
    val minTouchTarget: Dp = 80.dp,
    val secondaryTouchTarget: Dp = 56.dp,
    val screenPadding: Dp = 24.dp,
    val sectionSpacing: Dp = 28.dp,
    val itemSpacing: Dp = 16.dp,
    val cardCornerRadius: Dp = 16.dp,
    val buttonCornerRadius: Dp = 16.dp,
    val railWidth: Dp = 112.dp
)

val LocalElderlyDimens = staticCompositionLocalOf { ElderlyDimens() }
