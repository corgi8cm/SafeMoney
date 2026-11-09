package io.github.corgi8cm.safemoney.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.data.db.progress
import io.github.corgi8cm.safemoney.data.db.total
import io.github.corgi8cm.safemoney.domain.CompletedGoal
import io.github.corgi8cm.safemoney.domain.DatedAmount
import io.github.corgi8cm.safemoney.domain.PeriodReview
import io.github.corgi8cm.safemoney.domain.ReviewEntry
import io.github.corgi8cm.safemoney.domain.ReviewPeriod
import io.github.corgi8cm.safemoney.domain.buildReview
import io.github.corgi8cm.safemoney.ui.Routes
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

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

/** 統計頁上方的切換：總覽、每月回顧、每年回顧。 */
enum class StatsMode { OVERVIEW, MONTH, YEAR }

data class ReviewUiState(val review: PeriodReview, val canPrevious: Boolean, val canNext: Boolean)

class StatsViewModel(savedStateHandle: SavedStateHandle, repository: SafeMoneyRepository) : ViewModel() {
    val state: StateFlow<StatsUiState> =
        combine(repository.observeAllSavings(), repository.observeGoals()) { savings, goals ->
            buildStats(savings, goals, LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** 從首頁「上個月回顧」卡片進來時，直接打開那個月。 */
    private val initialMonth: YearMonth? =
        savedStateHandle.get<String>(Routes.ARG_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    private val _mode = MutableStateFlow(if (initialMonth != null) StatsMode.MONTH else StatsMode.OVERVIEW)
    val mode: StateFlow<StatsMode> = _mode.asStateFlow()

    private val month = MutableStateFlow(initialMonth ?: YearMonth.now())
    private val year = MutableStateFlow(LocalDate.now().year)

    val review: StateFlow<ReviewUiState?> =
        combine(repository.observeAllSavings(), repository.observeGoals(), _mode, month, year) { savings, goals, mode, m, y ->
            val period = when (mode) {
                StatsMode.OVERVIEW -> return@combine null
                StatsMode.MONTH -> ReviewPeriod.Month(m)
                StatsMode.YEAR -> ReviewPeriod.Year(y)
            }
            buildReviewState(savings, goals, period, LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMode(mode: StatsMode) {
        _mode.value = mode
    }

    fun previous() = move(-1)

    fun next() = move(1)

    private fun move(step: Long) {
        when (_mode.value) {
            StatsMode.MONTH -> month.value = month.value.plusMonths(step)
            StatsMode.YEAR -> year.value += step.toInt()
            StatsMode.OVERVIEW -> Unit
        }
    }
}

fun buildReviewState(
    savings: List<SavingWithAllocations>,
    goals: List<GoalWithSaved>,
    period: ReviewPeriod,
    today: LocalDate,
): ReviewUiState {
    val entries = savings.map { ReviewEntry(it.saving.date, it.saving.reason, it.total()) }
    val completed = goals.mapNotNull { g -> g.goal.completedAt?.let { CompletedGoal(g.goal.name, it.localDate()) } }
    val earliest = entries.minOfOrNull { it.date }
    return ReviewUiState(
        review = buildReview(entries, completed, period),
        canPrevious = earliest != null && period.start > earliest,
        canNext = period.end < today,
    )
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
