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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.SessionRecord
import com.rehabtrainer.app.data.session.AppData
import com.rehabtrainer.app.ui.ExerciseRegistry
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens
import kotlinx.coroutines.launch

/**
 * 记录：真实训练记录卡片流。
 *
 * 数据来源为 `GET /sessions`（服务端回读视图），契约只回传时间与客观计数，
 * 不含动作名称与 `metrics`，因此卡片只展示确实存在的字段。
 */
@Composable
fun RecordsScreen(modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current
    val patientId = ExerciseRegistry.patientId
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RecordsUiState>(RecordsUiState.Loading) }
    var pendingUploads by remember { mutableStateOf(0) }
    var heldUploads by remember { mutableStateOf(0) }

    LaunchedEffect(patientId) {
        // 进入页面先补传离线积压，再回读，使记录页成为自愈点。
        AppData.flushPending()
        pendingUploads = AppData.pendingCount()
        heldUploads = AppData.heldCount()
        state = when {
            patientId == null -> RecordsUiState.Unbound
            else -> when (val result = AppData.listSessions(patientId)) {
                null -> RecordsUiState.Unbound
                is ApiResult.Success -> RecordsUiState.Loaded(result.value.items)
                is ApiResult.Failure -> RecordsUiState.Failed(result.error.message)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        // 页面标题由顶栏统一承担，页内不再重复大标题。
        when (val current = state) {
            RecordsUiState.Loading -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }

            RecordsUiState.Unbound -> Notice(
                "未绑定患者，暂无训练记录。绑定后可查看已上传的记录。"
            )

            is RecordsUiState.Failed -> Notice("训练记录读取失败：${current.message}")

            is RecordsUiState.Loaded -> {
                if (current.records.isEmpty()) {
                    Notice("暂无训练记录。完成一组训练后，记录会显示在这里。")
                } else {
                    current.records.forEach { record -> RecordCard(record) }
                }
            }
        }

        PendingUploadNotice(
            pending = pendingUploads,
            held = heldUploads,
            onRetryHeld = {
                scope.launch {
                    AppData.retryHeld()
                    pendingUploads = AppData.pendingCount()
                    heldUploads = AppData.heldCount()
                }
            }
        )
    }
}

private sealed interface RecordsUiState {
    data object Loading : RecordsUiState
    data object Unbound : RecordsUiState
    data class Failed(val message: String) : RecordsUiState
    data class Loaded(val records: List<SessionRecord>) : RecordsUiState
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 离线积压提示：有未上传记录时明确告知，避免患者以为记录丢失。 */
@Composable
private fun PendingUploadNotice(pending: Int, held: Int, onRetryHeld: () -> Unit) {
    if (pending <= 0) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "本机还有 $pending 条记录待上传，联网后会自动补传。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        if (held > 0) {
            Text(
                text = "其中 $held 条被服务器拒绝或归属不明，需要人工处理。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetryHeld) {
                Text("重试这些记录")
            }
        }
    }
}

@Composable
private fun RecordCard(record: SessionRecord) {
    val dimens = LocalElderlyDimens.current
    val isSuspicious = record.isDoubtful == true
    val ratio = record.validFrameRatio

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
                    text = formatIsoTimestamp(record.recordedAt),
                    style = MaterialTheme.typography.titleMedium
                )
                DoubtBadge(record.isDoubtful)
            }

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
                    RecordMetricRow(label = "完成次数", value = record.completionCount?.let { "$it 次" } ?: "—")
                    RecordMetricRow(label = "动作错误", value = record.movementErrorCount?.let { "$it 次" } ?: "—")
                    RecordMetricRow(label = "中断次数", value = record.interruptionCount?.let { "$it 次" } ?: "—")
                    // 无有效帧时契约回传 null；不得把它渲染成 0% 造成误读。
                    val ratioValue = ratio?.let { "${formatPercent(it)}%" } ?: "—"
                    RecordMetricRow(
                        label = "有效帧占比",
                        value = ratioValue,
                        valueColor = if (ratio != null && ratio < VALID_FRAME_WARN_RATIO) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // Version Metadata (使用 onSurfaceVariant 满足 P1-12 对比度 >= 4.5:1)
            Text(
                text = "模型: ${record.modelVersion ?: "—"} · 指标: ${record.metricAlgorithmVersion ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 三态展示：合格 / 存疑 / 未知；`null` 时不得冒充合格。 */
@Composable
private fun DoubtBadge(isDoubtful: Boolean?) {
    val (label, container, content) = when (isDoubtful) {
        true -> Triple(
            "已存疑（质检未过）",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        false -> Triple(
            "无存疑（质检合格）",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        null -> Triple(
            "存疑状态未知",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(color = container, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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

private const val VALID_FRAME_WARN_RATIO = 0.8
