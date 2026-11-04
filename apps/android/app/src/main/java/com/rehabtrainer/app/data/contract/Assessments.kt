package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `POST /patients/{patientId}/stage-confirmations` 请求体（契约内联 schema）。 */
@Serializable
data class StageConfirmationCreate(
    val stage: Int,
    val confirmedAt: String,
    val note: String? = null
)

/** 契约 `StageConfirmation`。 */
@Serializable
data class StageConfirmation(
    val patientId: String,
    val stage: Int,
    val status: StageConfirmationStatus,
    val confirmedBy: String,
    val confirmedAt: String,
    val note: String? = null
)

/** 契约 `AssessmentCreate`；`answers` 为自由结构。 */
@Serializable
data class AssessmentCreate(
    val instrument: String,
    val assessedAt: String,
    val score: Double? = null,
    val answers: JsonObject? = null,
    val assessedBy: String? = null
)

/** 契约 `Assessment`。 */
@Serializable
data class Assessment(
    val id: String,
    val patientId: String,
    val instrument: String,
    val assessedAt: String,
    val assessedBy: String,
    val score: Double? = null,
    val riskLevel: RiskLevel? = null
)
