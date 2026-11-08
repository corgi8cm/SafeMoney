package io.github.corgi8cm.safemoney.reminder

import android.content.Context
import io.github.corgi8cm.safemoney.domain.DEFAULT_REMINDER_TIME
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

data class ReminderConfig(val enabled: Boolean = false, val time: LocalTime = DEFAULT_REMINDER_TIME)

/** 每日提醒的設定，存在 SharedPreferences（不放進資料庫，也不進備份檔）。 */
class ReminderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<ReminderConfig> = _config.asStateFlow()

    private fun read() = ReminderConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        time = LocalTime.of(
            prefs.getInt(KEY_HOUR, DEFAULT_REMINDER_TIME.hour),
            prefs.getInt(KEY_MINUTE, DEFAULT_REMINDER_TIME.minute),
        ),
    )

    fun update(config: ReminderConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_HOUR, config.time.hour)
            .putInt(KEY_MINUTE, config.time.minute)
            .apply()
        _config.value = config
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}
