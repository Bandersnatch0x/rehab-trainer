package com.rehabtrainer.app.data.session

import kotlinx.serialization.Serializable

/** 服务端训练方案中的一条训练项目（已合并条目详情与方案训练量）。 */
@Serializable
data class PlanExercise(
    val code: String,
    val label: String,
    val category: String,
    val sets: Int,
    val reps: Int,
    val bpm: Int,
    val restSeconds: Int,
    val videoUri: String? = null,
    val watermarkText: String? = null,
    val creditLine: String? = null,
    val riskLevel: String? = null
)

/** 一次成功同步后的本地快照；离线启动时直接读它。 */
@Serializable
data class SessionSnapshot(
    val accountId: String,
    val displayName: String,
    val orgName: String? = null,
    val patientId: String,
    val patientName: String,
    val prescriptionId: String? = null,
    val prescriptionVersion: String? = null,
    val plan: List<PlanExercise> = emptyList(),
    val loadedAtEpochMillis: Long = 0L
)