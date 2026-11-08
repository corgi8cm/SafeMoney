package io.github.corgi8cm.safemoney.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 圖表上的一根長條：從 [start] 起的一段期間（一天或一週）省下的總額。 */
data class PeriodTotal(val start: LocalDate, val amount: Long)

/** 一個省錢原因與它累計省下的金額。 */
data class ReasonTotal(val reason: String, val amount: Long)

/** 一筆省錢紀錄的原因與總金額。 */
data class ReasonAmount(val reason: String, val amount: Long)

const val NO_REASON_LABEL = "（沒有填原因）"
const val OTHER_REASONS_LABEL = "其他"

/** 最近 [days] 天（含今天）每天省下的總額，由舊到新，沒記錄的日子為 0。 */
fun dailyTotals(entries: List<DatedAmount>, today: LocalDate, days: Int = 14): List<PeriodTotal> {
    require(days > 0)
    val byDate = entries.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } }
    return (days - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        PeriodTotal(date, byDate[date] ?: 0)
    }
}

/** 一週從星期一開始。 */
fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** 最近 [weeks] 週（含本週）每週省下的總額，由舊到新。 */
fun weeklyTotals(entries: List<DatedAmount>, today: LocalDate, weeks: Int = 8): List<PeriodTotal> {
    require(weeks > 0)
    val byWeek = entries.groupBy { it.date.weekStart() }.mapValues { (_, v) -> v.sumOf { it.amount } }
    val thisWeek = today.weekStart()
    return (weeks - 1 downTo 0).map { back ->
        val start = thisWeek.minusWeeks(back.toLong())
        PeriodTotal(start, byWeek[start] ?: 0)
    }
}

/**
 * 各原因累計省下的金額，由多到少。超過 [top] 個原因時，其餘合併成「其他」放最後。
 * 原因前後空白不計、空白原因顯示為 [NO_REASON_LABEL]。
 */
fun reasonBreakdown(items: List<ReasonAmount>, top: Int = 5): List<ReasonTotal> {
    require(top > 0)
    val sorted = items
        .groupBy { it.reason.trim().ifEmpty { NO_REASON_LABEL } }
        .map { (reason, v) -> ReasonTotal(reason, v.sumOf { it.amount }) }
        .filter { it.amount > 0 }
        .sortedWith(compareByDescending<ReasonTotal> { it.amount }.thenBy { it.reason })
    if (sorted.size <= top) return sorted
    val rest = sorted.drop(top).sumOf { it.amount }
    return sorted.take(top) + ReasonTotal(OTHER_REASONS_LABEL, rest)
}

/**
 * 連續有省錢的天數，算到今天為止。今天還沒記也不算中斷（從昨天往回算）。
 */
fun currentStreak(dates: Collection<LocalDate>, today: LocalDate): Int {
    val set = dates.toHashSet()
    var day = if (today in set) today else today.minusDays(1)
    var streak = 0
    while (day in set) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}
