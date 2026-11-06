package com.rehabtrainer.app.ui.screens

import com.rehabtrainer.app.data.session.parseRecordedAt
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RECORDED_AT_PATTERN = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/**
 * 把服务端回读的 ISO-8601 时间渲染为本机时区的 `MM-dd HH:mm`。
 * 解析失败时原样返回，不丢字段也不猜测。
 *
 * @param zone 仅测试注入，生产使用设备时区。
 */
internal fun formatIsoTimestamp(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (raw.isNullOrBlank()) return "时间未知"
    val instant = parseRecordedAt(raw) ?: return raw
    return instant.atZone(zone).format(RECORDED_AT_PATTERN)
}

/** 有效帧占比按百分比展示，保留 1 位小数（与记录卡片既有文案一致）。 */
internal fun formatPercent(ratio: Double): String =
    String.format(Locale.US, "%.1f", ratio * 100)
