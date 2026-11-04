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
 * 训练条目列表：按 4 大固定训练大类（牵伸放松、肌力维持、节律步态、姿势矫正）展示，
 * 5 级仅宣教与转诊，不提供训练条目。
 */
@Composable
fun ExercisesScreen(
    onSelectExercise: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = LocalElderlyDimens.current

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
            categoryDesc = "利用固定听觉节拍引导步伐，预防冻结步态 (FoG)。",
            exerciseName = "原地节律踏步训练",
            parameters = "节拍: 64 BPM · 3 组 × 12 次",
            riskLevel = "低风险 (H-Y 2期适用)",
            onStart = { onSelectExercise("EX-RAS-01") }
        )

        // Category 2: 牵伸放松
        ExerciseCategoryCard(
            categoryTitle = "牵伸放松训练",
            categoryDesc = "缓解四肢齿轮样强直与晨僵，扩大关节活动度。",
            exerciseName = "坐位双上肢躯干伸展",
            parameters = "无节拍限制 · 2 组 × 10 次",
            riskLevel = "安全条目 (全分期适用)",
            onStart = { onSelectExercise("EX-STRETCH-01") }
        )

        // Category 3: 肌力维持
        ExerciseCategoryCard(
            categoryTitle = "肌力维持训练",
            categoryDesc = "维持双下肢支撑肌群力量，延缓肌少症。",
            exerciseName = "下肢靠墙微蹲训练",
            parameters = "需有稳固扶手 · 2 组 × 8 次",
            riskLevel = "中风险 (需照护者在场)",
            onStart = { onSelectExercise("EX-STRENGTH-01") }
        )

        // Category 4: 姿势矫正
        ExerciseCategoryCard(
            categoryTitle = "姿势矫正训练",
            categoryDesc = "通过关节角度与标准动作模板比对，改善前倾姿态。",
            exerciseName = "站立位脊柱对齐矫正",
            parameters = "端上实时姿态比对偏差输出",
            riskLevel = "需分期确认准入",
            onStart = { onSelectExercise("EX-POSTURE-01") }
        )
    }
}

@Composable
private fun ExerciseCategoryCard(
    categoryTitle: String,
    categoryDesc: String,
    exerciseName: String,
    parameters: String,
    riskLevel: String,
    onStart: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = riskLevel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = categoryDesc,
                style = MaterialTheme.typography.bodyMedium
            )

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

            ElderlyButton(
                text = "开始此项训练",
                onClick = onStart,
                variant = ElderlyButtonVariant.Primary
            )
        }
    }
}
