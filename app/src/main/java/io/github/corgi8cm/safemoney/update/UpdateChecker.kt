package io.github.corgi8cm.safemoney.update

import android.content.Context
import io.github.corgi8cm.safemoney.domain.isNewerVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant

/**
 * 檢查有沒有新版本（只有 github 版有 [feed]）。最多一天自動查一次，結果存在 SharedPreferences。
 * 失敗（沒網路等）就安靜地略過，下次再查。
 */
class UpdateChecker(
    context: Context,
    private val currentVersion: String,
    private val feed: ReleaseFeed?,
    private val now: () -> Instant = Instant::now,
) {
    private val prefs = context.applicationContext.getSharedPreferences("update", Context.MODE_PRIVATE)

    /** 這個版本有沒有檢查更新的功能（play 版沒有）。 */
    val supported: Boolean get() = feed != null

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _available = MutableStateFlow(storedUpdate())

    /** 比目前新、而且使用者沒有按「稍後」的版本。 */
    val available: StateFlow<ReleaseInfo?> = _available.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
        _available.value = storedUpdate()
    }

    /** 開 App 時呼叫：開著自動檢查、而且距離上次超過一天才查。 */
    suspend fun checkIfDue() {
        if (!supported || !enabled.value) return
        val last = Instant.ofEpochMilli(prefs.getLong(KEY_LAST_CHECK, 0))
        if (Duration.between(last, now()) < CHECK_INTERVAL) return
        checkNow()
    }

    /** @return 查到的最新版本（不一定比目前新）；查詢失敗時為 null */
    suspend fun checkNow(): ReleaseInfo? {
        val latest = runCatching { feed?.latest() }.getOrNull() ?: return null
        prefs.edit()
            .putLong(KEY_LAST_CHECK, now().toEpochMilli())
            .putString(KEY_LATEST_VERSION, latest.version)
            .putString(KEY_LATEST_URL, latest.url)
            .apply()
        _available.value = storedUpdate()
        return latest
    }

    /** 「稍後」：這個版本不再提醒，更新的版本出來時才會再出現。 */
    fun dismiss(version: String) {
        prefs.edit().putString(KEY_DISMISSED, version).apply()
        _available.value = storedUpdate()
    }

    fun isNewer(version: String): Boolean = isNewerVersion(version, currentVersion)

    private fun storedUpdate(): ReleaseInfo? {
        if (!supported || !prefs.getBoolean(KEY_ENABLED, true)) return null
        val version = prefs.getString(KEY_LATEST_VERSION, null) ?: return null
        val url = prefs.getString(KEY_LATEST_URL, null) ?: return null
        if (!isNewerVersion(version, currentVersion)) return null
        if (prefs.getString(KEY_DISMISSED, null) == version) return null
        return ReleaseInfo(version, url)
    }

    private companion object {
        val CHECK_INTERVAL: Duration = Duration.ofHours(24)
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_CHECK = "lastCheck"
        const val KEY_LATEST_VERSION = "latestVersion"
        const val KEY_LATEST_URL = "latestUrl"
        const val KEY_DISMISSED = "dismissed"
    }
}
