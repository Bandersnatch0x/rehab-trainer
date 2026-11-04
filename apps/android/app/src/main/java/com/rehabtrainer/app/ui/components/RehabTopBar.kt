package com.rehabtrainer.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 自绘顶栏：标准 TopAppBar 高度装不下 80dp 的「菜单」按钮。
 *
 * 传 null 给 [onMenuClick] 时不显示菜单按钮（平板侧栏模式）。
 */
@Composable
fun RehabTopBar(
    title: String,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dimens = LocalElderlyDimens.current
    val menuClick = onMenuClick
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 96.dp)
                .padding(horizontal = dimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (menuClick != null) {
                Button(
                    onClick = menuClick,
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "菜单",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.width(dimens.itemSpacing))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
