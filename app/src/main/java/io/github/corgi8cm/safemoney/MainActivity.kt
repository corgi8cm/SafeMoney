package io.github.corgi8cm.safemoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.corgi8cm.safemoney.ui.HomeScreen
import io.github.corgi8cm.safemoney.ui.theme.SafeMoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeMoneyTheme {
                HomeScreen()
            }
        }
    }
}
