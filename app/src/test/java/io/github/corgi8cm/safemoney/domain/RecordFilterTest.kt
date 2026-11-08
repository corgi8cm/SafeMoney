package io.github.corgi8cm.safemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecordFilterTest {
    private data class Item(val date: LocalDate, val reason: String)

    private val items = listOf(
        Item(LocalDate.of(2026, 11, 8), "沒買飲料"),
        Item(LocalDate.of(2026, 11, 1), " 沒買飲料 "),
        Item(LocalDate.of(2026, 10, 31), "沒買飲料"),
        Item(LocalDate.of(2026, 11, 3), ""),
        Item(LocalDate.of(2026, 11, 4), "外食"),
    )

    private fun filter(f: RecordFilter) = filterRecords(items, f, { it.date }, { it.reason })

    @Test
    fun noFilterKeepsAll() {
        assertEquals(items, filter(RecordFilter()))
    }

    @Test
    fun filtersByMonthIncludingMonthEdges() {
        assertEquals(4, filter(RecordFilter(month = YearMonth.of(2026, 11))).size)
        assertEquals(listOf(items[2]), filter(RecordFilter(month = YearMonth.of(2026, 10))))
    }

    @Test
    fun filtersByNormalizedReasonAndCombinesWithMonth() {
        assertEquals(3, filter(RecordFilter(reason = "沒買飲料")).size)
        assertEquals(listOf(items[3]), filter(RecordFilter(reason = NO_REASON_LABEL)))
        assertEquals(
            listOf(items[0], items[1]),
            filter(RecordFilter(month = YearMonth.of(2026, 11), reason = "沒買飲料")),
        )
    }

    @Test
    fun reasonOptionsSortedByCount() {
        assertEquals(
            // 次數相同（各 1 次）時依名稱排序：「外」(U+5916) 在全形括號 (U+FF08) 前面
            listOf("沒買飲料", "外食", NO_REASON_LABEL),
            reasonOptions(items.map { it.reason }),
        )
    }
}
