package io.github.corgi8cm.safemoney.ui.common

import io.github.corgi8cm.safemoney.domain.Progress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
