package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable

/** 契约 `NotificationDelivery`。 */
@Serializable
data class NotificationDelivery(
    val channel: DeliveryChannel,
    val status: DeliveryStatus,
    val deliveredAt: String
)

/** 契约 `Alert`。 */
@Serializable
data class Alert(
    val id: String,
    val patientId: String,
    val ruleCode: String,
    val level: AlertLevel,
    val triggeredAt: String,
    val status: AlertStatus,
    val deliveries: List<NotificationDelivery>? = null
)

/** `PATCH /alerts/{alertId}` 请求体（契约内联 schema）。 */
@Serializable
data class AlertUpdateRequest(
    val status: AlertUpdateStatus,
    val note: String? = null
)

/** 契约 `AlertPage`。 */
@Serializable
data class AlertPage(
    val items: List<Alert>,
    val nextCursor: String? = null
)
