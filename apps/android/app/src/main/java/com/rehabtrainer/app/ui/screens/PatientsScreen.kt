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
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 患者：查看患者档案、H-Y 分期与分期确认状态（遵守 D10 与 D11 裁决）。
 */
@Composable
fun PatientsScreen(modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        Text(
            text = "患者档案",
            style = MaterialTheme.typography.headlineLarge
        )

        // Profile Overview Card
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "张建国 (72岁)",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "活力达人 🌟",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Text(
                    text = "所属机构：市康复中心第三病区",
                    style = MaterialTheme.typography.bodyLarge
                )

                // Positive Training Progress & Encouragement (Replaces all disease-related wording per D20)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "累计完成训练：36 次 【演示数据】",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "连续健康打卡：12 天 【演示数据】",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "本周完成率：96% (坚持就是胜利～) 【演示数据】",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "🌟 鼓励语：脚步越来越轻盈，动一动更舒畅，为您点赞！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Caregiver Proxy Card
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "照护者代操作管理",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "当前为「本人直接操作」。如按键操作有困难或需家属协助，可授权照护人代为执行。",
                    style = MaterialTheme.typography.bodyLarge
                )
                ElderlyButton(
                    text = "绑定照护者账号",
                    onClick = { /* 接入绑定流程 */ },
                    variant = ElderlyButtonVariant.Secondary
                )
            }
        }
    }
}
