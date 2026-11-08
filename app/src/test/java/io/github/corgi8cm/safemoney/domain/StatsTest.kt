package io.github.corgi8cm.safemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    // 2026/11/08 是星期日
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun dailyTotalsFillsGapsWithZeroOldestFirst() {
        val entries = listOf(
            DatedAmount(today, 60),
            DatedAmount(today, 40),
            DatedAmount(today.minusDays(2), 80),
            DatedAmount(today.minusDays(10), 999), // 超出範圍
        )
        val result = dailyTotals(entries, today, days = 3)
        assertEquals(
            listOf(
                PeriodTotal(today.minusDays(2), 80),
                PeriodTotal(today.minusDays(1), 0),
                PeriodTotal(today, 100),
            ),
            result,
        )
    }

    @Test
    fun weeksStartOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 2), today.weekStart())
        assertEquals(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2).weekStart())
    }

    @Test
    fun weeklyTotalsGroupsByWeek() {
        val entries = listOf(
            DatedAmount(LocalDate.of(2026, 11, 2), 10), // 本週一
            DatedAmount(today, 20), // 本週日
            DatedAmount(LocalDate.of(2026, 11, 1), 30), // 上週日
        )
        val result = weeklyTotals(entries, today, weeks = 2)
        assertEquals(
            listOf(
                PeriodTotal(LocalDate.of(2026, 10, 26), 30),
                PeriodTotal(LocalDate.of(2026, 11, 2), 30),
            ),
            result,
        )
    }

    @Test
    fun reasonBreakdownMergesTrimsAndGroupsOthers() {
        val items = listOf(
            ReasonAmount("飲料", 60),
            ReasonAmount(" 飲料 ", 60),
            ReasonAmount("外食", 200),
            ReasonAmount("", 30),
            ReasonAmount("計程車", 20),
            ReasonAmount("零食", 10),
        )
        val result = reasonBreakdown(items, top = 3)
        assertEquals(
            listOf(
                ReasonTotal("外食", 200),
                ReasonTotal("飲料", 120),
                ReasonTotal(NO_REASON_LABEL, 30),
                ReasonTotal(OTHER_REASONS_LABEL, 30),
            ),
            result,
        )
    }

    @Test
    fun reasonBreakdownWithinTopHasNoOthers() {
        val result = reasonBreakdown(listOf(ReasonAmount("飲料", 60)), top = 3)
        assertEquals(listOf(ReasonTotal("飲料", 60)), result)
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(3, currentStreak(dates, today))
        // 今天還沒記，從昨天算起不中斷
        assertEquals(2, currentStreak(listOf(today.minusDays(1), today.minusDays(2)), today))
        assertEquals(0, currentStreak(listOf(today.minusDays(2)), today))
    }
}
