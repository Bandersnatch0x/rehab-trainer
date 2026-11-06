package com.rehabtrainer.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 设备绑定 / 内部账号登录。
 *
 * 患者设备默认走一次性绑定码（D9：一台设备长期绑定一位患者）；
 * 内部账号登录保留给治疗师调试与机构发码场景。
 */
@Composable
fun BindScreen(
    busy: Boolean,
    error: String?,
    onBind: (code: String) -> Unit,
    onLogin: (username: String, password: String) -> Unit,
    onSkipDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalElderlyDimens.current
    var useAccountLogin by remember { mutableStateOf(false) }
    var bindingCode by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        Text("绑定设备", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "输入机构提供的一次性绑定码，绑定后即可同步您的训练方案。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                if (!useAccountLogin) {
                    OutlinedTextField(
                        value = bindingCode,
                        onValueChange = { bindingCode = it.trim() },
                        label = { Text("绑定码") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onBind(bindingCode) },
                        enabled = !busy && bindingCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "正在绑定…" else "绑定设备")
                    }
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("账号") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onLogin(username, password) },
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "正在登录…" else "登录")
                    }
                }

                if (busy) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = { useAccountLogin = !useAccountLogin }) {
                    Text(if (useAccountLogin) "改用绑定码" else "使用内部账号登录")
                }
            }
        }

        TextButton(onClick = onSkipDemo) {
            Text("暂不绑定，使用演示数据")
        }
        Text(
            text = "演示数据仅用于功能演示，不代表真实患者或真实训练结果。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}