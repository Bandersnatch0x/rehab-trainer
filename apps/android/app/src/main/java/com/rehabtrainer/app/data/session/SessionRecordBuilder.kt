package com.rehabtrainer.app.data.session

import com.rehabtrainer.app.core.training.ModelFingerprint
import com.rehabtrainer.app.data.contract.SessionRecordCreate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 一组训练结束时的客观计数（全部来自端上会话，不推测临床结论）。 */
data class SetOutcome(
    val exerciseCode: String,
    val setIndex: Int,
    val totalSets: Int,
    val completionCount: Int,
    val movementErrorCount: Int,
    val interruptionCount: Int,
    val validFrameRatio: Double?,
    val recordedAtIso: String,
    val clientRecordId: String
)

object SessionRecordBuilder {

    fun build(
        patientId: String,
        prescriptionId: String,
        outcome: SetOutcome,
        modelSha256: String
    ): SessionRecordCreate = SessionRecordCreate(
        clientRecordId = outcome.clientRecordId,
        patientId = patientId,
        prescriptionId = prescriptionId,
        recordedAt = outcome.recordedAtIso,
        modelVersion = ModelFingerprint.MODEL_VERSION,
        modelSha256 = modelSha256,
        metricAlgorithmVersion = ModelFingerprint.METRIC_ALGORITHM_VERSION,
        thresholdConfigVersion = ModelFingerprint.THRESHOLD_CONFIG_VERSION,
        checkedIn = outcome.completionCount > 0,
        completionCount = outcome.completionCount,
        movementErrorCount = outcome.movementErrorCount,
        interruptionCount = outcome.interruptionCount,
        validFrameRatio = outcome.validFrameRatio,
        metrics = buildJsonObject {
            put("exerciseCode", JsonPrimitive(outcome.exerciseCode))
            put("setIndex", JsonPrimitive(outcome.setIndex))
            put("totalSets", JsonPrimitive(outcome.totalSets))
        }
    )

    /** 有效帧占比：没有任何有效帧时返回 null，由服务端按缺失处理。 */
    fun validFrameRatio(validFrames: Int, totalFrames: Int): Double? =
        if (totalFrames <= 0) null else (validFrames.toDouble() / totalFrames).coerceIn(0.0, 1.0)

    fun emptyMetrics(): JsonObject = buildJsonObject { }
}