package com.rehabtrainer.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 设置：字号缩放、语音提示、设备绑定与机构信息。 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "设置",
        description = "字体缩放、语音提示、设备绑定与机构信息。",
        modifier = modifier
    )
}
