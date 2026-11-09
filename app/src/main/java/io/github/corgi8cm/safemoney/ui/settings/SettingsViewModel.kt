package io.github.corgi8cm.safemoney.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.SafeMoneyApp
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.backup.BackupCodec
import io.github.corgi8cm.safemoney.data.backup.BackupData
import io.github.corgi8cm.safemoney.data.backup.BackupException
import io.github.corgi8cm.safemoney.diagnostics.CrashLog
import io.github.corgi8cm.safemoney.domain.CsvExport
import io.github.corgi8cm.safemoney.reminder.ReminderConfig
import io.github.corgi8cm.safemoney.sync.AuthFailedException
import io.github.corgi8cm.safemoney.sync.GoogleDriveAuth
import io.github.corgi8cm.safemoney.sync.SyncStatus
import android.content.Intent
import android.content.IntentSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 手機上目前的資料量，顯示在還原確認視窗。 */
data class DataCounts(val goals: Int = 0, val savings: Int = 0)

/** 備份檔最大 10 MB；正常使用一輩子也到不了，超過多半是選錯檔案。 */
private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

class SettingsViewModel(
    private val app: SafeMoneyApp,
    private val repository: SafeMoneyRepository,
) : ViewModel() {
    val appVersion: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull().orEmpty()

    val counts: StateFlow<DataCounts> =
        combine(repository.observeGoals(), repository.observeAllSavings()) { goals, savings ->
            DataCounts(goals.size, savings.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataCounts())

    val reminder: StateFlow<ReminderConfig> = app.reminderSettings.config

    val sync: StateFlow<SyncStatus> = app.syncManager.status

    /** 需要顯示 Google 的同意畫面時不是 null；畫面啟動後呼叫 [consentLaunched]。 */
    var consentRequest by mutableStateOf<IntentSender?>(null)
        private set

    /** 開始同步：先取得 Google 雲端硬碟的授權（第一次會跳出同意畫面）。 */
    fun connectSync() = runBusy {
        try {
            when (val r = app.driveAuth.authorize()) {
                is GoogleDriveAuth.Result.Token -> {
                    app.syncManager.enable()
                    message = "已開啟同步"
                }
                is GoogleDriveAuth.Result.NeedsConsent -> consentRequest = r.intentSender
            }
        } catch (e: AuthFailedException) {
            message = e.message
        }
    }

    fun consentLaunched() {
        consentRequest = null
    }

    fun onConsentResult(resultOk: Boolean, data: Intent?) {
        if (!resultOk) {
            message = "沒有完成授權，同步沒有開啟"
            return
        }
        try {
            app.driveAuth.tokenFromConsent(data)
            app.syncManager.enable()
            message = "已開啟同步"
        } catch (e: AuthFailedException) {
            message = e.message
        }
    }

    fun syncNow() = runBusy {
        message = if (app.syncManager.syncNow()) "同步完成" else app.syncManager.status.value.error ?: "目前無法同步"
    }

    fun disableSync() {
        app.syncManager.disable()
        message = "已停止同步，雲端的資料仍然保留"
    }

    fun deleteCloudData() = runBusy {
        app.syncManager.deleteCloudData()
        message = "已刪除雲端的同步資料，並停止同步"
    }

    val updatesSupported: Boolean = app.updateChecker.supported
    val updatesEnabled: StateFlow<Boolean> = app.updateChecker.enabled

    fun setUpdatesEnabled(enabled: Boolean) = app.updateChecker.setEnabled(enabled)

    fun checkUpdateNow() = runBusy {
        val latest = app.updateChecker.checkNow()
        message = when {
            latest == null -> "目前無法檢查，請確認網路後再試"
            app.updateChecker.isNewer(latest.version) -> "有新版本 v${latest.version}，可以到首頁下載"
            else -> "已經是最新版本"
        }
    }

    fun setReminderEnabled(enabled: Boolean) = updateReminder(reminder.value.copy(enabled = enabled))

    fun setReminderTime(time: LocalTime) = updateReminder(reminder.value.copy(time = time))

    private fun updateReminder(config: ReminderConfig) {
        app.reminderSettings.update(config)
        app.reminderScheduler.apply(config)
    }

    /** 給畫面顯示一次性訊息（例如沒有通知權限）。 */
    fun showMessage(text: String) {
        message = text
    }

    /** 有沒有錯誤紀錄可以匯出。 */
    var hasCrashLog by mutableStateOf(CrashLog.read(app) != null)
        private set

    fun crashLogFileName(today: LocalDate = LocalDate.now()): String =
        "safemoney-error-log-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}.txt"

    fun exportCrashLog(uri: Uri) = runBusy {
        val text = CrashLog.read(app) ?: throw IOException("沒有錯誤紀錄")
        withContext(Dispatchers.IO) {
            val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("無法開啟檔案")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        message = "已匯出錯誤紀錄"
    }

    fun clearCrashLog() {
        CrashLog.clear(app)
        hasCrashLog = false
        message = "已清除錯誤紀錄"
    }

    var busy by mutableStateOf(false)
        private set

    /** 已讀取、驗證過，等使用者確認的備份。 */
    var pendingRestore by mutableStateOf<BackupData?>(null)
        private set

    /** 要顯示在 snackbar 的訊息，顯示後呼叫 [messageShown]。 */
    var message by mutableStateOf<String?>(null)
        private set

    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "safemoney-backup-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}.json"

    fun csvFileName(): String = CsvExport.fileName(month = null)

    /** 匯出所有紀錄成試算表（CSV）。 */
    fun exportCsv(uri: Uri) = runBusy {
        val data = repository.snapshot(null)
        val text = CsvExport.build(data.savings, data.goals.associate { it.id to it.name })
        withContext(Dispatchers.IO) {
            val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("無法開啟檔案")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        message = "已匯出 ${data.savings.size} 筆紀錄"
    }

    fun export(uri: Uri) = runBusy {
        val data = repository.snapshot(appVersion.ifEmpty { null })
        val text = BackupCodec.encode(data)
        withContext(Dispatchers.IO) {
            val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("無法開啟檔案")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        message = "已匯出 ${data.goals.size} 個目標、${data.savings.size} 筆紀錄"
    }

    /** 讀取並驗證備份檔；沒問題就等使用者在確認視窗按下還原。 */
    fun pickRestore(uri: Uri) = runBusy {
        val text = withContext(Dispatchers.IO) {
            val stream = app.contentResolver.openInputStream(uri) ?: throw IOException("無法開啟檔案")
            stream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BACKUP_BYTES + 1)
                if (bytes.size > MAX_BACKUP_BYTES) throw BackupException("檔案太大，這應該不是省回來的備份檔")
                bytes.toString(Charsets.UTF_8)
            }
        }
        pendingRestore = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
    }

    fun confirmRestore() {
        val data = pendingRestore ?: return
        pendingRestore = null
        runBusy {
            repository.restore(data)
            message = "已還原 ${data.goals.size} 個目標、${data.savings.size} 筆紀錄"
        }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun messageShown() {
        message = null
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: BackupException) {
                message = e.message
            } catch (e: IOException) {
                message = "讀寫檔案失敗：${e.message ?: "未知錯誤"}"
            } catch (e: SecurityException) {
                message = "沒有權限讀寫這個檔案"
            } finally {
                busy = false
            }
        }
    }
}

/** InputStream.readNBytes 要 API 33，這裡自己讀最多 [limit] bytes。 */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
