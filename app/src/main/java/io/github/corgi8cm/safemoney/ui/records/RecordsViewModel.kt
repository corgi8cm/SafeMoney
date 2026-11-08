package io.github.corgi8cm.safemoney.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.data.db.total
import io.github.corgi8cm.safemoney.domain.RecordFilter
import io.github.corgi8cm.safemoney.domain.filterRecords
import io.github.corgi8cm.safemoney.domain.normalizeReason
import io.github.corgi8cm.safemoney.domain.reasonOptions
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class RecordRow(
    val savingId: Long,
    val date: LocalDate,
    val reason: String,
    val total: Long,
    /** 例：「🧥 大衣」或拆分時「🧥 大衣 $60、鞋子 $40」 */
    val goalsLabel: String,
)

data class RecordsUiState(
    val loading: Boolean = true,
    val rows: List<RecordRow> = emptyList(),
    val total: Long = 0,
    /** 篩選選單可選的原因（依出現次數）。 */
    val reasons: List<String> = emptyList(),
    val hasAnyRecord: Boolean = false,
)

class RecordsViewModel(repository: SafeMoneyRepository) : ViewModel() {
    private val _filter = MutableStateFlow(RecordFilter(month = YearMonth.now()))
    val filter: StateFlow<RecordFilter> = _filter.asStateFlow()

    val state: StateFlow<RecordsUiState> =
        combine(repository.observeAllSavings(), repository.observeGoals(), _filter) { savings, goals, filter ->
            buildRecords(savings, goals, filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    fun setMonth(month: YearMonth?) {
        _filter.value = _filter.value.copy(month = month)
    }

    fun setReason(reason: String?) {
        _filter.value = _filter.value.copy(reason = reason)
    }
}

fun buildRecords(
    savings: List<SavingWithAllocations>,
    goals: List<GoalWithSaved>,
    filter: RecordFilter,
): RecordsUiState {
    val goalLabel = goals.associate { g -> g.goal.id to listOfNotNull(g.goal.emoji, g.goal.name).joinToString(" ") }
    val filtered = filterRecords(savings, filter, { it.saving.date }, { it.saving.reason })
    val rows = filtered.map { s ->
        val label = if (s.allocations.size == 1) {
            goalLabel[s.allocations.single().goalId].orEmpty()
        } else {
            s.allocations.joinToString("、") { "${goalLabel[it.goalId].orEmpty()} ${formatMoney(it.amount)}" }
        }
        RecordRow(s.saving.id, s.saving.date, normalizeReason(s.saving.reason), s.total(), label)
    }
    return RecordsUiState(
        loading = false,
        rows = rows,
        total = rows.sumOf { it.total },
        reasons = reasonOptions(savings.map { it.saving.reason }),
        hasAnyRecord = savings.isNotEmpty(),
    )
}
