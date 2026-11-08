package io.github.corgi8cm.safemoney.ui.saving

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.domain.Allocations
import io.github.corgi8cm.safemoney.domain.SavingForm
import io.github.corgi8cm.safemoney.ui.Routes
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.time.LocalDate

class SavingEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SafeMoneyRepository,
) : ViewModel() {
    private val savingId: Long? = savedStateHandle.get<Long>(Routes.ARG_SAVING_ID)?.takeIf { it > 0 }
    private val presetGoalId: Long? = savedStateHandle.get<Long>(Routes.ARG_GOAL_ID)?.takeIf { it > 0 }
    val isEdit: Boolean = savingId != null

    /** 編輯時，原本分配到的目標就算已封存也要能看到。 */
    private val keepGoalIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 可以選的目標：未封存的，加上這筆紀錄原本分配到的。 */
    val goals: StateFlow<List<GoalWithSaved>> =
        combine(repository.observeGoals(), keepGoalIds) { all, keep ->
            all.filter { it.goal.status != GoalStatus.ARCHIVED || it.goal.id in keep }
                // 進行中的排前面
                .sortedBy { if (it.goal.status == GoalStatus.ACTIVE) 0 else 1 }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 快捷原因，常用的在前面。 */
    val quickReasons: StateFlow<List<String>> =
        repository.observeQuickReasonsByUsage().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var amountText by mutableStateOf("")
    var reason by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var selectedGoalId by mutableStateOf<Long?>(null)
    var splitMode by mutableStateOf(false)
        private set

    /** 拆分模式下勾選的目標 → 金額文字。 */
    val splitAmounts = mutableStateMapOf<Long, String>()

    var loaded by mutableStateOf(!isEdit)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    /** 存檔後有目標剛回本時，在這裡依序慶祝。 */
    val celebration = CelebrationController(repository, viewModelScope)

    init {
        viewModelScope.launch {
            if (savingId != null) {
                val existing = repository.getSaving(savingId)
                if (existing == null) {
                    done = true
                    return@launch
                }
                keepGoalIds.value = existing.allocations.map { it.goalId }.toSet()
                amountText = existing.allocations.sumOf { it.amount }.toString()
                reason = existing.saving.reason
                date = existing.saving.date
                if (existing.allocations.size == 1) {
                    selectedGoalId = existing.allocations.single().goalId
                } else {
                    splitMode = true
                    existing.allocations.forEach { splitAmounts[it.goalId] = it.amount.toString() }
                }
                loaded = true
            } else {
                val available = repository.observeGoals().first()
                    .filter { it.goal.status != GoalStatus.ARCHIVED }
                selectedGoalId = presetGoalId?.takeIf { id -> available.any { it.goal.id == id } }
                    ?: available.firstOrNull { it.goal.status == GoalStatus.ACTIVE }?.goal?.id
            }
        }
    }

    /** 拆分模式中勾選的目標，依畫面上的順序。 */
    fun splitParts(): List<Pair<Long, String>> =
        goals.value.mapNotNull { row -> splitAmounts[row.goal.id]?.let { row.goal.id to it } }

    fun setSplit(enabled: Boolean) {
        if (enabled == splitMode) return
        error = null
        if (enabled) {
            splitAmounts.clear()
            selectedGoalId?.let { splitAmounts[it] = amountText }
        } else {
            selectedGoalId = splitParts().firstOrNull()?.first ?: selectedGoalId
            splitAmounts.clear()
        }
        splitMode = enabled
    }

    fun toggleSplitGoal(goalId: Long) {
        if (splitAmounts.containsKey(goalId)) splitAmounts.remove(goalId) else splitAmounts[goalId] = ""
    }

    fun splitEvenly() {
        val total = SavingForm.parseAmount(amountText)
        val ids = splitParts().map { it.first }
        error = when {
            total == null -> "請先輸入省下的金額"
            ids.isEmpty() -> "請先勾選要拆分的目標"
            else -> try {
                Allocations.splitEvenly(total, ids).forEach { splitAmounts[it.goalId] = it.amount.toString() }
                null
            } catch (e: IllegalArgumentException) {
                e.message
            }
        }
    }

    fun save() {
        if (busy) return
        val result = if (splitMode) {
            SavingForm.split(amountText, splitParts())
        } else {
            SavingForm.single(amountText, selectedGoalId)
        }
        when (result) {
            is SavingForm.Result.Error -> error = result.message
            is SavingForm.Result.Ok -> {
                error = null
                busy = true
                viewModelScope.launch {
                    try {
                        val completed = if (savingId == null) {
                            repository.addSaving(reason, date, result.allocations).completedGoalIds
                        } else {
                            repository.updateSaving(savingId, reason, date, result.allocations)
                        }
                        if (completed.isEmpty()) {
                            done = true
                        } else {
                            // 保持 busy，慶祝看完（celebration.finished）才離開
                            celebration.start(completed)
                        }
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                        busy = false
                    }
                }
            }
        }
    }

    /** 刪除後呼叫 [onDeleted]（訊息與復原動作），由畫面顯示「復原」按鈕。 */
    fun delete(onDeleted: (message: String, undo: suspend () -> Unit) -> Unit) {
        val id = savingId ?: return
        if (busy) return
        busy = true
        val repo = repository
        viewModelScope.launch(NonCancellable) {
            try {
                val deleted = repo.deleteSaving(id)
                if (deleted != null) {
                    val what = deleted.saving.reason.ifBlank { "這筆紀錄" }
                    onDeleted("已刪除「$what」") { repo.undoDeleteSaving(deleted) }
                }
                done = true
            } finally {
                busy = false
            }
        }
    }
}
