package com.rehabtrainer.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 记录：训练记录卡片流。
 * 遵循 D14 与 T7：展示动作错误次数、有效帧占比、算法版本与存疑标记。
 */
@Composable
fun RecordsScreen(modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        Text(
            text = "训练历史记录",
            style = MaterialTheme.typography.headlineLarge
        )

        // Valid Record 1
        RecordCard(
            dateText = "今日 10:15",
            exerciseTitle = "节律性听觉刺激踏步训练",
            completedCount = 36,
            errorCount = 1,
            interruptionCount = 0,
            effectiveFrameRate = 98.4f,
            isSuspicious = false,
            modelVersion = "MediaPipe-Lite-v1.2",
            algorithmVersion = "Metric-Gait-v2.0"
        )

        // Suspicious Record 2
        RecordCard(
            dateText = "昨日 15:30",
            exerciseTitle = "坐位上肢躯干伸展",
            completedCount = 20,
            errorCount = 4,
            interruptionCount = 2,
            effectiveFrameRate = 71.2f,
            isSuspicious = true,
            modelVersion = "MediaPipe-Lite-v1.2",
            algorithmVersion = "Metric-Gait-v2.0"
        )
    }
}

@Composable
private fun RecordCard(
    dateText: String,
    exerciseTitle: String,
    completedCount: Int,
    errorCount: Int,
    interruptionCount: Int,
    effectiveFrameRate: Float,
    isSuspicious: Boolean,
    modelVersion: String,
    algorithmVersion: String
) {
    val dimens = LocalElderlyDimens.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.5.dp,
            if (isSuspicious) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    color = if (isSuspicious) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (isSuspicious) "已存疑（质检未过）" else "无存疑（质检合格）",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSuspicious) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = exerciseTitle,
                style = MaterialTheme.typography.titleLarge
            )

            // Metrics Grid
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "完成次数: $completedCount 次", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "动作错误: $errorCount 次", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "中断次数: $interruptionCount 次", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "有效帧占比: ${effectiveFrameRate}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (effectiveFrameRate < 80f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Version Metadata
            Text(
                text = "模型版本: $modelVersion · 指标版本: $algorithmVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
