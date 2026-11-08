package io.github.corgi8cm.safemoney.domain

import java.time.LocalTime
import java.time.ZonedDateTime

/** 預設晚上 9 點提醒。 */
val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(21, 0)

/**
 * 下一次提醒的時間：今天的 [time] 還沒到就是今天，否則是明天。
 * 用 [ZonedDateTime] 計算，日光節約時間切換時仍落在當地的 [time]（遇到不存在的時刻會往後順延）。
 */
fun nextReminderAt(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
}

/** 提醒通知的內容。 */
data class ReminderText(val title: String, val body: String)

/** 最接近回本的進行中目標，用來讓提醒更有動力。 */
data class NearestGoal(val name: String, val remaining: Long)

/**
 * 今天已經記過就不打擾（回傳 null）；否則依最接近回本的目標產生提醒文字。
 * [formatMoney] 由呼叫端提供，避免 domain 依賴 UI 的格式化。
 */
fun reminderText(
    recordedToday: Boolean,
    nearest: NearestGoal?,
    formatMoney: (Long) -> String,
): ReminderText? {
    if (recordedToday) return null
    val body = if (nearest != null) {
        "「${nearest.name}」還差 ${formatMoney(nearest.remaining)}，記下今天少花的錢吧！"
    } else {
        "今天有少花什麼嗎？記一筆吧！"
    }
    return ReminderText(title = "今天省了什麼？", body = body)
}
