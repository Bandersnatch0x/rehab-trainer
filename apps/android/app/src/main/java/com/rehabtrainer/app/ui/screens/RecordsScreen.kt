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
import androidx.compose.ui.text.font.FontWeight
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
        // 页面标题由顶栏统一承担，页内不再重复大标题。
        // D21：记录、指标与模型版本均为 mock；接入真实训练记录 API 时本声明与 mock 一并移除。
        Text(
            text = "演示数据：当前为演示模式，下列训练记录、指标与模型版本均为示例数据，不是真实患者记录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Metrics Grid (结构化单行指标，解决 P1-4 标签粘连与颜色串项)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecordMetricRow(label = "完成次数", value = "$completedCount 次")
                    RecordMetricRow(label = "动作错误", value = "$errorCount 次")
                    RecordMetricRow(label = "中断次数", value = "$interruptionCount 次")
                    RecordMetricRow(
                        label = "有效帧占比",
                        value = "${effectiveFrameRate}%",
                        valueColor = if (effectiveFrameRate < 80f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Version Metadata (使用 onSurfaceVariant 满足 P1-12 对比度 >= 4.5:1)
            Text(
                text = "模型: $modelVersion · 指标: $algorithmVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordMetricRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
