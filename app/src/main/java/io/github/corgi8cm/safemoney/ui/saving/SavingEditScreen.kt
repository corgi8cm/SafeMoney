package io.github.corgi8cm.safemoney.ui.saving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.progress
import io.github.corgi8cm.safemoney.domain.SavingForm
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationDialog
import io.github.corgi8cm.safemoney.ui.common.DateField
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import io.github.corgi8cm.safemoney.ui.common.MoneyField
import io.github.corgi8cm.safemoney.ui.common.formatMoney

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavingEditScreen(
    onBack: () -> Unit,
    onManageReasons: () -> Unit,
    viewModel: SavingEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val quickReasons by viewModel.quickReasons.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val celebration = viewModel.celebration
    LaunchedEffect(viewModel.done, celebration.finished) {
        if (viewModel.done || celebration.finished) onBack()
    }
    celebration.current?.let { ui ->
        CelebrationDialog(
            ui = ui,
            busy = celebration.busy,
            onTransfer = celebration::transfer,
            onNext = celebration::next,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(messenger.hostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "編輯省錢紀錄" else "記一筆省錢") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (viewModel.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "刪除這筆紀錄")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyField(
                value = viewModel.amountText,
                onValueChange = { viewModel.amountText = it },
                label = "省下多少",
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = viewModel.reason,
                onValueChange = { viewModel.reason = it.take(40) },
                label = { Text("怎麼省的（選填）") },
                placeholder = { Text("例：沒買飲料") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickReasons.forEach { quick ->
                    SuggestionChip(onClick = { viewModel.reason = quick }, label = { Text(quick) })
                }
                AssistChip(
                    onClick = onManageReasons,
                    label = { Text("管理") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                )
            }

            DateField(date = viewModel.date, onDateChange = { viewModel.date = it }, label = "日期")

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("分給哪個目標", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("拆分", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = viewModel.splitMode, onCheckedChange = viewModel::setSplit)
            }

            if (goals.isEmpty()) {
                Text(
                    "還沒有可以分配的目標，請先回首頁新增目標。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (viewModel.splitMode) {
                SplitGoalList(goals, viewModel)
            } else {
                SingleGoalList(goals, viewModel.selectedGoalId, onSelect = { viewModel.selectedGoalId = it })
            }

            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::save,
                enabled = !viewModel.busy && goals.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這筆紀錄？") },
            text = { Text("分給各目標的金額都會一起扣掉。刪除後可以馬上按「復原」救回來。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(messenger::showUndo)
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SingleGoalList(goals: List<GoalWithSaved>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Column {
        goals.forEach { row ->
            val selected = row.goal.id == selectedId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, onClick = { onSelect(row.goal.id) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                GoalLabel(row, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SplitGoalList(goals: List<GoalWithSaved>, viewModel: SavingEditViewModel) {
    val parts = viewModel.splitParts()
    val total = SavingForm.parseAmount(viewModel.amountText) ?: 0L
    val allocated = SavingForm.allocatedSum(parts)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        goals.forEach { row ->
            val id = row.goal.id
            val checked = viewModel.splitAmounts.containsKey(id)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .toggleable(value = checked, onValueChange = { viewModel.toggleSplitGoal(id) }, role = Role.Checkbox)
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    GoalLabel(row, Modifier.weight(1f))
                }
                Spacer(Modifier.width(8.dp))
                MoneyField(
                    value = viewModel.splitAmounts[id].orEmpty(),
                    onValueChange = { viewModel.splitAmounts[id] = it },
                    label = "金額",
                    enabled = checked,
                    modifier = Modifier.width(130.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val matches = parts.isNotEmpty() && allocated == total && total > 0
            Text(
                "已分配 ${formatMoney(allocated)} / ${formatMoney(total)}",
                color = if (matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::splitEvenly) { Text("平均分配") }
        }
    }
}

@Composable
private fun GoalLabel(row: GoalWithSaved, modifier: Modifier = Modifier) {
    val progress = row.progress()
    Column(modifier) {
        Text(row.goal.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = when {
            row.goal.status == GoalStatus.ARCHIVED -> "已封存"
            progress.isCompleted -> "已回本"
            else -> "還差 ${formatMoney(progress.remaining)}"
        }
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
