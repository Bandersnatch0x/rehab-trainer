package com.rehabtrainer.app.ui.screens

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFormattingTest {

    // 固定时区，避免测试结果随运行机器变化。
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun `naive utc from server is shown in local time`() {
        // 服务端 to_utc_naive 后回传的就是这种无偏移形式（UTC 02:00 → 本地 10:00）。
        assertEquals("11-07 10:00", formatRecordedAt("2026-11-07T02:00:00", shanghai))
    }

    @Test
    fun `offset and Z forms are accepted`() {
        assertEquals("11-07 10:00", formatRecordedAt("2026-11-07T02:00:00Z", shanghai))
        assertEquals("11-07 10:00", formatRecordedAt("2026-11-07T10:00:00+08:00", shanghai))
    }

    @Test
    fun `fractional seconds do not break parsing`() {
        assertEquals("11-07 10:00", formatRecordedAt("2026-11-07T02:00:00.123456", shanghai))
    }

    @Test
    fun `missing or unparsable time degrades without dropping the field`() {
        assertEquals("时间未知", formatRecordedAt(null, shanghai))
        assertEquals("时间未知", formatRecordedAt("   ", shanghai))
        assertEquals("昨天", formatRecordedAt("昨天", shanghai))
    }

    @Test
    fun `valid frame ratio renders as one decimal percent`() {
        assertEquals("98.4", formatPercent(0.984))
        assertEquals("100.0", formatPercent(1.0))
        assertEquals("0.0", formatPercent(0.0))
    }
}
