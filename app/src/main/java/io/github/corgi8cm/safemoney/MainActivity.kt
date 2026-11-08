package io.github.corgi8cm.safemoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.corgi8cm.safemoney.ui.SafeMoneyNavHost
import io.github.corgi8cm.safemoney.ui.theme.SafeMoneyTheme
import io.github.corgi8cm.safemoney.widget.WidgetUpdater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 強制停止 App 會清掉鬧鐘；每次開啟時依設定補排一次（已排的會被覆蓋，不會重複）
        (application as SafeMoneyApp).let { it.reminderScheduler.apply(it.reminderSettings.config.value) }
        // 資料只會在 App 開著時變動，開啟時開始監聽並更新主畫面小工具
        WidgetUpdater.start(application as SafeMoneyApp)
        setContent {
            SafeMoneyTheme {
                SafeMoneyNavHost()
            }
        }
    }
}
