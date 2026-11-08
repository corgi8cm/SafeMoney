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
import io.github.corgi8cm.safemoney.ui.Routes
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalDetailUiState(
    val loading: Boolean = true,
    val goal: GoalWithSaved? = null,
    val savings: List<SavingWithAllocations> = emptyList(),
    /** 可以接收超額的其他進行中目標。 */
    val transferTargets: List<GoalWithSaved> = emptyList(),
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
            GoalDetailUiState(
                loading = false,
                goal = goal,
                savings = savings,
                transferTargets = all.filter { it.goal.status == GoalStatus.ACTIVE && it.goal.id != goalId },
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

    fun delete() {
        viewModelScope.launch { repository.deleteGoal(goalId) }
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
