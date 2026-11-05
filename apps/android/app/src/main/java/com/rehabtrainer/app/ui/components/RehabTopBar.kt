package com.rehabtrainer.app.ui.components

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 自绘顶栏：支持手机竖屏、横屏与平板侧栏自适应。
 * 在手机横屏下自动压缩为紧凑高度 (52dp)，避免挤占画面垂直空间 (P0-1)。
 */
@Composable
fun RehabTopBar(
    title: String,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dimens = LocalElderlyDimens.current
    val menuClick = onMenuClick
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isPhoneLandscape = isLandscape && !isTablet

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
                .heightIn(min = if (isPhoneLandscape) 52.dp else 72.dp)
                .padding(horizontal = if (isPhoneLandscape) 12.dp else dimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (menuClick != null) {
                Surface(
                    onClick = menuClick,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(if (isPhoneLandscape) 44.dp else 56.dp)
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "菜单",
                            modifier = Modifier.size(if (isPhoneLandscape) 26.dp else 32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(if (isPhoneLandscape) 8.dp else dimens.itemSpacing))
            }
            Text(
                text = title,
                style = if (isPhoneLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
