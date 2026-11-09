package io.github.corgi8cm.safemoney.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.data.MAX_REASON_LENGTH
import io.github.corgi8cm.safemoney.data.db.RecurringSaving
import io.github.corgi8cm.safemoney.domain.Recurrence
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.DateField
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import io.github.corgi8cm.safemoney.ui.common.MoneyField
import io.github.corgi8cm.safemoney.ui.common.display
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import java.time.DayOfWeek
import java.time.LocalDate

/** 「每天・2026/11/10 起，沒有結束日」 */
fun RecurringSaving.scheduleText(today: LocalDate = LocalDate.now()): String {
    val range = if (endDate == null) "${startDate.display()} 起，沒有結束日" else "${startDate.display()} ～ ${endDate.display()}"
    val ended = if (Recurrence.isEnded(this, today)) "（已結束）" else ""
    return "${Recurrence.describe(days)}・$range$ended"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: RecurringListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val hasActiveGoal by viewModel.hasActiveGoal.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current

    Scaffold(
        snackbarHost = { SnackbarHost(messenger.hostState) },
        topBar = {
            TopAppBar(
                title = { Text("定期省錢") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasActiveGoal) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("新增") },
                )
            }
        },
    ) { padding ->
        val list = rules ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                Text(
                    "設定好之後，每到那一天一開始，App 裡就會跳出通知；關掉通知就記下一筆。" +
                        "錢會先補滿最早的目標，超出的再給下一個。哪天沒做到，到紀錄裡刪掉那一筆就好。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                if (!hasActiveGoal) {
                    Text(
                        "目前沒有還沒回本的目標，先回首頁新增目標，才能設定定期省錢。",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                HorizontalDivider()
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "還沒有定期省錢。例如「平日不買咖啡，每次省 60 元」。",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(list, key = { it.id }) { rule ->
                ListItem(
                    headlineContent = { Text("${rule.reason}　${formatMoney(rule.amount)}") },
                    supportingContent = { Text(rule.scheduleText()) },
                    modifier = Modifier.clickable { onOpen(rule.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringEditScreen(
    onBack: () -> Unit,
    viewModel: RecurringEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val quickReasons by viewModel.quickReasons.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.done) {
        if (viewModel.done) onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(messenger.hostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "編輯定期省錢" else "新增定期省錢") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (viewModel.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "移除這個定期省錢")
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
            OutlinedTextField(
                value = viewModel.reason,
                onValueChange = { viewModel.reason = it.take(MAX_REASON_LENGTH) },
                label = { Text("省什麼") },
                placeholder = { Text("例：不買咖啡") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickReasons.forEach { quick ->
                    SuggestionChip(onClick = { viewModel.reason = quick }, label = { Text(quick) })
                }
            }

            MoneyField(
                value = viewModel.amountText,
                onValueChange = { viewModel.amountText = it },
                label = "每次省下多少",
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("哪幾天", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("每天" to Recurrence.EVERY_DAY, "平日" to Recurrence.WEEKDAYS, "週末" to Recurrence.WEEKENDS)
                    .forEach { (label, value) ->
                        FilterChip(
                            selected = viewModel.days == value,
                            onClick = { viewModel.days = value },
                            label = { Text(label) },
                        )
                    }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = Recurrence.includes(viewModel.days, day),
                        onClick = { viewModel.days = Recurrence.toggle(viewModel.days, day) },
                        label = { Text("週" + Recurrence.dayName(day)) },
                    )
                }
            }

            if (viewModel.started) {
                Text(
                    "從 ${viewModel.startDate.display()} 開始（已經開始，不能改開始日）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                DateField(
                    date = viewModel.startDate,
                    onDateChange = viewModel::onStartDateChange,
                    label = "開始日",
                    notBefore = viewModel.today,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("設定結束日", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (viewModel.hasEndDate) "到結束日為止" else "不設定就一直持續",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = viewModel.hasEndDate, onCheckedChange = { viewModel.hasEndDate = it })
            }
            if (viewModel.hasEndDate) {
                DateField(
                    date = viewModel.endDate,
                    onDateChange = { viewModel.endDate = it },
                    label = "結束日",
                    notBefore = maxOf(viewModel.today, viewModel.startDate),
                )
            }

            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::save,
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("移除這個定期省錢？") },
            text = { Text("已經記下的紀錄都會保留，只是之後不會再跳通知。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(messenger::showUndo)
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
