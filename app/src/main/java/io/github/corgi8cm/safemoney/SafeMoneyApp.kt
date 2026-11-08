package io.github.corgi8cm.safemoney

import android.app.Application
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.reminder.ReminderScheduler
import io.github.corgi8cm.safemoney.reminder.ReminderSettings

/** 手動相依注入：整個 App 共用一個 repository 與提醒設定。 */
class SafeMoneyApp : Application() {
    val repository: SafeMoneyRepository by lazy {
        SafeMoneyRepository(SafeMoneyDatabase.get(this))
    }

    val reminderSettings: ReminderSettings by lazy { ReminderSettings(this) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(this) }
}
