package com.rehabtrainer.app.data.session

import com.rehabtrainer.app.data.contract.SessionRecord
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSummaryTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 7)

    private fun record(recordedAt: String?, completionCount: Int? = 10) =
        SessionRecord(
            clientRecordId = "c-$recordedAt",
            recordedAt = recordedAt,
            completionCount = completionCount
        )

    /** 服务端回读为 UTC 时间；本地 11-07 08:00 对应 UTC 00:00，日期仍应是 11-07。 */
    private fun utcForLocalDay(day: LocalDate, hour: Int = 0) =
        day.atTime(hour, 0).atZone(zone).toInstant().toString().removeSuffix("Z")

    @Test
    fun `totals count sets and reps`() {
        val summary = RecordSummaries.summarize(
            records = listOf(
                record(utcForLocalDay(today), completionCount = 12),
                record(utcForLocalDay(today, 9), completionCount = 8)
            ),
            today = today,
            zone = zone
        )

        assertEquals(2, summary.totalSets)
        assertEquals(20, summary.totalReps)
        assertEquals(1, summary.activeDays)
        assertEquals(1, summary.streakDays)
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val summary = RecordSummaries.summarize(
            records = listOf(
                record(utcForLocalDay(today)),
                record(utcForLocalDay(today.minusDays(1))),
                record(utcForLocalDay(today.minusDays(2))),
                // 断档：再往前的记录不计入连续天数。
                record(utcForLocalDay(today.minusDays(5)))
            ),
            today = today,
            zone = zone
        )

        assertEquals(4, summary.activeDays)
        assertEquals(3, summary.streakDays)
    }

    @Test
    fun `streak survives a day that has not been trained yet`() {
        // 今天还没练，昨天与前天练过：连续天数仍应为 2，而不是 0。
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, RecordSummaries.streak(days, today))
    }

    @Test
    fun `streak is zero when the last training is older than yesterday`() {
        assertEquals(0, RecordSummaries.streak(setOf(today.minusDays(3)), today))
        assertEquals(0, RecordSummaries.streak(emptySet(), today))
    }

    @Test
    fun `unparsable timestamps are skipped rather than counted as today`() {
        val summary = RecordSummaries.summarize(
            records = listOf(record("not-a-time"), record(null)),
            today = today,
            zone = zone
        )

        assertEquals(2, summary.totalSets)
        assertEquals(0, summary.activeDays)
        assertEquals(0, summary.streakDays)
    }

    @Test
    fun `empty records give empty summary`() {
        assertEquals(
            RecordSummary.EMPTY,
            RecordSummaries.summarize(emptyList(), today, zone)
        )
    }
}
