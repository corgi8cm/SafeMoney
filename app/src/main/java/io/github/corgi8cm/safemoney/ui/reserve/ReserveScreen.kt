package io.github.corgi8cm.safemoney.ui.reserve

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.RESERVE_NAME
import io.github.corgi8cm.safemoney.domain.normalizeReason
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.shortWithWeekday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 存錢筒裡的一筆：哪一筆紀錄放了多少進來。 */
data class ReserveRow(val savingId: Long, val date: LocalDate, val reason: String, val amount: Long)

data class ReserveUiState(val balance: Long, val rows: List<ReserveRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class ReserveViewModel(repository: SafeMoneyRepository) : ViewModel() {
    val state: StateFlow<ReserveUiState?> =
        repository.observeReserve().flatMapLatest { reserve ->
            if (reserve == null) {
                flowOf(ReserveUiState(0, emptyList()))
            } else {
                repository.observeSavingsForGoal(reserve.goal.id).map { savings ->
                    ReserveUiState(
                        balance = reserve.saved,
                        rows = savings.mapNotNull { s ->
                            s.allocations.firstOrNull { it.goalId == reserve.goal.id }?.let {
                                ReserveRow(s.saving.id, s.saving.date, normalizeReason(s.saving.reason), it.amount)
                            }
                        },
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReserveScreen(
    onBack: () -> Unit,
    onOpenSaving: (Long) -> Unit,
    viewModel: ReserveViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(LocalAppMessenger.current.hostState) },
        topBar = {
            TopAppBar(
                title = { Text(RESERVE_NAME) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("目前存了", style = MaterialTheme.typography.labelLarge)
                        Text(
                            formatMoney(current.balance),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "所有目標都回本之後記的錢、超出目標的錢都會存在這裡。下次新增目標時，會先用這裡的錢來抵。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (current.rows.isEmpty()) {
                item {
                    Text(
                        "存錢筒還是空的。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(current.rows, key = { it.savingId }) { row ->
                ListItem(
                    headlineContent = { Text(row.reason, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(row.date.shortWithWeekday() + if (row.date.year != LocalDate.now().year) " ${row.date.year}" else "") },
                    trailingContent = {
                        Text(
                            "+${formatMoney(row.amount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable { onOpenSaving(row.savingId) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
