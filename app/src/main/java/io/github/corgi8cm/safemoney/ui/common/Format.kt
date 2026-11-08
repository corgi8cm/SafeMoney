package io.github.corgi8cm.safemoney.ui.common

import io.github.corgi8cm.safemoney.domain.FORECAST_WINDOW_DAYS
import io.github.corgi8cm.safemoney.domain.Forecast
import io.github.corgi8cm.safemoney.domain.Progress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 7000 → "$7,000" */
fun formatMoney(amount: Long): String = "$" + String.format(Locale.US, "%,d", amount)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

fun LocalDate.display(): String = format(DATE_FORMAT)

/** 無條件捨去到小數一位，避免還沒達標就顯示 100.0%。例：1240/7000 → "17.7%" */
fun Progress.percentText(): String {
    val permille = saved * 1000 / target
    return "${permille / 10}.${permille % 10}%"
}

/** Material3 DatePicker 使用 UTC 午夜的毫秒值。 */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** 依手機時區換成當地日期。 */
fun Instant.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()

/** 11/05（四） */
fun LocalDate.shortWithWeekday(): String =
    "$monthValue/$dayOfMonth（${dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.TAIWAN)}）"

/** 日均金額：四捨五入到元，不到 1 元時顯示「不到 $1」。 */
fun formatDailyAverage(average: Double): String =
    if (average < 0.5) "不到 \$1" else formatMoney(Math.round(average))

/** 超過這麼多年就不顯示日期，只鼓勵多省一點。 */
private const val FORECAST_MAX_YEARS = 10L

/**
 * 預估回本日的說明文字。[subject] 例：「回本」、「全部回本」。已回本時回傳 null。
 */
fun Forecast.describe(today: LocalDate, subject: String = "回本"): String? = when (this) {
    Forecast.Completed -> null
    Forecast.NoRecentSavings -> "最近 $FORECAST_WINDOW_DAYS 天沒有紀錄，記一筆就能預估${subject}日"
    is Forecast.Estimate -> {
        val pace = "照最近 $windowDays 天的速度（日均 ${formatDailyAverage(dailyAverage)}）"
        if (date.isAfter(today.plusYears(FORECAST_MAX_YEARS))) {
            "$pace，要超過 $FORECAST_MAX_YEARS 年才能${subject}，再多省一點吧！"
        } else {
            "$pace，預計 ${date.display()} $subject"
        }
    }
}
