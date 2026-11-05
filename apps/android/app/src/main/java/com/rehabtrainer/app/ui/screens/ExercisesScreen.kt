package com.rehabtrainer.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.core.training.TrainingAccessPolicy
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens

/**
 * 训练条目列表：按 4 大固定训练大类展示。
 *
 * D11：需要治疗师确认分期的条目在确认前禁用开始按钮；演示版没有分期数据来源，
 * 调用方默认传 `stageConfirmed = false`，按最保守处理。
 */
@Composable
fun ExercisesScreen(
    onSelectExercise: (String) -> Unit = {},
    stageConfirmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dimens = LocalElderlyDimens.current
    var showContactDialog by remember { mutableStateOf(false) }

    if (showContactDialog) {
        ContactTherapistDialog(onDismiss = { showContactDialog = false })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        Text(
            text = "康复训练条目",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "由康复治疗师制定处方。请在适宜体力下按顺序完成。",
            style = MaterialTheme.typography.bodyLarge
        )

        // Category 1: 节律步态 (RAS)
        ExerciseCategoryCard(
            categoryTitle = "节律步态训练 (RAS)",
            categoryDesc = "跟着舒适节拍踏步，帮助步伐更加连贯稳健。",
            exerciseName = "原地节律踏步训练",
            parameters = "推荐剂量：3 组 × 12 次 · 64 BPM 听觉节拍",
            suitabilityTag = "适宜阶段 · 自主完成",
            onStart = { onSelectExercise("EX-RAS-01") }
        )

        // Category 2: 牵伸放松
        ExerciseCategoryCard(
            categoryTitle = "牵伸放松训练",
            categoryDesc = "轻柔拉伸身体各部位，让全身动作更加舒展轻松。",
            exerciseName = "坐位双上肢躯干伸展",
            parameters = "推荐剂量：2 组 × 10 次 · 自主节律无需设备",
            suitabilityTag = "轻柔舒缓 · 自主完成",
            onStart = { onSelectExercise("EX-STRETCH-01") }
        )

        // Category 3: 肌力维持
        ExerciseCategoryCard(
            categoryTitle = "肌力维持训练",
            categoryDesc = "增强双腿站立力量，日常走路更有劲。",
            exerciseName = "下肢靠墙微蹲训练",
            parameters = "推荐剂量：2 组 × 8 次 · 需身旁有稳固扶手",
            suitabilityTag = "温和强化 · 家属陪同",
            onStart = { onSelectExercise("EX-STRENGTH-01") }
        )

        // Category 4: 姿势矫正（D11 高风险准入）
        ExerciseCategoryCard(
            categoryTitle = "姿势矫正训练",
            categoryDesc = "根据实时动作指引，保持挺拔舒展的站立姿态。",
            exerciseName = "站立位脊柱对齐矫正",
            parameters = "推荐剂量：3 组 × 10 次 · 端上实时姿态比对",
            suitabilityTag = "疑似 · 需分期确认准入",
            onStart = { onSelectExercise("EX-POSTURE-01") },
            requiresStageConfirmation = true,
            stageConfirmed = stageConfirmed,
            onContactTherapist = { showContactDialog = true }
        )
    }
}

@Composable
private fun ExerciseCategoryCard(
    categoryTitle: String,
    categoryDesc: String,
    exerciseName: String,
    parameters: String,
    suitabilityTag: String,
    onStart: () -> Unit,
    requiresStageConfirmation: Boolean = false,
    stageConfirmed: Boolean = false,
    onContactTherapist: () -> Unit = {}
) {
    val dimens = LocalElderlyDimens.current
    val canStart = TrainingAccessPolicy.canStart(requiresStageConfirmation, stageConfirmed)

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // 适宜性标签（整行自适应，防止 P1-1 竖排单字）
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = suitabilityTag,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Text(
                text = categoryDesc,
                style = MaterialTheme.typography.bodyMedium
            )

            // 参数胶囊（单行或结构化排布，彻底消除 P1-15 孤立「次」字折行）
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = parameters,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (requiresStageConfirmation && !stageConfirmed) {
                Text(
                    text = "分期状态：疑似（尚未经治疗师确认）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ElderlyButton(
                text = if (canStart) "开始此项训练" else "需治疗师确认分期",
                onClick = onStart,
                variant = ElderlyButtonVariant.Primary,
                enabled = canStart
            )

            if (!canStart) {
                Text(
                    text = "该训练属于需治疗师评估的高风险条目。为保证安全，分期确认前不能开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ElderlyButton(
                    text = "查看联系治疗师方式",
                    onClick = onContactTherapist,
                    variant = ElderlyButtonVariant.Secondary
                )
            }
        }
    }
}

@Composable
private fun ContactTherapistDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("联系康复治疗师") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("签约机构：市康复中心第三病区", style = MaterialTheme.typography.bodyLarge)
                Text("责任治疗师：李主任", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "请治疗师确认分期后，再开始「姿势矫正训练」。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
