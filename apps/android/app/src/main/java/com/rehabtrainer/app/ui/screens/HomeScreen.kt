package com.rehabtrainer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 首页骨架。
 *
 * Compact / Medium 单列；Expanded（大屏平板）左右双列。后续训练计划、处方摘要从 API 接入。
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
            HomeQuickActions()
            HomeTodayPlan()
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
                HomeQuickActions()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                HomeTodayPlan()
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
            text = "开始前请确认患者与 H-Y 分期已由康复治疗师确认。",
            style = MaterialTheme.typography.bodyLarge
        )
        ElderlyButton(
            text = "开始训练",
            onClick = { /* 待接入训练流程 */ },
            icon = Icons.Filled.PlayArrow
        )
        ElderlyButton(
            text = "查看处方",
            onClick = { /* 待接入处方接口 */ },
            icon = Icons.Filled.List
        )
    }
}

@Composable
private fun HomeTodayPlan() {
    val dimens = LocalElderlyDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
        Text(
            text = "今日安排",
            style = MaterialTheme.typography.headlineLarge
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                Text(
                    text = "暂无已签发的处方",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "处方接口已接入客户端骨架，数据加载待实现。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
