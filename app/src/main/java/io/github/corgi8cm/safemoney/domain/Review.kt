package io.github.corgi8cm.safemoney.domain

import java.time.LocalDate
import java.time.YearMonth

/** 回顧的期間：某個月或某一年。 */
sealed interface ReviewPeriod {
    val start: LocalDate
    val end: LocalDate
    /** 「2026 年 10 月」、「2026 年」 */
    val label: String
    /** 「上個月」、「去年」 */
    val previousLabel: String
    fun previous(): ReviewPeriod
    fun next(): ReviewPeriod
    fun contains(date: LocalDate): Boolean = date in start..end

    data class Month(val month: YearMonth) : ReviewPeriod {
        override val start: LocalDate get() = month.atDay(1)
        override val end: LocalDate get() = month.atEndOfMonth()
        override val label: String get() = "${month.year} 年 ${month.monthValue} 月"
        override val previousLabel: String get() = "上個月"
        override fun previous() = Month(month.minusMonths(1))
        override fun next() = Month(month.plusMonths(1))
    }

    data class Year(val year: Int) : ReviewPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year, 12, 31)
        override val label: String get() = "$year 年"
        override val previousLabel: String get() = "去年"
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
    }
}

/** 一筆紀錄在回顧裡需要的資料。 */
data class ReviewEntry(val date: LocalDate, val reason: String, val amount: Long)

/** 回本的目標與回本那天。 */
data class CompletedGoal(val name: String, val date: LocalDate)

data class PeriodReview(
    val period: ReviewPeriod,
    val total: Long,
    val count: Int,
    /** 有記錄的天數。 */
    val activeDays: Int,
    val previousTotal: Long,
    /** 最有效的原因（最多 3 個）。 */
    val topReasons: List<ReasonTotal>,
    /** 這段期間回本的目標，依回本先後。 */
    val completedGoals: List<String>,
    /** 省最多的一天；沒有紀錄時為 null。 */
    val bestDay: PeriodTotal?,
) {
    val isEmpty: Boolean get() = count == 0

    /** 「比上個月多 $300」；上一期沒有紀錄時為 null。 */
    fun comparisonText(format: (Long) -> String): String? {
        if (previousTotal == 0L) return null
        val diff = total - previousTotal
        return when {
            diff > 0 -> "比${period.previousLabel}多 ${format(diff)}"
            diff < 0 -> "比${period.previousLabel}少 ${format(-diff)}"
            else -> "跟${period.previousLabel}一樣"
        }
    }
}

fun buildReview(entries: List<ReviewEntry>, completed: List<CompletedGoal>, period: ReviewPeriod): PeriodReview {
    val inPeriod = entries.filter { period.contains(it.date) }
    val previous = period.previous()
    val byDay = inPeriod.groupBy { it.date }.map { (date, list) -> PeriodTotal(date, list.sumOf { it.amount }) }
    return PeriodReview(
        period = period,
        total = inPeriod.sumOf { it.amount },
        count = inPeriod.size,
        activeDays = byDay.size,
        previousTotal = entries.filter { previous.contains(it.date) }.sumOf { it.amount },
        topReasons = reasonBreakdown(inPeriod.map { ReasonAmount(it.reason, it.amount) }, top = Int.MAX_VALUE).take(3),
        completedGoals = completed.filter { period.contains(it.date) }.sortedBy { it.date }.map { it.name },
        bestDay = byDay.maxWithOrNull(compareBy<PeriodTotal> { it.amount }.thenByDescending { it.start }),
    )
}

/**
 * 首頁「上個月回顧」卡片：每月 1 號起顯示上個月的回顧，直到使用者按 ✕ 或下個月。
 * 上個月沒有紀錄就不顯示。
 */
fun monthlyCardMonth(today: LocalDate, dismissed: YearMonth?, hasRecordsIn: (YearMonth) -> Boolean): YearMonth? {
    val last = YearMonth.from(today).minusMonths(1)
    if (dismissed == last || !hasRecordsIn(last)) return null
    return last
}
