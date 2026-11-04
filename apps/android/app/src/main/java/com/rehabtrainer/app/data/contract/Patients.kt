package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable

/** 契约 `PatientCreate`。 */
@Serializable
data class PatientCreate(
    val displayName: String,
    val birthYear: Int? = null,
    val note: String? = null
)

/**
 * 契约 `Patient`。
 *
 * 时间为 ISO-8601 date-time 字符串；Android 端暂不引入额外日期类型，避免与生成物冲突。
 */
@Serializable
data class Patient(
    val id: String,
    val orgId: String,
    val displayName: String,
    val hyStage: Int,
    val stageConfirmationStatus: StageConfirmationStatus,
    val status: PatientStatus,
    val birthYear: Int? = null,
    val stageConfirmedBy: String? = null,
    val stageConfirmedAt: String? = null,
    val fallRisk: RiskLevel? = null,
    val symptoms: List<String>? = null,
    val createdAt: String? = null
)

/** 契约 `PatientPage`。 */
@Serializable
data class PatientPage(
    val items: List<Patient>,
    val nextCursor: String? = null
)
