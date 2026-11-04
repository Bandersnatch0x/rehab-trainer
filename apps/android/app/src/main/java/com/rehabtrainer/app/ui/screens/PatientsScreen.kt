package com.rehabtrainer.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 患者：档案、H-Y 分期与分期确认状态（只读骨架）。 */
@Composable
fun PatientsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "患者",
        description = "查看患者档案、H-Y 分期与分期确认状态。",
        modifier = modifier
    )
}
