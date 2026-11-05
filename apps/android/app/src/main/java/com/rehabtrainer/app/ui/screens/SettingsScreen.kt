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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.data.local.AppPreferences
import com.rehabtrainer.app.data.local.SettingsRuntime
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.components.performHapticTap
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 设置：语音节拍提示、手部震颤触控反馈、高对比度主题、设备绑定与机构信息。
 *
 * 三个开关均读写 [AppPreferences]，真实生效且跨页面持久化：
 * - 语音节拍：训练页读取同一份偏好控制 TTS 与节拍音。
 * - 防颤振动：点击 [ElderlyButton] 时按该偏好触发马达回馈。
 * - 高对比度：通过 [SettingsRuntime] 通知主题切换配色。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current
    val context = LocalContext.current
    val view = LocalView.current

    var audioGuidanceEnabled by remember { mutableStateOf(AppPreferences.isAudioGuidanceEnabled(context)) }
    var hapticFeedbackEnabled by remember { mutableStateOf(AppPreferences.isHapticFeedbackEnabled(context)) }
    var highContrastEnabled by remember { mutableStateOf(AppPreferences.isHighContrastEnabled(context)) }
    var isBound by remember { mutableStateOf(AppPreferences.isDeviceBound(context)) }
    var showUnbindConfirm by remember { mutableStateOf(false) }

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

                SettingSwitchRow(
                    title = "RAS 节拍语音与蜂鸣引导",
                    description = "训练中播放固定节拍声音与动作语音提示，辅助步态节律",
                    checked = audioGuidanceEnabled,
                    onCheckedChange = { enabled ->
                        audioGuidanceEnabled = enabled
                        AppPreferences.setAudioGuidanceEnabled(context, enabled)
                    }
                )

                SettingSwitchRow(
                    title = "按键防颤振动确认",
                    description = "点击 80dp 主按钮时触发物理马达震动回馈",
                    checked = hapticFeedbackEnabled,
                    onCheckedChange = { enabled ->
                        hapticFeedbackEnabled = enabled
                        AppPreferences.setHapticFeedbackEnabled(context, enabled)
                        if (enabled) view.performHapticTap()
                    }
                )

                SettingSwitchRow(
                    title = "高对比度界面",
                    description = "加深次级文字与边框，白底正文对比度 ≥ 7:1（默认常开）",
                    checked = highContrastEnabled,
                    onCheckedChange = { enabled ->
                        highContrastEnabled = enabled
                        AppPreferences.setHighContrastEnabled(context, enabled)
                        SettingsRuntime.notifyChanged()
                    }
                )
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
                // D21：机构与治疗师为 mock 信息（上方三个开关是真实偏好，不属演示数据）；
                // 接入真实机构 API 后本声明随这两行 mock 一并移除。
                Text(
                    text = "演示数据：以下机构与治疗师信息为示例数据，不是真实机构信息。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isBound) {
                    ElderlyButton(
                        text = "解除设备绑定",
                        onClick = { showUnbindConfirm = true },
                        variant = ElderlyButtonVariant.Danger
                    )
                } else {
                    // 已解除：给出明确下一步，不留可点但无反应的按钮。
                    Text(
                        text = "设备已解除绑定",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "需重新绑定才能继续训练。演示版未开放自助绑定入口，请联系机构管理员获取绑定码。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showUnbindConfirm) {
        AlertDialog(
            onDismissRequest = { showUnbindConfirm = false },
            title = { Text("确认解除设备绑定？", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "确认解除后需要重新绑定才能继续训练。",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnbindConfirm = false
                        AppPreferences.clearDeviceBinding(context)
                        isBound = false
                    }
                ) {
                    Text("确认解除", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnbindConfirm = false }) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
