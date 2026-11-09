package io.github.corgi8cm.safemoney.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.corgi8cm.safemoney.domain.CsvExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.domain.RecordFilter
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.shortWithWeekday
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    onBack: () -> Unit,
    onOpenSaving: (savingId: Long) -> Unit,
    viewModel: RecordsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CsvExport.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val (text, count) = viewModel.csvForCurrentFilter()
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("無法開啟檔案")
                        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    messenger.show("已匯出 $count 筆紀錄")
                } catch (e: IOException) {
                    messenger.show("匯出失敗：${e.message ?: "未知錯誤"}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("所有紀錄") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.rows.isNotEmpty()) {
                        IconButton(onClick = { csvLauncher.launch(viewModel.csvFileName()) }) {
                            Icon(Icons.Filled.Share, contentDescription = "把目前篩選的紀錄匯出成試算表")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalAppMessenger.current.hostState) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "filters") {
                Filters(filter, state.reasons, viewModel::setMonth, viewModel::setReason)
            }
            item(key = "total") {
                val count = state.rows.size
                Text(
                    if (count == 0) "沒有符合的紀錄" else "共 $count 筆，省了 ${formatMoney(state.total)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (!state.hasAnyRecord) {
                item(key = "empty") {
                    Text(
                        "還沒有任何省錢紀錄。",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            var lastDate: java.time.LocalDate? = null
            state.rows.forEach { row ->
                if (row.date != lastDate) {
                    lastDate = row.date
                    item(key = "date-${row.date}") {
                        Text(
                            row.date.shortWithWeekday() + if (row.date.year != java.time.LocalDate.now().year) " ${row.date.year}" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
                item(key = row.savingId) {
                    ListItem(
                        headlineContent = { Text(row.reason, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(row.goalsLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Text(
                                "+${formatMoney(row.total)}",
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
}

@Composable
private fun Filters(
    filter: RecordFilter,
    reasons: List<String>,
    onMonth: (YearMonth?) -> Unit,
    onReason: (String?) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val month = filter.month
            IconButton(onClick = { onMonth((month ?: YearMonth.now()).minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上個月")
            }
            Text(
                if (month == null) "全部期間" else "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onMonth((month ?: YearMonth.now()).plusMonths(1)) },
                enabled = month == null || month < YearMonth.now(),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下個月")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.month == null,
                onClick = { onMonth(if (filter.month == null) YearMonth.now() else null) },
                label = { Text("全部期間") },
            )
            var open by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = filter.reason != null,
                    onClick = { open = true },
                    label = { Text(filter.reason ?: "所有原因", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("所有原因") }, onClick = {
                        open = false
                        onReason(null)
                    })
                    reasons.forEach { reason ->
                        DropdownMenuItem(text = { Text(reason) }, onClick = {
                            open = false
                            onReason(reason)
                        })
                    }
                }
            }
        }
    }
}
