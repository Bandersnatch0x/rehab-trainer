package com.rehabtrainer.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.session.RecordSummary
import com.rehabtrainer.app.data.session.RecordSummaries
import com.rehabtrainer.app.data.session.SessionSnapshot
import com.rehabtrainer.app.data.session.AppData
import com.rehabtrainer.app.ui.ExerciseRegistry
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens
import java.time.LocalDate

/**
 * 首页：适老化康复工作台。
 *
 * 布局策略：
 * - Compact / Medium 手机端：单列纵向大卡片流，防误触大间距。
 * - Expanded（大屏平板）：左右双列自适应（左操作与主行动点，右训练方案与安全状态）。
 *
 * 数据来源：[snapshot]（账号/患者/训练方案）。为 null 表示未绑定（演示模式），
 * 此时卡片明确标注为内置示例，不冒充服务端下发内容。
 */
@Composable
fun HomeScreen(
    widthSizeClass: WindowWidthSizeClass,
    snapshot: SessionSnapshot? = null,
    onStartTraining: () -> Unit = {},
    onOpenPrescription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = LocalElderlyDimens.current
    val contentModifier = modifier
        .fillMaxSize()
        .padding(dimens.screenPadding)
    val (planRows, isDemoPlan) = remember(snapshot) { planRowsOf(snapshot) }
    val summary = rememberRecordSummary(snapshot?.patientId)

    if (widthSizeClass != WindowWidthSizeClass.Expanded) {
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
        ) {
            HomeStatusBanner(displayName = snapshot?.displayName, summary = summary)
            HomeQuickActions(
                projectCount = planRows.size,
                onStartTraining = onStartTraining,
                onOpenPrescription = onOpenPrescription
            )
            HomeTodayPlan(rows = planRows, isDemoPlan = isDemoPlan)
            HomeSafetyGuide()
        }
    } else {
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                HomeStatusBanner(displayName = snapshot?.displayName, summary = summary)
                HomeQuickActions(
                    projectCount = planRows.size,
                    onStartTraining = onStartTraining,
                    onOpenPrescription = onOpenPrescription
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
            ) {
                HomeTodayPlan(rows = planRows, isDemoPlan = isDemoPlan)
                HomeSafetyGuide()
            }
        }
    }
}

/** 记录汇总只在绑定后拉取；失败或未绑定返回 null，调用方据此不展示任何数字。 */
@Composable
private fun rememberRecordSummary(patientId: String?): RecordSummary? {
    var summary by remember { mutableStateOf<RecordSummary?>(null) }
    LaunchedEffect(patientId) {
        summary = patientId?.let { id ->
            when (val result = AppData.listSessions(id, limit = RECORD_SAMPLE_LIMIT)) {
                is ApiResult.Success ->
                    RecordSummaries.summarize(result.value.items, LocalDate.now())
                else -> null
            }
        }
    }
    return summary
}

@Composable
private fun HomeStatusBanner(displayName: String?, summary: RecordSummary?) {
    val greeting = displayName?.takeIf { it.isNotBlank() }?.let { "你好，$it" } ?: "你好"
    // 连续天数只在真拿到记录时展示；没有记录就不报数字，避免编造坚持天数。
    val streak = summary?.streakDays?.takeIf { it > 0 }
    val title = if (streak != null) {
        "今日活力打卡 · 连续坚持 $streak 天"
    } else {
        "今日活力打卡"
    }
    val subtitle = if (streak != null) {
        "🦌 康康小鹿陪您练习：今天也是元气满满的一天，跟着节拍动一动～"
    } else {
        "🦌 康康小鹿陪您练习：今天还没有训练记录，动一动更舒畅～"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun HomeQuickActions(
    projectCount: Int,
    onStartTraining: () -> Unit,
    onOpenPrescription: () -> Unit
) {
    val dimens = LocalElderlyDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)) {
        Text(
            text = "今日训练",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "今日计划：共 $projectCount 个训练项目 · 建议在照护者监护下开始。感到头晕或肌肉酸痛请随时休息。",
            style = MaterialTheme.typography.bodyLarge
        )
        ElderlyButton(
            text = "开始今日训练",
            onClick = onStartTraining,
            icon = Icons.Filled.PlayArrow,
            variant = ElderlyButtonVariant.Primary
        )
        ElderlyButton(
            text = "查看完整训练项目",
            onClick = onOpenPrescription,
            icon = Icons.Filled.List,
            variant = ElderlyButtonVariant.Secondary
        )
    }
}

/** 首页训练项目行的展示数据；由服务端方案或内置示例映射而来，两者不混用。 */
private data class PlanRowData(val label: String, val volume: String, val cadence: String?)

/**
 * @return 行数据与「是否为内置示例」标记；未绑定（无服务端方案）时回退内置示例并标记。
 */
private fun planRowsOf(snapshot: SessionSnapshot?): Pair<List<PlanRowData>, Boolean> {
    val plan = snapshot?.plan.orEmpty()
    if (plan.isNotEmpty()) {
        return plan.map { item ->
            PlanRowData(
                label = item.label,
                volume = "${item.sets} 组 × ${item.reps} 次",
                cadence = if (item.bpm > 0) "${item.bpm} BPM" else null
            )
        } to false
    }
    // 内置示例沿用旧版 target 文案（如「目标 12 次 · 100 BPM」），不臆造节拍值。
    val fallback = ExerciseRegistry.exercises.map { exercise ->
        PlanRowData(label = exercise.name, volume = exercise.target, cadence = null)
    }
    return fallback to true
}

@Composable
private fun HomeTodayPlan(rows: List<PlanRowData>, isDemoPlan: Boolean) {
    val dimens = LocalElderlyDimens.current
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
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing)
        ) {
            Text(
                text = "今日训练项目",
                style = MaterialTheme.typography.titleLarge
            )
            if (isDemoPlan) {
                // 未绑定时的内置示例：明确标注，不冒充机构下发的训练方案。
                Text(
                    text = "演示模式：未绑定患者，以下为内置示例项目，不是机构下发的训练方案。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rows.forEach { row ->
                        PlanRow(
                            label = row.label,
                            value = row.volume,
                            secondary = row.cadence
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(label: String, value: String, secondary: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HomeSafetyGuide() {
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
                text = "居家运动安全提醒",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "请穿防滑软底鞋，清理周围地面杂物，身旁备有稳固扶手座椅。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** 与契约 `limit` 上限一致；首页统计只覆盖最近这些记录。 */
private const val RECORD_SAMPLE_LIMIT = 100
