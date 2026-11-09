package io.github.corgi8cm.safemoney.domain

import io.github.corgi8cm.safemoney.data.db.RecurringSaving
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 某個定期省錢設定在某一天到期、還沒記下來。 */
data class PendingRecurring(val rule: RecurringSaving, val date: LocalDate)

/** 定期省錢的日期計算（不碰資料庫，方便測試）。 */
object Recurrence {
    const val EVERY_DAY = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKENDS = 0b1100000

    private val NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun includes(days: Int, day: DayOfWeek): Boolean = days and bit(day) != 0

    fun toggle(days: Int, day: DayOfWeek): Int = days xor bit(day)

    fun dayName(day: DayOfWeek): String = NAMES[day.value - 1]

    /** 每天、平日、週末，或「每週一、三、五」。 */
    fun describe(days: Int): String = when (days and EVERY_DAY) {
        EVERY_DAY -> "每天"
        WEEKDAYS -> "平日"
        WEEKENDS -> "週末"
        0 -> "沒有選日子"
        else -> "每週" + DayOfWeek.entries.filter { includes(days, it) }.joinToString("、") { dayName(it) }
    }

    /** 到 [today]（包含）為止，這個設定到期但還沒記下來的日子，由舊到新。 */
    fun pendingDates(rule: RecurringSaving, today: LocalDate): List<LocalDate> {
        val from = maxOf(rule.startDate, rule.handledThrough?.plusDays(1) ?: rule.startDate)
        val to = rule.endDate?.let { minOf(it, today) } ?: today
        if (from > to || rule.days and EVERY_DAY == 0) return emptyList()
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .filter { includes(rule.days, it.dayOfWeek) }
            .toList()
    }

    /** 所有設定到期的日子，依日期、再依設定建立的先後排序。 */
    fun pending(rules: List<RecurringSaving>, today: LocalDate): List<PendingRecurring> =
        rules.flatMap { rule -> pendingDates(rule, today).map { PendingRecurring(rule, it) } }
            .sortedWith(compareBy<PendingRecurring> { it.date }.thenBy { it.rule.createdAt }.thenBy { it.rule.uid })

    /** 結束日已經過了。 */
    fun isEnded(rule: RecurringSaving, today: LocalDate): Boolean = rule.endDate != null && rule.endDate < today

    /** 自動記下的紀錄用固定的編號：兩支手機同步時，同一天只會有一筆。 */
    fun savingUid(ruleUid: String, date: LocalDate): String = "recurring-$ruleUid-$date"

    /**
     * 自動記下的紀錄，建立與修改時間都用「那一天一開始」：
     * 兩支手機各自記下時內容一樣；使用者刪掉後，另一支手機晚一點才記下也不會讓它又出現。
     */
    fun recordedAt(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()

    /**
     * 新增或修改設定時檢查輸入；不合法時丟 [IllegalArgumentException]（訊息可直接顯示）。
     * 開始日不能在今天以前；[allowPastStart] 給「已經開始的設定」修改其他欄位用。
     */
    fun validate(
        reason: String,
        amount: Long,
        days: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
        today: LocalDate,
        allowPastStart: Boolean = false,
        maxReasonLength: Int = 40,
    ) {
        require(reason.isNotBlank()) { "請輸入省什麼（例：不買咖啡）" }
        require(reason.trim().length <= maxReasonLength) { "原因最多 $maxReasonLength 個字" }
        require(amount > 0) { "每次省下的金額必須大於 0" }
        require(days and EVERY_DAY != 0) { "至少要選一天" }
        require(allowPastStart || startDate >= today) { "開始日不能早於今天" }
        require(endDate == null || endDate >= startDate) { "結束日不能早於開始日" }
        require(endDate == null || endDate >= today) { "結束日不能早於今天" }
    }
}
