package io.github.corgi8cm.safemoney.ui.goal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.ui.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalDetailUiState(
    val loading: Boolean = true,
    val goal: GoalWithSaved? = null,
    val savings: List<SavingWithAllocations> = emptyList(),
)

class GoalDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SafeMoneyRepository,
) : ViewModel() {
    val goalId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GOAL_ID))

    val state: StateFlow<GoalDetailUiState> =
        combine(repository.observeGoal(goalId), repository.observeSavingsForGoal(goalId)) { goal, savings ->
            GoalDetailUiState(loading = false, goal = goal, savings = savings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailUiState())

    fun toggleArchived() {
        val goal = state.value.goal?.goal ?: return
        viewModelScope.launch {
            repository.setArchived(goalId, archived = goal.status != GoalStatus.ARCHIVED)
        }
    }

    fun delete() {
        viewModelScope.launch { repository.deleteGoal(goalId) }
    }
}
