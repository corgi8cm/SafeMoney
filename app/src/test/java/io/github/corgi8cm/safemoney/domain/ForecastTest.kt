package io.github.corgi8cm.safemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ForecastTest {
    private val today = LocalDate.of(2026, 11, 8)

    private fun on(daysAgo: Long, amount: Long) = DatedAmount(today.minusDays(daysAgo), amount)

    @Test
    fun completedGoalNeedsNoForecast() {
        assertEquals(Forecast.Completed, forecastCompletion(0, listOf(on(0, 100)), today, today))
    }

    @Test
    fun noSavingsInWindowCannotForecast() {
        val old = listOf(on(40, 500))
        assertEquals(
            Forecast.NoRecentSavings,
            forecastCompletion(1_000, old, since = today.minusDays(60), today = today),
        )
        assertEquals(Forecast.NoRecentSavings, forecastCompletion(1_000, emptyList(), today, today))
    }

    @Test
    fun usesThirtyDayAverage() {
        // 30 天內共省 1500 → 日均 50；還差 1000 → 20 天
        val entries = listOf(on(0, 500), on(10, 500), on(29, 500), on(30, 9_999))
        val f = forecastCompletion(1_000, entries, since = today.minusDays(100), today = today)
        f as Forecast.Estimate
        assertEquals(30, f.windowDays)
        assertEquals(50.0, f.dailyAverage, 0.001)
        assertEquals(today.plusDays(20), f.date)
    }

    @Test
    fun newGoalOnlyCountsDaysSinceStart() {
        // 3 天前才開始，共 4 天省 200 → 日均 50；還差 120 → 進位成 3 天
        val entries = listOf(on(3, 100), on(1, 100))
        val f = forecastCompletion(120, entries, since = today.minusDays(3), today = today) as Forecast.Estimate
        assertEquals(4, f.windowDays)
        assertEquals(today.plusDays(3), f.date)
    }

    @Test
    fun backdatedSavingBeforeSinceExtendsWindow() {
        // 目標今天才建立，但補記了 4 天前的紀錄 → 期間 5 天
        val entries = listOf(on(4, 250))
        val f = forecastCompletion(500, entries, since = today, today = today) as Forecast.Estimate
        assertEquals(5, f.windowDays)
        assertEquals(today.plusDays(10), f.date)
    }

    @Test
    fun roundsUpPartialDay() {
        // 今天開始、省 100（1 天）→ 還差 101 需要 2 天
        val f = forecastCompletion(101, listOf(on(0, 100)), today, today) as Forecast.Estimate
        assertEquals(today.plusDays(2), f.date)
    }
}
