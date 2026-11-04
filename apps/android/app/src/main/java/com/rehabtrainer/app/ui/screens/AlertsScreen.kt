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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 预警：风险预警列表、语音停止指引与随访处置。
 * 强调红色醒目警示与一键联系治疗师。
 */
@Composable
fun AlertsScreen(modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        Text(
            text = "风险预警中心",
            style = MaterialTheme.typography.headlineLarge
        )

        // Safety Stop Principle Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "紧急安全原则：如出现剧烈胸痛、呼吸困难或严重步态冻结，请立即停止训练并呼救！",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Critical Alert Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "严重预警 · 待处理",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "今日 09:20",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = "主观晨僵感连续 3 天加重",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "触发自评阈值规则 RULE-RIGIDITY-03，系统已自动向责任治疗师发送随访警报。",
                    style = MaterialTheme.typography.bodyLarge
                )

                ElderlyButton(
                    text = "📞 呼叫康复治疗师",
                    onClick = { /* 调起电话或语音服务 */ },
                    variant = ElderlyButtonVariant.Danger
                )
            }
        }

        // Secondary Info Alert
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
                    text = "提示 · 步态指标波动",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "昨日节律踏步对称性轻微下降，建议今日放慢踏步频率。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
