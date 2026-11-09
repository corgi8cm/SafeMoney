package io.github.corgi8cm.safemoney.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.total
import io.github.corgi8cm.safemoney.domain.Summary
import io.github.corgi8cm.safemoney.domain.monthlyCardMonth
import io.github.corgi8cm.safemoney.ui.stats.ReviewCardSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** 首頁「上個月回顧」卡片。 */
data class MonthlyCard(val month: YearMonth, val total: Long)

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
    private val reviewCardSettings: ReviewCardSettings,
) : ViewModel() {
    /** 每月 1 號起顯示上個月的回顧，按 ✕ 收起（上個月沒有紀錄就不顯示）。 */
    val monthlyCard: StateFlow<MonthlyCard?> =
        combine(repository.observeAllSavings(), reviewCardSettings.dismissed) { savings, dismissed ->
            val totals = savings.groupBy { YearMonth.from(it.saving.date) }.mapValues { (_, v) -> v.sumOf { it.total() } }
            monthlyCardMonth(LocalDate.now(), dismissed) { (totals[it] ?: 0) > 0 }?.let { MonthlyCard(it, totals.getValue(it)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissMonthlyCard(month: YearMonth) = reviewCardSettings.dismiss(month)

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
