package com.rehabtrainer.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 手机端抽屉：每个目的地是整行、最小 80dp 高的条目，避免 6 个底部标签在窄屏被压缩。
 */
@Composable
fun RehabDrawerContent(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val dimens = LocalElderlyDimens.current
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp)
        ) {
            Text(
                text = "康复训练",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(dimens.itemSpacing))
            // 训练页只能从条目列表或首页入口进入，不能从抽屉绕过分级准入。
            RehabDestination.entries
                .filterNot { it == RehabDestination.TRAINING }
                .forEach { destination ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .heightIn(min = dimens.minTouchTarget)
                )
            }
        }
    }
}

/** 平板端侧栏。 */
@Composable
fun RehabNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val dimens = LocalElderlyDimens.current
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(dimens.railWidth)
            .verticalScroll(rememberScrollState()),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        // 外层 Scaffold 已经消费系统栏 inset，这里置零避免重复留白。
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Spacer(modifier = Modifier.height(dimens.itemSpacing))
        RehabDestination.entries
            .filterNot { it == RehabDestination.TRAINING }
            .forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                alwaysShowLabel = true,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget)
            )
        }
    }
}
