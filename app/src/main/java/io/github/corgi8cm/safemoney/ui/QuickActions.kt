package io.github.corgi8cm.safemoney.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.corgi8cm.safemoney.MainActivity
import io.github.corgi8cm.safemoney.R

/** 從 App 外面直接跳到某個畫面：長按桌面圖示的捷徑、主畫面小工具的按鈕。 */
object QuickActions {
    const val ACTION_NEW_SAVING = "io.github.corgi8cm.safemoney.action.NEW_SAVING"

    private val known = setOf(ACTION_NEW_SAVING)

    fun actionOf(intent: Intent?): String? = intent?.action?.takeIf { it in known }

    fun newSavingIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_NEW_SAVING)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** 長按桌面圖示會出現「記一筆」。用動態捷徑，測試版（不同 applicationId）也能用。 */
    fun publishShortcuts(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, "new_saving")
            .setShortLabel("記一筆")
            .setLongLabel("記一筆省錢")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add))
            .setIntent(newSavingIntent(context))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}
