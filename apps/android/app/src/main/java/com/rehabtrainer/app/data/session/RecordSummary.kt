package com.rehabtrainer.app.data.session

import com.rehabtrainer.app.data.contract.SessionRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * 训练记录汇总。
 *
 * 全部由端上从记录本身派生，只做计数，不涉及任何临床结论或风险判定（D13 未签核前不得派生结论）。
 * 近期打卡统计只覆盖已取回的那一页记录（见 `limit`），不是全量历史。
 */
data class RecordSummary(
    val totalSets: Int,
    val totalReps: Int,
    val activeDays: Int,
    val streakDays: Int
) {
    companion object {
        val EMPTY = RecordSummary(totalSets = 0, totalReps = 0, activeDays = 0, streakDays = 0)
    }
}

object RecordSummaries {

    /**
     * @param today 由调用方给出，避免测试与跨时区场景下依赖系统时钟。
     */
    fun summarize(
        records: List<SessionRecord>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): RecordSummary {
        if (records.isEmpty()) return RecordSummary.EMPTY
        val days = records.mapNotNull { record ->
            parseRecordedAt(record.recordedAt)?.atZone(zone)?.toLocalDate()
        }.toSet()
        return RecordSummary(
            totalSets = records.size,
            totalReps = records.sumOf { it.completionCount ?: 0 },
            activeDays = days.size,
            streakDays = streak(days, today)
        )
    }

    /**
     * 连续打卡天数：从今天往前数；今天还没练时从昨天起算，避免白天显示的连续天数突然归零。
     */
    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (today in days) today else today.minusDays(1)
        var count = 0
        while (cursor in days) {
            count += 1
            cursor = cursor.minusDays(1)
        }
        return count
    }
}
