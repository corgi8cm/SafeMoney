package io.github.corgi8cm.safemoney.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 某一天省下的金額（一筆紀錄或其中分給某目標的部分）。 */
data class DatedAmount(val date: LocalDate, val amount: Long)

/** 依最近的省錢速度推算何時回本。 */
sealed interface Forecast {
    /** 已經回本，不用推算。 */
    data object Completed : Forecast

    /** 最近沒有任何省錢紀錄，速度為 0，無法推算。 */
    data object NoRecentSavings : Forecast

    /**
     * @param date 預計回本日
     * @param dailyAverage 計算期間的平均每日省下金額
     * @param windowDays 計算期間的天數（最多 [FORECAST_WINDOW_DAYS] 天）
     */
    data class Estimate(val date: LocalDate, val dailyAverage: Double, val windowDays: Int) : Forecast
}

const val FORECAST_WINDOW_DAYS = 30

/**
 * 用最近 [windowDays] 天（含今天）的平均每日省錢金額推算回本日。
 *
 * 剛開始記錄不到 [windowDays] 天時，只用「開始記錄那天」到今天計算，
 * 避免新目標被前面沒記錄的空白天數拉低速度。開始那天取 [since]（例如目標建立日）
 * 與最早一筆紀錄中較早的一天。
 */
fun forecastCompletion(
    remaining: Long,
    entries: List<DatedAmount>,
    since: LocalDate,
    today: LocalDate,
    windowDays: Int = FORECAST_WINDOW_DAYS,
): Forecast {
    require(windowDays > 0) { "windowDays must be positive" }
    if (remaining <= 0) return Forecast.Completed

    val start = minOf(since, entries.minOfOrNull { it.date } ?: since)
    val windowStart = maxOf(start, today.minusDays(windowDays - 1L)).let { minOf(it, today) }
    val days = ChronoUnit.DAYS.between(windowStart, today) + 1
    val sum = entries.filter { it.date in windowStart..today }.sumOf { it.amount }
    if (sum <= 0) return Forecast.NoRecentSavings

    // 無條件進位：還差 1 元也要再多一天
    val daysNeeded = (remaining * days + sum - 1) / sum
    return Forecast.Estimate(
        date = today.plusDays(daysNeeded),
        dailyAverage = sum.toDouble() / days,
        windowDays = days.toInt(),
    )
}
