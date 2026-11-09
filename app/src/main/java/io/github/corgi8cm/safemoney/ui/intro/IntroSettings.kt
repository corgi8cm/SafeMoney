package io.github.corgi8cm.safemoney.ui.intro

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 記住使用者是否看過新手說明（只在第一次、還沒有任何目標時自動顯示）。 */
class IntroSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("intro", Context.MODE_PRIVATE)

    private val _seen = MutableStateFlow(prefs.getBoolean(KEY_SEEN, false))
    val seen: StateFlow<Boolean> = _seen.asStateFlow()

    fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
        _seen.value = true
    }

    private companion object {
        const val KEY_SEEN = "seen"
    }
}
