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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.core.contact.DialResult
import com.rehabtrainer.app.core.contact.TherapistDialer
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.Alert
import com.rehabtrainer.app.data.contract.AlertLevel
import com.rehabtrainer.app.data.contract.AlertStatus
import com.rehabtrainer.app.data.session.AppData
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 预警：真实预警事件列表、语音停止指引与一键联系治疗师。
 *
 * 数据来源 `GET /alerts`。规则文案只做「哪一类提醒」的中性映射，
 * 不在此页派生临床结论：阈值与处置路径待专业签核（P1-2/P1-4），未签核前自动预警保持关闭。
 */
@Composable
fun AlertsScreen(patientId: String? = null, modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current
    val context = LocalContext.current
    var dialFeedback by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<AlertsUiState>(AlertsUiState.Loading) }

    LaunchedEffect(patientId) {
        state = when {
            patientId == null -> AlertsUiState.Unbound
            else -> when (val result = AppData.listAlerts(patientId)) {
                null -> AlertsUiState.Unbound
                is ApiResult.Success -> AlertsUiState.Loaded(result.value.items)
                is ApiResult.Failure -> AlertsUiState.Failed(result.error.message)
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
        // Safety Stop Principle Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "运动安全建议：练习时请量力而行，身旁备有座椅，感到疲劳随时休息！",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        when (val current = state) {
            AlertsUiState.Loading -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }

            AlertsUiState.Unbound -> Notice(
                "未绑定患者，暂无预警信息。绑定后可查看本页提醒。"
            )

            is AlertsUiState.Failed -> Notice("预警读取失败：${current.message}")

            is AlertsUiState.Loaded -> {
                if (current.alerts.isEmpty()) {
                    Notice("当前没有待处理的提醒。")
                } else {
                    current.alerts.forEach { alert -> AlertCard(alert) }
                }
            }
        }

        // Therapist Call (always available, independent of alert state)
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
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "需要帮助？",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "练习中如有不适，请先停下休息，再联系您的康复治疗师。",
                    style = MaterialTheme.typography.bodyLarge
                )
                ElderlyButton(
                    text = "📞 呼叫康复治疗师",
                    onClick = {
                        when (val resolved = TherapistDialer.currentNumber()) {
                            is DialResult.Ready -> {
                                val opened = TherapistDialer.dial(context, resolved.number)
                                dialFeedback = if (opened) {
                                    "已打开拨号盘，请按拨号键呼叫。"
                                } else {
                                    "本机没有可用的拨号应用，请联系机构。"
                                }
                            }
                            DialResult.NotConfigured -> {
                                dialFeedback = "未配置联系电话，请联系机构。"
                            }
                        }
                    },
                    variant = ElderlyButtonVariant.Primary
                )
                if (dialFeedback.isNotEmpty()) {
                    Text(
                        text = dialFeedback,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private sealed interface AlertsUiState {
    data object Loading : AlertsUiState
    data object Unbound : AlertsUiState
    data class Failed(val message: String) : AlertsUiState
    data class Loaded(val alerts: List<Alert>) : AlertsUiState
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AlertCard(alert: Alert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.5.dp, levelColor(alert.level))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(text = levelLabel(alert.level), container = levelColor(alert.level))
                Text(
                    text = formatIsoTimestamp(alert.triggeredAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = ruleLabel(alert.ruleCode),
                style = MaterialTheme.typography.titleLarge
            )
            // 规则代码原样保留，便于与机构/治疗师核对，不做二次解释。
            Text(
                text = "提醒类型：${alert.ruleCode} · 状态：${statusLabel(alert.status)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Badge(text: String, container: androidx.compose.ui.graphics.Color) {
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun levelColor(level: AlertLevel): androidx.compose.ui.graphics.Color = when (level) {
    AlertLevel.INFO -> MaterialTheme.colorScheme.secondaryContainer
    AlertLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    AlertLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
}

private fun levelLabel(level: AlertLevel): String = when (level) {
    AlertLevel.INFO -> "提示"
    AlertLevel.WARNING -> "警示"
    AlertLevel.CRITICAL -> "紧急"
}

/** 未知规则码原样回显，绝不因为映射表没收录就把提醒藏起来。 */
internal fun ruleLabel(ruleCode: String): String = when (ruleCode) {
    "PAIN_PERSISTENT" -> "身体不适持续提醒"
    "RIGIDITY_WORSENING" -> "肢体僵硬变化提醒"
    "CRAMP_RECURRENT" -> "肌肉痉挛提醒"
    "FALL_RECURRENT" -> "跌倒风险提醒"
    else -> ruleCode
}

private fun statusLabel(status: AlertStatus): String = when (status) {
    AlertStatus.OPEN -> "待处理"
    AlertStatus.ACKNOWLEDGED -> "已确认"
    AlertStatus.FOLLOWED_UP -> "已随访"
    AlertStatus.CLOSED -> "已关闭"
}
