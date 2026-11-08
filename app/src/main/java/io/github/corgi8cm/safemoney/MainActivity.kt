package io.github.corgi8cm.safemoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.corgi8cm.safemoney.ui.SafeMoneyNavHost
import io.github.corgi8cm.safemoney.ui.theme.SafeMoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 強制停止 App 會清掉鬧鐘；每次開啟時依設定補排一次（已排的會被覆蓋，不會重複）
        (application as SafeMoneyApp).let { it.reminderScheduler.apply(it.reminderSettings.config.value) }
        setContent {
            SafeMoneyTheme {
                SafeMoneyNavHost()
            }
        }
    }
}
