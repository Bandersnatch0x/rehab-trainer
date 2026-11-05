package com.rehabtrainer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

enum class ElderlyButtonVariant {
    Primary,
    Secondary,
    Danger
}

/**
 * 适老化操作按钮：整行宽度，最小高度遵守适老化约束（主操作 ≥80dp，次级操作 ≥56dp），
 * 字号使用 [MaterialTheme.typography.labelLarge] 或 [MaterialTheme.typography.labelMedium]。
 *
 * 业务页不要直接使用 Material 默认无约束 Button，避免尺寸回退到 40dp 级而引起老年患者触击失败。
 */
@Composable
fun ElderlyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ElderlyButtonVariant = ElderlyButtonVariant.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    minHeight: androidx.compose.ui.unit.Dp? = null,
    contentPadding: PaddingValues? = null
) {
    val dimens = LocalElderlyDimens.current
    val effectiveMinHeight = minHeight ?: if (variant == ElderlyButtonVariant.Secondary) dimens.secondaryTouchTarget else dimens.minTouchTarget
    val defaultPadding = if (variant == ElderlyButtonVariant.Secondary) {
        PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    } else {
        PaddingValues(horizontal = 24.dp, vertical = 20.dp)
    }
    val effectivePadding = contentPadding ?: defaultPadding

    when (variant) {
        ElderlyButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = effectiveMinHeight),
                contentPadding = effectivePadding,
                shape = MaterialTheme.shapes.medium
            ) {
                ButtonInnerContent(text, icon)
            }
        }
        ElderlyButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = effectiveMinHeight),
                contentPadding = effectivePadding,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                ButtonInnerContent(text, icon)
            }
        }
        ElderlyButtonVariant.Danger -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = effectiveMinHeight),
                contentPadding = effectivePadding,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                ButtonInnerContent(text, icon)
            }
        }
    }
}

@Composable
private fun ButtonInnerContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge
    )
}
