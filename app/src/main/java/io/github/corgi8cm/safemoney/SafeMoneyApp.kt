package io.github.corgi8cm.safemoney

import android.app.Application
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.diagnostics.CrashLog
import io.github.corgi8cm.safemoney.reminder.ReminderScheduler
import io.github.corgi8cm.safemoney.reminder.ReminderSettings
import io.github.corgi8cm.safemoney.sync.GoogleDriveAuth
import io.github.corgi8cm.safemoney.sync.SyncManager
import io.github.corgi8cm.safemoney.ui.intro.IntroSettings
import io.github.corgi8cm.safemoney.ui.splash.SplashSettings
import io.github.corgi8cm.safemoney.ui.stats.ReviewCardSettings
import io.github.corgi8cm.safemoney.update.UpdateChecker
import io.github.corgi8cm.safemoney.update.UpdateFeedProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 手動相依注入：整個 App 共用一個 repository 與提醒設定。 */
class SafeMoneyApp : Application() {
    /** 跟著 App 程序存活的背景工作（例如更新小工具）。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: SafeMoneyRepository by lazy {
        SafeMoneyRepository(SafeMoneyDatabase.get(this))
    }

    val reminderSettings: ReminderSettings by lazy { ReminderSettings(this) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(this) }

    val introSettings: IntroSettings by lazy { IntroSettings(this) }

    val splashSettings: SplashSettings by lazy { SplashSettings(this) }

    val reviewCardSettings: ReviewCardSettings by lazy { ReviewCardSettings(this) }

    val driveAuth: GoogleDriveAuth by lazy { GoogleDriveAuth(this) }

    val syncManager: SyncManager by lazy { SyncManager(this, repository, driveAuth, appScope) }

    val updateChecker: UpdateChecker by lazy {
        UpdateChecker(this, BuildConfig.VERSION_NAME, UpdateFeedProvider.feed)
    }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
