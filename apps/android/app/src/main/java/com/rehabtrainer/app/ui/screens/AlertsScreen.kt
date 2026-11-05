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
    val context = LocalContext.current
    var dialFeedback by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        Text(
            text = "关怀提醒中心",
            style = MaterialTheme.typography.headlineLarge
        )

        // D21：预警规则、时间与随访文案均为 mock；接入真实预警数据时本声明与 mock 一并移除。
        Text(
            text = "演示数据：当前为演示模式，本页预警与随访内容均为示例数据，不是真实患者信息。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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

        // Caring Alert Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "温和提示 · 建议休息",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "今日 09:20",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "自测身体动作略感沉重",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "近几天身体可能有些疲惫，建议今日适当减少组数，多做舒展放松。",
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
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "提示 · 步态节奏建议",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "昨日节律踏步节奏偏快，建议今日跟随听觉节拍放慢踏步，保持平稳。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
