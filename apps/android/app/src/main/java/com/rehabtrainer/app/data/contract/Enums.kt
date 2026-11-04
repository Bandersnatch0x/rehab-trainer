package com.rehabtrainer.app.data.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 契约枚举的手写镜像：取值与 `packages/api-contract/openapi.yaml` 逐字一致。
 *
 * 说明：
 * - 生成客户端落地前（见 `packages/api-client-kt/README.md`），本包是契约类型的唯一临时宿主；
 *   业务代码与 API 层共用这里的类型，禁止在其他包复制同名枚举或数据类。
 * - `wireValue` 同时服务 JSON（经 @SerialName）与 Retrofit 查询参数（经 toString 覆写）。
 */
@Serializable
enum class PatientStatus(val wireValue: String) {
    @SerialName("active")
    ACTIVE("active"),

    @SerialName("inactive")
    INACTIVE("inactive");

    override fun toString(): String = wireValue
}

@Serializable
enum class StageConfirmationStatus(val wireValue: String) {
    @SerialName("confirmed")
    CONFIRMED("confirmed"),

    @SerialName("suspected")
    SUSPECTED("suspected");

    override fun toString(): String = wireValue
}

@Serializable
enum class ExerciseCategory(val wireValue: String) {
    @SerialName("stretching-relaxation")
    STRETCHING_RELAXATION("stretching-relaxation"),

    @SerialName("strength-maintenance")
    STRENGTH_MAINTENANCE("strength-maintenance"),

    @SerialName("rhythmic-gait")
    RHYTHMIC_GAIT("rhythmic-gait"),

    @SerialName("postural-correction")
    POSTURAL_CORRECTION("postural-correction");

    override fun toString(): String = wireValue
}

@Serializable
enum class RiskLevel(val wireValue: String) {
    @SerialName("low")
    LOW("low"),

    @SerialName("medium")
    MEDIUM("medium"),

    @SerialName("high")
    HIGH("high");

    override fun toString(): String = wireValue
}

@Serializable
enum class AlertLevel(val wireValue: String) {
    @SerialName("info")
    INFO("info"),

    @SerialName("warning")
    WARNING("warning"),

    @SerialName("critical")
    CRITICAL("critical");

    override fun toString(): String = wireValue
}

@Serializable
enum class AlertStatus(val wireValue: String) {
    @SerialName("open")
    OPEN("open"),

    @SerialName("acknowledged")
    ACKNOWLEDGED("acknowledged"),

    @SerialName("followed_up")
    FOLLOWED_UP("followed_up"),

    @SerialName("closed")
    CLOSED("closed");

    override fun toString(): String = wireValue
}

/** `PATCH /alerts/{alertId}` 请求体只允许三态；`open` 由服务端产生。 */
@Serializable
enum class AlertUpdateStatus(val wireValue: String) {
    @SerialName("acknowledged")
    ACKNOWLEDGED("acknowledged"),

    @SerialName("followed_up")
    FOLLOWED_UP("followed_up"),

    @SerialName("closed")
    CLOSED("closed");

    override fun toString(): String = wireValue
}

@Serializable
enum class PublicationStatus(val wireValue: String) {
    @SerialName("draft")
    DRAFT("draft"),

    @SerialName("published")
    PUBLISHED("published"),

    @SerialName("retired")
    RETIRED("retired");

    override fun toString(): String = wireValue
}

@Serializable
enum class PrescriptionStatus(val wireValue: String) {
    @SerialName("draft")
    DRAFT("draft"),

    @SerialName("active")
    ACTIVE("active"),

    @SerialName("expired")
    EXPIRED("expired");

    override fun toString(): String = wireValue
}

@Serializable
enum class SupportLevel(val wireValue: String) {
    @SerialName("unsupported-standing")
    UNSUPPORTED_STANDING("unsupported-standing"),

    @SerialName("supported-standing")
    SUPPORTED_STANDING("supported-standing"),

    @SerialName("seated")
    SEATED("seated");

    override fun toString(): String = wireValue
}

@Serializable
enum class DeliveryChannel(val wireValue: String) {
    @SerialName("in_app_popup")
    IN_APP_POPUP("in_app_popup"),

    @SerialName("voice_prompt")
    VOICE_PROMPT("voice_prompt"),

    @SerialName("push_notification")
    PUSH_NOTIFICATION("push_notification"),

    @SerialName("audit_log")
    AUDIT_LOG("audit_log");

    override fun toString(): String = wireValue
}

@Serializable
enum class DeliveryStatus(val wireValue: String) {
    @SerialName("pending")
    PENDING("pending"),

    @SerialName("sent")
    SENT("sent"),

    @SerialName("failed")
    FAILED("failed"),

    @SerialName("fallback")
    FALLBACK("fallback");

    override fun toString(): String = wireValue
}
