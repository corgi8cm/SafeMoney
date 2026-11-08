package io.github.corgi8cm.safemoney

import android.app.Application
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase

/** 手動相依注入：整個 App 共用一個 repository。 */
class SafeMoneyApp : Application() {
    val repository: SafeMoneyRepository by lazy {
        SafeMoneyRepository(SafeMoneyDatabase.get(this))
    }
}
