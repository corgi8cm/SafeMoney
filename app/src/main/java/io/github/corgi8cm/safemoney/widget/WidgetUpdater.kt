package io.github.corgi8cm.safemoney.widget

import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.github.corgi8cm.safemoney.SafeMoneyApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 資料一變動就更新主畫面上的小工具。
 * 資料只會在 App 程序裡被修改（記錄、編輯、轉移、還原…），所以在程序存活時監聽資料庫就夠了。
 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private val started = AtomicBoolean(false)

    /** 可以重複呼叫，只會啟動一次（例如 Activity 重建時）。 */
    fun start(app: SafeMoneyApp) {
        if (!started.compareAndSet(false, true)) return
        app.appScope.launch {
            app.repository.observeGoals()
                .map(::widgetState)
                .catch { Log.w(TAG, "讀取資料失敗", it) }
                .distinctUntilChanged()
                .collectLatest {
                    delay(300) // 連續寫入（例如還原）時只更新一次
                    try {
                        val manager = GlanceAppWidgetManager(app)
                        if (manager.getGlanceIds(SafeMoneyWidget::class.java).isNotEmpty()) {
                            SafeMoneyWidget().updateAll(app)
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "更新小工具失敗", e)
                    }
                }
        }
    }
}
