package io.github.corgi8cm.safemoney.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.data.db.progress
import io.github.corgi8cm.safemoney.data.db.total
import io.github.corgi8cm.safemoney.domain.DatedAmount
import io.github.corgi8cm.safemoney.domain.FORECAST_WINDOW_DAYS
import io.github.corgi8cm.safemoney.domain.Forecast
import io.github.corgi8cm.safemoney.domain.PeriodTotal
import io.github.corgi8cm.safemoney.domain.ReasonAmount
import io.github.corgi8cm.safemoney.domain.ReasonTotal
import io.github.corgi8cm.safemoney.domain.currentStreak
import io.github.corgi8cm.safemoney.domain.dailyTotals
import io.github.corgi8cm.safemoney.domain.forecastCompletion
import io.github.corgi8cm.safemoney.domain.reasonBreakdown
import io.github.corgi8cm.safemoney.domain.weeklyTotals
import io.github.corgi8cm.safemoney.ui.common.localDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

const val DAILY_CHART_DAYS = 14
const val WEEKLY_CHART_WEEKS = 8

data class StatsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val totalSaved: Long = 0,
    val recentTotal: Long = 0,
    val streak: Int = 0,
    val daily: List<PeriodTotal> = emptyList(),
    val weekly: List<PeriodTotal> = emptyList(),
    val reasons: List<ReasonTotal> = emptyList(),
    /** 進行中目標還差的總額。 */
    val activeRemaining: Long = 0,
    /** 沒有進行中目標時為 null。 */
    val overallForecast: Forecast? = null,
) {
    val hasSavings: Boolean get() = totalSaved > 0
}

class StatsViewModel(repository: SafeMoneyRepository) : ViewModel() {
    val state: StateFlow<StatsUiState> =
        combine(repository.observeAllSavings(), repository.observeGoals()) { savings, goals ->
            buildStats(savings, goals, LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

fun buildStats(savings: List<SavingWithAllocations>, goals: List<GoalWithSaved>, today: LocalDate): StatsUiState {
    val entries = savings.map { DatedAmount(it.saving.date, it.total()) }
    val recentStart = today.minusDays(FORECAST_WINDOW_DAYS - 1L)
    val active = goals.filter { it.goal.status == GoalStatus.ACTIVE }
    val activeRemaining = active.sumOf { it.progress().remaining }
    val overall = if (active.isEmpty()) {
        null
    } else {
        forecastCompletion(
            remaining = activeRemaining,
            entries = entries,
            since = goals.minOf { it.goal.createdAt.localDate() },
            today = today,
        )
    }
    return StatsUiState(
        loading = false,
        today = today,
        totalSaved = entries.sumOf { it.amount },
        recentTotal = entries.filter { it.date in recentStart..today }.sumOf { it.amount },
        streak = currentStreak(entries.filter { it.amount > 0 }.map { it.date }, today),
        daily = dailyTotals(entries, today, DAILY_CHART_DAYS),
        weekly = weeklyTotals(entries, today, WEEKLY_CHART_WEEKS),
        reasons = reasonBreakdown(savings.map { ReasonAmount(it.saving.reason, it.total()) }),
        activeRemaining = activeRemaining,
        overallForecast = overall,
    )
}
