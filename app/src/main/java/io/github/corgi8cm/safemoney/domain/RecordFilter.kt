package io.github.corgi8cm.safemoney.domain

import java.time.LocalDate
import java.time.YearMonth

/** 所有紀錄頁的篩選條件；null 代表不限。 */
data class RecordFilter(val month: YearMonth? = null, val reason: String? = null)

/** 原因前後空白不計，空白原因統一成 [NO_REASON_LABEL]（跟統計頁一致）。 */
fun normalizeReason(reason: String): String = reason.trim().ifEmpty { NO_REASON_LABEL }

fun <T> filterRecords(
    items: List<T>,
    filter: RecordFilter,
    date: (T) -> LocalDate,
    reason: (T) -> String,
): List<T> = items.filter { item ->
    (filter.month == null || YearMonth.from(date(item)) == filter.month) &&
        (filter.reason == null || normalizeReason(reason(item)) == filter.reason)
}

/** 篩選選單裡的原因：出現越多次越前面，次數相同依名稱。 */
fun reasonOptions(reasons: List<String>): List<String> =
    reasons.groupingBy(::normalizeReason).eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
