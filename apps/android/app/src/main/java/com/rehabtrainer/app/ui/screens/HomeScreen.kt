package com.rehabtrainer.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 首页：适老化康复工作台。
 *
 * 布局策略：
 * - Compact / Medium 手机端：单列纵向大卡片流，防误触大间距。
 * - Expanded（大屏平板）：左右双列自适应（左操作与主行动点，右处方与安全状态）。
 */
@Composable
fun HomeScreen(
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val dimens = LocalElderlyDimens.current
    val contentModifier = modifier
        .fillMaxSize()
        .padding(dimens.screenPadding)

    if (widthSizeClass != WindowWidthSizeClass.Expanded) {
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
        ) {
            HomeStatusBanner()
            HomeQuickActions()
            HomeTodayPrescription()
            HomeSafetyGuide()
        }
    } else {
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                HomeStatusBanner()
                HomeQuickActions()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                HomeTodayPrescription()
                HomeSafetyGuide()
            }
        }
    }
}

@Composable
private fun HomeStatusBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = "患者状态：H-Y 2期（已确认）",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "经康复治疗师权威判定，准入节律步态训练。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun HomeQuickActions() {
    val dimens = LocalElderlyDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
        Text(
            text = "今日训练",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "在照护者监护下开始。感到头晕或肌肉酸痛请随时休息。",
            style = MaterialTheme.typography.bodyLarge
        )
        ElderlyButton(
            text = "开始今日训练（第 1/4 节）",
            onClick = { /* 接入训练流程 */ },
            icon = Icons.Filled.PlayArrow,
            variant = ElderlyButtonVariant.Primary
        )
        ElderlyButton(
            text = "查看完整处方条目",
            onClick = { /* 接入处方接口 */ },
            icon = Icons.Filled.List,
            variant = ElderlyButtonVariant.Secondary
        )
    }
}

@Composable
private fun HomeTodayPrescription() {
    val dimens = LocalElderlyDimens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
        ) {
            Text(
                text = "今日处方条目",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "训练大类：节律步态（RAS 引导）+ 姿势矫正",
                style = MaterialTheme.typography.bodyLarge
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "64 BPM", style = MaterialTheme.typography.headlineMedium)
                        Text(text = "听觉节拍", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "3 组 × 12 次", style = MaterialTheme.typography.headlineMedium)
                        Text(text = "剂量设定", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSafetyGuide() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "跌倒风险防护提醒",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "请穿防滑鞋，清理地面杂物，身旁备有稳固扶手座椅。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
