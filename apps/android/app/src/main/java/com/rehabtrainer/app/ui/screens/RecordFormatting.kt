package com.rehabtrainer.app.ui.screens

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RECORDED_AT_PATTERN = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/**
 * 把服务端回读的 `recordedAt` 渲染为本机时区的 `MM-dd HH:mm`。
 *
 * 服务端以 UTC-naive 存储（`to_utc_naive`），因此正常回传形如 `2026-11-07T02:00:00`
 * 不带偏移；同时兼容带偏移或 `Z` 的输入。解析失败时原样返回，不丢字段也不猜测。
 *
 * @param zone 仅测试注入，生产使用设备时区。
 */
internal fun formatRecordedAt(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (raw.isNullOrBlank()) return "时间未知"
    val instant = runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }
        .getOrNull()
    return instant?.atZone(zone)?.format(RECORDED_AT_PATTERN) ?: raw
}

/** 有效帧占比按百分比展示，保留 1 位小数（与记录卡片既有文案一致）。 */
internal fun formatPercent(ratio: Double): String =
    String.format(Locale.US, "%.1f", ratio * 100)
