package io.github.corgi8cm.safemoney.ui.goal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.data.db.progress
import io.github.corgi8cm.safemoney.domain.DatedAmount
import io.github.corgi8cm.safemoney.domain.Forecast
import io.github.corgi8cm.safemoney.domain.forecastCompletion
import io.github.corgi8cm.safemoney.ui.common.localDate
import io.github.corgi8cm.safemoney.ui.Routes
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalDetailUiState(
    val loading: Boolean = true,
    val goal: GoalWithSaved? = null,
    val savings: List<SavingWithAllocations> = emptyList(),
    /** 可以接收超額的其他進行中目標。 */
    val transferTargets: List<GoalWithSaved> = emptyList(),
    /** 進行中目標的預估回本日；已回本或已封存時為 null。 */
    val forecast: Forecast? = null,
    val today: LocalDate = LocalDate.now(),
)

class GoalDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SafeMoneyRepository,
) : ViewModel() {
    val goalId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GOAL_ID))

    val state: StateFlow<GoalDetailUiState> =
        combine(
            repository.observeGoal(goalId),
            repository.observeSavingsForGoal(goalId),
            repository.observeGoals(),
        ) { goal, savings, all ->
            val today = LocalDate.now()
            GoalDetailUiState(
                loading = false,
                goal = goal,
                savings = savings,
                transferTargets = all.filter { it.goal.status == GoalStatus.ACTIVE && it.goal.id != goalId },
                forecast = goal?.takeIf { it.goal.status == GoalStatus.ACTIVE }?.let { row ->
                    forecastCompletion(
                        remaining = row.progress().remaining,
                        entries = savings.map { s ->
                            DatedAmount(s.saving.date, s.allocations.filter { it.goalId == goalId }.sumOf { it.amount })
                        },
                        since = row.goal.createdAt.localDate(),
                        today = today,
                    )
                },
                today = today,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailUiState())

    /** 轉移超額後若讓其他目標回本，在這頁慶祝。 */
    val celebration = CelebrationController(repository, viewModelScope)

    var transferring by mutableStateOf(false)
        private set

    fun toggleArchived() {
        val goal = state.value.goal?.goal ?: return
        viewModelScope.launch {
            repository.setArchived(goalId, archived = goal.status != GoalStatus.ARCHIVED)
        }
    }

    /** 刪除後呼叫 [onDeleted]（訊息與復原動作），由畫面顯示「復原」按鈕。 */
    fun delete(onDeleted: (message: String, undo: suspend () -> Unit) -> Unit) {
        val repo = repository
        // 刪除後畫面會立刻離開、ViewModel 跟著結束；NonCancellable 確保還是會送出「復原」
        viewModelScope.launch(NonCancellable) {
            val deleted = repo.deleteGoal(goalId) ?: return@launch
            onDeleted("已刪除「${deleted.goal.name}」") { repo.undoDeleteGoal(deleted) }
        }
    }

    fun transferOverflow(toGoalId: Long) {
        if (transferring) return
        transferring = true
        viewModelScope.launch {
            try {
                val result = repository.transferOverflow(goalId, toGoalId)
                if (result.completedGoalIds.isNotEmpty()) celebration.start(result.completedGoalIds)
            } finally {
                transferring = false
            }
        }
    }
}
