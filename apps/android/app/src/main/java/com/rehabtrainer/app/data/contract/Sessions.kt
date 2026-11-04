package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 契约 `SessionRecordCreate`。
 *
 * 幂等键为 [clientRecordId]：设备离线时生成，重试与补传必须复用同一个值；
 * 服务端按该键去重，并返回逐条接收结果。
 */
@Serializable
data class SessionRecordCreate(
    val clientRecordId: String,
    val patientId: String,
    val prescriptionId: String,
    val recordedAt: String,
    val modelVersion: String,
    val modelSha256: String,
    val metricAlgorithmVersion: String,
    val thresholdConfigVersion: String,
    val recordedBy: String? = null,
    val checkedIn: Boolean? = null,
    val completionCount: Int? = null,
    val movementErrorCount: Int? = null,
    val interruptionCount: Int? = null,
    val validFrameRatio: Double? = null,
    val isDoubtful: Boolean? = null,
    val metrics: JsonObject? = null
)

/** `POST /sessions` 请求体；契约限制 1..100 条。 */
@Serializable
data class SessionBatchRequest(
    val records: List<SessionRecordCreate>
) {
    init {
        require(records.size in MIN_BATCH_SIZE..MAX_BATCH_SIZE) {
            "records 数量必须在 $MIN_BATCH_SIZE..$MAX_BATCH_SIZE 之间"
        }
    }

    companion object {
        const val MIN_BATCH_SIZE: Int = 1
        const val MAX_BATCH_SIZE: Int = 100
    }
}

/** 契约 `SessionBatchResult.results[]`。 */
@Serializable
data class SessionBatchItemResult(
    val clientRecordId: String,
    val accepted: Boolean,
    val recordId: String? = null,
    val error: ApiError? = null
)

/** 契约 `SessionBatchResult`。 */
@Serializable
data class SessionBatchResult(
    val results: List<SessionBatchItemResult>
)

/**
 * 契约 `SessionRecord`（服务端回读视图）。
 *
 * 契约未回传 `modelSha256`、`metrics`、`recordedBy`，此处不得自行扩展字段。
 */
@Serializable
data class SessionRecord(
    val id: String? = null,
    val clientRecordId: String? = null,
    val patientId: String? = null,
    val prescriptionId: String? = null,
    val recordedAt: String? = null,
    val checkedIn: Boolean? = null,
    val completionCount: Int? = null,
    val movementErrorCount: Int? = null,
    val interruptionCount: Int? = null,
    val validFrameRatio: Double? = null,
    val isDoubtful: Boolean? = null,
    val modelVersion: String? = null,
    val metricAlgorithmVersion: String? = null,
    val thresholdConfigVersion: String? = null
)

/** 契约 `SessionRecordPage`。 */
@Serializable
data class SessionRecordPage(
    val items: List<SessionRecord>,
    val nextCursor: String? = null
)
