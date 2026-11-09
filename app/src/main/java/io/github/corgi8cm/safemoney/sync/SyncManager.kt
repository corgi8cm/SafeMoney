package io.github.corgi8cm.safemoney.sync

import android.content.Context
import android.util.Log
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class SyncStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val lastSyncAt: Instant? = null,
    /** 最近一次失敗的原因；成功後清掉。 */
    val error: String? = null,
    /** 授權被收回或過期，需要使用者在設定頁重新同意。 */
    val needsConsent: Boolean = false,
)

/**
 * 管理同步的開關與時機：
 * - 開啟 App（距離上次超過 [STALE_AFTER]）時同步
 * - 資料有變動後等 [CHANGE_DELAY] 再同步（連續修改只同步一次）
 * - 設定頁的「立即同步」
 * 失敗時只記下原因，不打擾使用者，下次再試。
 */
class SyncManager(
    context: Context,
    private val repository: SafeMoneyRepository,
    private val auth: GoogleDriveAuth,
    private val scope: CoroutineScope,
) {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val store = DriveSyncStore(token = { auth.token() }, invalidate = { auth.invalidate(it) })
    private val engine = SyncEngine(repository, store)

    private val _status = MutableStateFlow(
        SyncStatus(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            error = prefs.getString(KEY_ERROR, null),
            needsConsent = prefs.getBoolean(KEY_NEEDS_CONSENT, false),
        )
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val watching = AtomicBoolean(false)

    /** 開 App 時呼叫：開始監聽資料變動，必要時同步一次。可重複呼叫。 */
    fun start() {
        if (watching.compareAndSet(false, true)) {
            scope.launch {
                combine(
                    repository.observeGoals(),
                    repository.observeAllSavings(),
                    repository.observeQuickReasons(),
                ) { _, _, _ -> }
                    .drop(1)
                    .collectLatest {
                        delay(CHANGE_DELAY.toMillis())
                        if (status.value.enabled) syncNow()
                    }
            }
        }
        val last = status.value.lastSyncAt
        if (status.value.enabled && (last == null || Duration.between(last, Instant.now()) > STALE_AFTER)) {
            scope.launch { syncNow() }
        }
    }

    /** 使用者同意授權之後開啟同步，並馬上同步一次。 */
    fun enable() {
        prefs.edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_NEEDS_CONSENT, false).apply()
        _status.update { it.copy(enabled = true, needsConsent = false, error = null) }
        scope.launch { syncNow() }
    }

    /** 停止同步（雲端的資料保留，之後可以再開）。 */
    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).remove(KEY_ERROR).apply()
        _status.update { it.copy(enabled = false, error = null) }
    }

    /** @return 成功時為 true */
    suspend fun syncNow(): Boolean {
        if (!status.value.enabled || status.value.running) return false
        _status.update { it.copy(running = true) }
        return try {
            engine.sync()
            val now = Instant.now()
            prefs.edit().putLong(KEY_LAST_SYNC, now.toEpochMilli()).remove(KEY_ERROR).putBoolean(KEY_NEEDS_CONSENT, false).apply()
            _status.update { it.copy(running = false, lastSyncAt = now, error = null, needsConsent = false) }
            true
        } catch (e: CancellationException) {
            _status.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            val needsConsent = e is NeedsConsentException
            val message = when (e) {
                is NeedsConsentException -> "需要重新授權 Google 雲端硬碟"
                is AuthFailedException, is SyncFormatException -> e.message
                is IOException -> "網路連線失敗，稍後會再試"
                else -> "同步失敗：${e.message ?: e.javaClass.simpleName}"
            }
            Log.w(TAG, "sync failed", e)
            prefs.edit().putString(KEY_ERROR, message).putBoolean(KEY_NEEDS_CONSENT, needsConsent).apply()
            _status.update { it.copy(running = false, error = message, needsConsent = needsConsent) }
            false
        }
    }

    /** 刪除雲端的同步檔並停止同步；手機上的資料不受影響。 */
    suspend fun deleteCloudData() {
        store.delete()
        disable()
    }

    private companion object {
        const val TAG = "SyncManager"
        val STALE_AFTER: Duration = Duration.ofMinutes(2)
        val CHANGE_DELAY: Duration = Duration.ofSeconds(10)
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_SYNC = "lastSync"
        const val KEY_ERROR = "error"
        const val KEY_NEEDS_CONSENT = "needsConsent"
    }
}
