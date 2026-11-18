package io.github.corgi8cm.safemoney.ui.recurring

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.RecurringRecordResult
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.RecurringSaving
import io.github.corgi8cm.safemoney.domain.PendingRecurring
import io.github.corgi8cm.safemoney.domain.Recurrence
import io.github.corgi8cm.safemoney.domain.SavingForm
import io.github.corgi8cm.safemoney.ui.Routes
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationController
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 設定頁 → 定期省錢 的清單。 */
class RecurringListViewModel(repository: SafeMoneyRepository) : ViewModel() {
    val rules: StateFlow<List<RecurringSaving>?> =
        repository.observeRecurring().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** 新增或修改一個定期省錢。 */
class RecurringEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SafeMoneyRepository,
) : ViewModel() {
    private val ruleId: Long? = savedStateHandle.get<Long>(Routes.ARG_RULE_ID)?.takeIf { it > 0 }
    val isEdit: Boolean = ruleId != null
    val today: LocalDate = LocalDate.now()

    val quickReasons: StateFlow<List<String>> =
        repository.observeQuickReasonsByUsage().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var reason by mutableStateOf("")
    var amountText by mutableStateOf("")
    var days by mutableIntStateOf(Recurrence.EVERY_DAY)
    var startDate by mutableStateOf(today)
    var hasEndDate by mutableStateOf(false)
    var endDate by mutableStateOf(today.plusMonths(1))

    /** 已經開始的設定不能改開始日。 */
    var started by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(!isEdit)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    init {
        if (ruleId != null) {
            viewModelScope.launch {
                val rule = repository.getRecurring(ruleId)
                if (rule == null) {
                    done = true
                    return@launch
                }
                reason = rule.reason
                amountText = rule.amount.toString()
                days = rule.days
                startDate = rule.startDate
                hasEndDate = rule.endDate != null
                endDate = rule.endDate ?: maxOf(today, rule.startDate).plusMonths(1)
                started = rule.startDate <= today
                loaded = true
            }
        }
    }

    fun onStartDateChange(date: LocalDate) {
        startDate = date
        if (endDate < date) endDate = date
    }

    fun save() {
        if (busy) return
        val amount = SavingForm.parseAmount(amountText)
        if (amount == null) {
            error = "請輸入每次省下的金額"
            return
        }
        val end = if (hasEndDate) endDate else null
        busy = true
        error = null
        viewModelScope.launch {
            try {
                if (ruleId == null) {
                    repository.addRecurring(reason, amount, days, startDate, end)
                } else {
                    repository.updateRecurring(ruleId, reason, amount, days, startDate, end)
                }
                done = true
            } catch (e: IllegalArgumentException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    /** 移除設定（已經記下的紀錄保留），由畫面顯示「復原」按鈕。 */
    fun delete(onDeleted: (message: String, undo: suspend () -> Unit) -> Unit) {
        val id = ruleId ?: return
        if (busy) return
        busy = true
        val repo = repository
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    repo.deleteRecurring(id)?.let { rule ->
                        onDeleted("已移除「${rule.reason}」，已經記下的紀錄都還在") { repo.undoDeleteRecurring(rule) }
                    }
                    done = true
                } finally {
                    busy = false
                }
            }
        }
    }
}

/**
 * App 內的定期省錢通知：到期的日子會累積起來，使用者關掉通知時一次記下。
 * 由畫面每天一開始（以及每次回到 App 時）呼叫 [setToday]。
 */
class RecurringPromptViewModel(private val repository: SafeMoneyRepository) : ViewModel() {
    private val today = MutableStateFlow<LocalDate?>(null)

    val pending: StateFlow<List<PendingRecurring>> =
        combine(repository.observeRecurring(), today) { rules, day ->
            if (day == null) emptyList() else Recurrence.pending(rules, day)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 記下之後要顯示的訊息（顯示後呼叫 [messageShown]）。 */
    var message by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    val celebration = CelebrationController(repository, viewModelScope)

    fun setToday(date: LocalDate) {
        today.value = date
    }

    /** 使用者關掉通知：記下所有到期的日子。 */
    fun dismiss() = record()

    fun messageShown() {
        message = null
    }

    private fun record() {
        val day = today.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = withContext(NonCancellable) { repository.recordPendingRecurring(day) }
                when (result) {
                    RecurringRecordResult.NothingDue -> Unit
                    is RecurringRecordResult.Recorded -> {
                        if (result.count > 0) message = "已記下 ${result.count} 筆定期省錢，共 ${formatMoney(result.total)}"
                        if (result.completedGoalIds.isNotEmpty()) celebration.start(result.completedGoalIds)
                    }
                }
            } finally {
                busy = false
            }
        }
    }
}
