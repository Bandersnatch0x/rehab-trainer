package com.rehabtrainer.app.data.session

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 解析服务端回读的 `recordedAt`。
 *
 * 服务端以 UTC-naive 存储（`to_utc_naive`），正常回传形如 `2026-11-07T02:00:00` 不带偏移，
 * 因此必须先按 `OffsetDateTime` 试，失败再按「无偏移即 UTC」解释。无法解析时返回 null，
 * 由调用方决定降级展示，不得猜时间。
 */
internal fun parseRecordedAt(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }
        .getOrNull()
}
