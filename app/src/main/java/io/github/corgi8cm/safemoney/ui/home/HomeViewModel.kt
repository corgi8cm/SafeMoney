package io.github.corgi8cm.safemoney.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.domain.Summary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val active: List<GoalWithSaved> = emptyList(),
    val completed: List<GoalWithSaved> = emptyList(),
    val archived: List<GoalWithSaved> = emptyList(),
    val summary: Summary? = null,
) {
    val hasAnyGoal: Boolean get() = active.isNotEmpty() || completed.isNotEmpty() || archived.isNotEmpty()
}

class HomeViewModel(
    repository: SafeMoneyRepository,
    private val updateChecker: io.github.corgi8cm.safemoney.update.UpdateChecker,
) : ViewModel() {
    /** 有新版本時在首頁上方提醒（只有 github 版）。 */
    val update = updateChecker.available

    fun dismissUpdate(version: String) = updateChecker.dismiss(version)

    val state: StateFlow<HomeUiState> =
        combine(repository.observeGoals(), repository.observeSummary()) { goals, summary ->
            HomeUiState(
                loading = false,
                active = goals.filter { it.goal.status == GoalStatus.ACTIVE },
                // 最近回本的排前面
                completed = goals.filter { it.goal.status == GoalStatus.COMPLETED }
                    .sortedByDescending { it.goal.completedAt },
                archived = goals.filter { it.goal.status == GoalStatus.ARCHIVED },
                summary = summary,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
