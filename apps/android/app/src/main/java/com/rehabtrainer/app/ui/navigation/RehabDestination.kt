package com.rehabtrainer.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** 顶层路由。手机用抽屉，平板用侧栏，两处共用同一份目的地定义。 */
enum class RehabDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(route = "home", label = "首页", icon = Icons.Filled.Home),
    PATIENTS(route = "patients", label = "患者", icon = Icons.Filled.Person),
    TRAINING(route = "training", label = "训练", icon = Icons.Filled.PlayArrow),
    RECORDS(route = "records", label = "记录", icon = Icons.Filled.List),
    ALERTS(route = "alerts", label = "预警", icon = Icons.Filled.Notifications),
    SETTINGS(route = "settings", label = "设置", icon = Icons.Filled.Settings)
}
