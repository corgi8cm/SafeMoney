package io.github.corgi8cm.safemoney.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.progress
import io.github.corgi8cm.safemoney.domain.Summary
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.GoalProgressBar
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.percentText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onAddSaving: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("省回來") },
                actions = {
                    IconButton(onClick = onAddGoal) { Icon(Icons.Filled.Add, contentDescription = "新增目標") }
                },
            )
        },
        floatingActionButton = {
            if (state.active.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddSaving,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("記一筆省錢") },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            !state.hasAnyGoal -> EmptyHome(onAddGoal, Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    // 留空間給浮動按鈕
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.summary?.takeIf { it.goalCount > 0 }?.let { summary ->
                    item(key = "summary") { SummaryCard(summary) }
                }

                if (state.active.isNotEmpty()) {
                    item(key = "header-active") { SectionHeader("進行中") }
                    items(state.active, key = { it.goal.id }) { GoalCard(it, onClick = { onOpenGoal(it.goal.id) }) }
                } else if (state.completed.isNotEmpty()) {
                    item(key = "all-done") { AllDoneCard(onAddGoal) }
                }

                if (state.completed.isNotEmpty()) {
                    item(key = "header-completed") { SectionHeader("已回本 🎉") }
                    items(state.completed, key = { it.goal.id }) { GoalCard(it, onClick = { onOpenGoal(it.goal.id) }) }
                }

                if (state.archived.isNotEmpty()) {
                    item(key = "header-archived") {
                        TextButton(onClick = { showArchived = !showArchived }) {
                            Text("已封存（${state.archived.size}）")
                            Icon(
                                if (showArchived) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    }
                    if (showArchived) {
                        items(state.archived, key = { it.goal.id }) { GoalCard(it, onClick = { onOpenGoal(it.goal.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(onAddGoal: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🧥💸", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text("還沒有回本目標", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "買了覺得太貴的東西？把它加進來，\n每天少花一點，一點一點省回來！",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddGoal) { Text("新增第一個目標") }
    }
}

@Composable
private fun AllDoneCard(onAddGoal: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("全部都省回來了！", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAddGoal) { Text("新增下一個目標") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SummaryCard(summary: Summary) {
    val progress = summary.progress ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("總共已省回", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatMoney(summary.totalSaved),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    " / ${formatMoney(summary.totalAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            GoalProgressBar(progress)
            Spacer(Modifier.height(8.dp))
            Text(
                "${progress.percentText()} · ${summary.goalCount} 個目標，${summary.completedCount} 個已回本",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GoalCard(row: GoalWithSaved, onClick: () -> Unit) {
    val progress = row.progress()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    progress.percentText(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            GoalProgressBar(progress)
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "已省 ${formatMoney(row.saved)} / ${formatMoney(row.goal.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val status = when {
                    row.goal.status == GoalStatus.ARCHIVED -> "已封存"
                    progress.overflow > 0 -> "超出 ${formatMoney(progress.overflow)}"
                    progress.isCompleted -> "已回本"
                    else -> "還差 ${formatMoney(progress.remaining)}"
                }
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
