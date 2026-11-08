package io.github.corgi8cm.safemoney.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.backup.BackupCodec
import io.github.corgi8cm.safemoney.data.backup.BackupData
import io.github.corgi8cm.safemoney.data.backup.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 手機上目前的資料量，顯示在還原確認視窗。 */
data class DataCounts(val goals: Int = 0, val savings: Int = 0)

/** 備份檔最大 10 MB；正常使用一輩子也到不了，超過多半是選錯檔案。 */
private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

class SettingsViewModel(
    private val app: Application,
    private val repository: SafeMoneyRepository,
) : ViewModel() {
    val appVersion: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull().orEmpty()

    val counts: StateFlow<DataCounts> =
        combine(repository.observeGoals(), repository.observeAllSavings()) { goals, savings ->
            DataCounts(goals.size, savings.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataCounts())

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
