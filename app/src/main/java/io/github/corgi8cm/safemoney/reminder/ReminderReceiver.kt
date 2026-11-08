package io.github.corgi8cm.safemoney.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.corgi8cm.safemoney.SafeMoneyApp
import io.github.corgi8cm.safemoney.domain.reminderText
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * - 鬧鐘觸發：今天還沒記錄就發提醒，然後排隔天的
 * - 開機、改時間 / 時區、App 更新：鬧鐘會被系統清掉或失準，依設定重新排程
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SafeMoneyApp
        val config = app.reminderSettings.config.value
        when (intent.action) {
            ACTION_REMIND -> {
                if (!config.enabled) return
                val pending = goAsync()
                scope.launch {
                    try {
                        val text = reminderText(
                            recordedToday = app.repository.hasSavingOn(LocalDate.now()),
                            nearest = app.repository.nearestActiveGoal(),
                            formatMoney = ::formatMoney,
                        )
                        if (text != null) ReminderNotifier.show(app, text)
                    } finally {
                        app.reminderScheduler.apply(config)
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> app.reminderScheduler.apply(config)
        }
    }

    companion object {
        const val ACTION_REMIND = "io.github.corgi8cm.safemoney.action.REMIND"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
