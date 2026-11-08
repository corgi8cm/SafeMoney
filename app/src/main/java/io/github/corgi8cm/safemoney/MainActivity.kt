package io.github.corgi8cm.safemoney

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.corgi8cm.safemoney.ui.QuickActions
import io.github.corgi8cm.safemoney.ui.SafeMoneyNavHost
import io.github.corgi8cm.safemoney.ui.theme.SafeMoneyTheme
import io.github.corgi8cm.safemoney.widget.WidgetUpdater

class MainActivity : ComponentActivity() {
    /** 從桌面捷徑或小工具帶進來、還沒處理的動作（例如直接記一筆）。 */
    private var pendingAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 畫面重建（例如旋轉）時不要重複執行捷徑動作
        if (savedInstanceState == null) pendingAction = QuickActions.actionOf(intent)
        // 強制停止 App 會清掉鬧鐘；每次開啟時依設定補排一次（已排的會被覆蓋，不會重複）
        (application as SafeMoneyApp).let { it.reminderScheduler.apply(it.reminderSettings.config.value) }
        // 資料只會在 App 開著時變動，開啟時開始監聽並更新主畫面小工具
        WidgetUpdater.start(application as SafeMoneyApp)
        QuickActions.publishShortcuts(this)
        setContent {
            SafeMoneyTheme {
                SafeMoneyNavHost(
                    pendingAction = pendingAction,
                    onPendingActionHandled = { pendingAction = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        QuickActions.actionOf(intent)?.let { pendingAction = it }
    }
}
