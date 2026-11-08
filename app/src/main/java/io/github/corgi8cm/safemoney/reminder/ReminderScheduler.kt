package io.github.corgi8cm.safemoney.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.corgi8cm.safemoney.domain.nextReminderAt
import java.time.ZonedDateTime

/**
 * 用 AlarmManager 排下一次提醒，觸發後再排隔天的（見 [ReminderReceiver]）。
 *
 * 使用不精準的 setAndAllowWhileIdle：不需要「精確鬧鐘」權限，省電模式下也會觸發，
 * 只是可能晚幾分鐘，對每日提醒來說沒問題。
 */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 依設定排程或取消。 */
    fun apply(config: ReminderConfig, now: ZonedDateTime = ZonedDateTime.now()) {
        if (config.enabled) schedule(config, now) else cancel()
    }

    private fun schedule(config: ReminderConfig, now: ZonedDateTime) {
        val at = nextReminderAt(now, config.time)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pendingIntent())
    }

    private fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 1
    }
}
