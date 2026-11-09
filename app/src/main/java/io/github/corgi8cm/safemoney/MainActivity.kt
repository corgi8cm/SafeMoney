package io.github.corgi8cm.safemoney

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.corgi8cm.safemoney.ui.QuickActions
import io.github.corgi8cm.safemoney.ui.SafeMoneyNavHost
import io.github.corgi8cm.safemoney.ui.splash.QuoteSplash
import io.github.corgi8cm.safemoney.ui.splash.animationsDisabled
import io.github.corgi8cm.safemoney.ui.theme.SafeMoneyTheme
import io.github.corgi8cm.safemoney.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 從桌面捷徑或小工具帶進來、還沒處理的動作（例如直接記一筆）。 */
    private var pendingAction by mutableStateOf<String?>(null)

    /** 正在播開場動畫時的句子；null 表示沒有在播。 */
    private var splashQuote by mutableStateOf<String?>(null)

    /** App 退到背景的時間（開機後經過的毫秒），用來判斷回來時要不要再播開場動畫。 */
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 畫面重建（例如旋轉）時不要重複執行捷徑動作
        if (savedInstanceState == null) pendingAction = QuickActions.actionOf(intent)
        // 打開 App 時播開場動畫（畫面旋轉等重建時不播）
        if (savedInstanceState == null) maybeShowSplash()
        // 強制停止 App 會清掉鬧鐘；每次開啟時依設定補排一次（已排的會被覆蓋，不會重複）
        (application as SafeMoneyApp).let { it.reminderScheduler.apply(it.reminderSettings.config.value) }
        // 資料只會在 App 開著時變動，開啟時開始監聽並更新主畫面小工具
        WidgetUpdater.start(application as SafeMoneyApp)
        QuickActions.publishShortcuts(this)
        (application as SafeMoneyApp).let { app -> app.appScope.launch { app.updateChecker.checkIfDue() } }
        setContent {
            SafeMoneyTheme {
                Box(Modifier.fillMaxSize()) {
                    // 首頁（或捷徑要去的「記一筆」）在動畫底下先準備好，播完就直接看到
                    SafeMoneyNavHost(
                        pendingAction = pendingAction,
                        onPendingActionHandled = { pendingAction = null },
                    )
                    splashQuote?.let { quote ->
                        key(quote) {
                            val animate = remember { !animationsDisabled(this@MainActivity) }
                            QuoteSplash(quote = quote, animate = animate, onDone = { splashQuote = null })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 離開一陣子再回來也算「打開」；只是切去別的 App 一下（或選檔案、分享）就不播
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt >= SPLASH_AGAIN_AFTER_MS) maybeShowSplash()
        // 有開同步時：開始監聽資料變動，距離上次同步太久就同步一次
        (application as SafeMoneyApp).syncManager.start()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun maybeShowSplash() {
        val settings = (application as SafeMoneyApp).splashSettings
        if (settings.enabled.value) splashQuote = settings.nextQuote()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        QuickActions.actionOf(intent)?.let { pendingAction = it }
    }
}

/** 退到背景超過這麼久再回來，就再播一次開場動畫。 */
private const val SPLASH_AGAIN_AFTER_MS = 5 * 60 * 1_000L
