package io.github.corgi8cm.safemoney.ui.goal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.data.db.progress
import io.github.corgi8cm.safemoney.data.db.total
import io.github.corgi8cm.safemoney.domain.Forecast
import java.time.LocalDate
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.GoalProgressBar
import io.github.corgi8cm.safemoney.ui.common.describe
import io.github.corgi8cm.safemoney.ui.common.display
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.percentText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEdit: (goalId: Long) -> Unit,
    onAddSaving: (goalId: Long) -> Unit,
    onEditSaving: (savingId: Long) -> Unit,
    viewModel: GoalDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showTransfer by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    // 目標被刪除（或不存在）時離開這頁
    LaunchedEffect(state.loading, state.goal) {
        if (!state.loading && state.goal == null && !leaving) {
            leaving = true
            onBack()
        }
    }

    val row = state.goal
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(row?.goal?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (row != null) {
                        IconButton(onClick = { onEdit(viewModel.goalId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "編輯目標")
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (row.goal.status == GoalStatus.ARCHIVED) "取消封存" else "封存") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.toggleArchived()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("刪除", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (row != null && row.goal.status != GoalStatus.ARCHIVED) {
                ExtendedFloatingActionButton(
                    onClick = { onAddSaving(viewModel.goalId) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("記一筆") },
                )
            }
        },
    ) { padding ->
        if (row == null) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item(key = "header") {
                ProgressHeader(
                    row = row,
                    forecast = state.forecast,
                    today = state.today,
                    canTransfer = state.transferTargets.isNotEmpty() && !viewModel.transferring,
                    onTransferClick = { showTransfer = true },
                )
            }
            item(key = "records-title") {
                Text(
                    "省錢紀錄（${state.savings.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            if (state.savings.isEmpty()) {
                item(key = "records-empty") {
                    Text(
                        "還沒有紀錄。今天少喝一杯飲料，就從這裡開始吧！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.savings, key = { it.saving.id }) { item ->
                SavingRow(item, viewModel.goalId, onClick = { onEditSaving(item.saving.id) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    if (showTransfer && row != null) {
        TransferDialog(
            overflow = row.progress().overflow,
            targets = state.transferTargets,
            onConfirm = { toGoalId ->
                showTransfer = false
                viewModel.transferOverflow(toGoalId)
            },
            onDismiss = { showTransfer = false },
        )
    }

    val celebration = viewModel.celebration
    celebration.current?.let { ui ->
        CelebrationDialog(
            ui = ui,
            busy = celebration.busy,
            onTransfer = celebration::transfer,
            onNext = celebration::next,
        )
    }

    if (confirmDelete && row != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除「${row.goal.name}」？") },
            text = { Text("這個目標與分給它的省錢金額都會刪除，無法復原。\n拆分給其他目標的部分會保留。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TransferDialog(
    overflow: Long,
    targets: List<GoalWithSaved>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(targets.firstOrNull()?.goal?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("轉移多省的 ${formatMoney(overflow)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "會從最近的省錢紀錄改分給選擇的目標，紀錄的總金額不變，這個目標會剛好停在 100%。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                targets.forEach { target ->
                    val isSelected = target.goal.id == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(isSelected, onClick = { selected = target.goal.id }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(target.goal.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "還差 ${formatMoney(target.progress().remaining)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null) { Text("轉過去") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ProgressHeader(
    row: GoalWithSaved,
    forecast: Forecast?,
    today: LocalDate,
    canTransfer: Boolean,
    onTransferClick: () -> Unit,
) {
    val progress = row.progress()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatMoney(row.saved),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    " / ${formatMoney(row.goal.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(progress.percentText(), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            GoalProgressBar(progress)
            Spacer(Modifier.height(8.dp))
            val statusText = when {
                row.goal.status == GoalStatus.ARCHIVED -> "已封存"
                progress.isCompleted && progress.overflow > 0 ->
                    "已回本 🎉 還多省了 ${formatMoney(progress.overflow)}"
                progress.isCompleted -> "已回本 🎉"
                else -> "還差 ${formatMoney(progress.remaining)}"
            }
            Text(statusText, style = MaterialTheme.typography.bodyLarge)
            forecast?.describe(today)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "購買日期 ${row.goal.purchaseDate.display()}",
                style = MaterialTheme.typography.bodySmall,
            )
            row.goal.note?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            if (row.goal.status != GoalStatus.ARCHIVED && progress.overflow > 0) {
                Spacer(Modifier.height(12.dp))
                if (canTransfer) {
                    OutlinedButton(onClick = onTransferClick, modifier = Modifier.fillMaxWidth()) {
                        Text("把多省的 ${formatMoney(progress.overflow)} 轉給其他目標")
                    }
                } else {
                    Text(
                        "新增其他目標後，就能把多省的錢轉過去。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingRow(item: SavingWithAllocations, goalId: Long, onClick: () -> Unit) {
    val forThisGoal = item.allocations.firstOrNull { it.goalId == goalId }?.amount ?: 0L
    val isSplit = item.allocations.size > 1
    ListItem(
        headlineContent = {
            Text(item.saving.reason.ifBlank { "（沒有填原因）" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val split = if (isSplit) " · 拆分，共 ${formatMoney(item.total())}" else ""
            Text(item.saving.date.display() + split)
        },
        trailingContent = {
            Text(
                "+${formatMoney(forThisGoal)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
