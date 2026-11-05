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
import androidx.compose.material3.Switch
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
import com.rehabtrainer.app.data.local.AppPreferences
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 设置：字号缩放、语音节拍提示、手部震颤触控反馈、设备绑定与机构信息。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current
    val context = LocalContext.current

    var audioGuidanceEnabled by remember {
        mutableStateOf(AppPreferences.isAudioGuidanceEnabled(context))
    }
    var hapticFeedbackEnabled by remember { mutableStateOf(true) }
    var highContrastEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        Text(
            text = "系统与适老设置",
            style = MaterialTheme.typography.headlineLarge
        )

        // Accessibility Settings Card
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "视听与触控无障碍辅助",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "RAS 节拍语音与蜂鸣引导", style = MaterialTheme.typography.titleMedium)
                        Text(text = "训练中播放固定节拍声音与动作语音提示，辅助步态节律", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = audioGuidanceEnabled,
                        onCheckedChange = { enabled ->
                            audioGuidanceEnabled = enabled
                            AppPreferences.setAudioGuidanceEnabled(context, enabled)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "按键防颤振动确认", style = MaterialTheme.typography.titleMedium)
                        Text(text = "点击 80dp 主按钮时触发物理马达震动回馈", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = hapticFeedbackEnabled, onCheckedChange = { hapticFeedbackEnabled = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "WCAG AA 高对比度界面", style = MaterialTheme.typography.titleMedium)
                        Text(text = "锁定深海医疗蓝与高清晰字阶（默认常开）", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = highContrastEnabled, onCheckedChange = { highContrastEnabled = it })
                }
            }
        }

        // Institution & Binding Info
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
                    text = "所属机构与设备标识",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "签约机构：市康复中心第三病区（机构隔离 D10）",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "责任康复治疗师：李主任",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "系统版本：v1.0.0 (MediaPipe Lite + RAS Engine)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                ElderlyButton(
                    text = "解除设备绑定",
                    onClick = { /* 解绑 */ },
                    variant = ElderlyButtonVariant.Secondary
                )
            }
        }
    }
}
