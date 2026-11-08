package io.github.corgi8cm.safemoney.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 全 App 共用的訊息列。刪除後離開畫面也還看得到「復原」按鈕：
 * 訊息顯示在目前畫面的 Scaffold（各畫面用 [hostState] 當 snackbarHost），
 * 復原動作在 App 層級的 [workScope] 執行，不會因為原畫面關閉而取消。
 */
class AppMessenger(
    val hostState: SnackbarHostState,
    private val uiScope: CoroutineScope,
    private val workScope: CoroutineScope,
) {
    fun show(message: String) {
        uiScope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message, withDismissAction = true)
        }
    }

    /** 顯示訊息與「復原」按鈕，按下就執行 [undo]。 */
    fun showUndo(message: String, undo: suspend () -> Unit) {
        uiScope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = "復原",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                workScope.launch {
                    try {
                        undo()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        show("無法復原：${e.message ?: "未知錯誤"}")
                    }
                }
            }
        }
    }
}

val LocalAppMessenger = staticCompositionLocalOf<AppMessenger> { error("AppMessenger not provided") }
