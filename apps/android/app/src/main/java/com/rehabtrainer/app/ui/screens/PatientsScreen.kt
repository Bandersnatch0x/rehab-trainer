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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.Patient
import com.rehabtrainer.app.data.session.AppData
import com.rehabtrainer.app.data.session.RecordSummary
import com.rehabtrainer.app.data.session.RecordSummaries
import com.rehabtrainer.app.ui.ExerciseRegistry
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant
import com.rehabtrainer.app.ui.theme.LocalElderlyDimens
import java.time.LocalDate

/**
 * 患者：真实档案与训练累计。
 *
 * 遵循 D20：患者可见页只呈现姓名/机构/训练累计与鼓励语，不呈现分级、风险或疾病相关文案。
 * 训练累计由本机从真实训练记录派生（[RecordSummaries]），不展示任何未落地的比率。
 */
@Composable
fun PatientsScreen(orgName: String? = null, modifier: Modifier = Modifier) {
    val dimens = LocalElderlyDimens.current
    val patientId = ExerciseRegistry.patientId
    var patient by remember { mutableStateOf<Patient?>(null) }
    var summary by remember { mutableStateOf<RecordSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(patientId) {
        error = null
        summary = null
        patient = null
        val id = patientId ?: return@LaunchedEffect
        when (val result = AppData.getPatient(id)) {
            is ApiResult.Success -> patient = result.value
            is ApiResult.Failure -> error = result.error.message
            null -> Unit
        }
        when (val result = AppData.listSessions(id, limit = RECORD_SAMPLE_LIMIT)) {
            is ApiResult.Success ->
                summary = RecordSummaries.summarize(result.value.items, LocalDate.now())
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        // 页面标题由顶栏统一承担，页内不再重复大标题。
        if (error != null) {
            Text(
                text = "档案读取失败：$error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when {
                            patient != null -> profileTitle(patient)
                            patientId != null -> if (error != null) "档案读取失败" else "档案读取中…"
                            else -> profileTitle(null)
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Text(
                    text = "所属机构：${orgName?.takeIf { it.isNotBlank() } ?: "—"}",
                    style = MaterialTheme.typography.bodyLarge
                )

                // Positive Training Progress & Encouragement (Replaces all disease-related wording per D20)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (summary == null) {
                            Text(
                                text = if (patientId == null) "未绑定患者，暂无训练累计。"
                                else "训练累计读取中…",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            val data = summary!!
                            Text(
                                text = "最近 $RECORD_SAMPLE_LIMIT 组完成训练：${data.totalSets} 组",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "最近 $RECORD_SAMPLE_LIMIT 组完成动作：${data.totalReps} 次",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "最近 $RECORD_SAMPLE_LIMIT 组内连续打卡：${data.streakDays} 天",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "最近 $RECORD_SAMPLE_LIMIT 组内有记录的训练日：${data.activeDays} 天",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
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
                    onClick = { /* 未开放：需机构管理员完成账号与角色绑定 */ },
                    variant = ElderlyButtonVariant.Secondary,
                    enabled = false
                )
                Text(
                    text = "需机构管理员协助绑定照护者，当前版本暂未开放。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 姓名 + 年龄；缺出生年份时只显示姓名，不猜年龄。 */
internal fun profileTitle(patient: Patient?, today: LocalDate = LocalDate.now()): String {
    val name = patient?.displayName?.takeIf { it.isNotBlank() } ?: "未绑定患者"
    val birthYear = patient?.birthYear ?: return name
    val age = today.year - birthYear
    return if (age in 0..MAX_PLAUSIBLE_AGE) "$name ($age 岁)" else name
}

/** 与契约 `limit` 上限一致；累计值只覆盖最近这些记录，不是全量历史。 */
private const val RECORD_SAMPLE_LIMIT = 100

private const val MAX_PLAUSIBLE_AGE = 130
