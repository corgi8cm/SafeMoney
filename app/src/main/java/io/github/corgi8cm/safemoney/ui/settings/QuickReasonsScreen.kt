package io.github.corgi8cm.safemoney.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.data.MAX_REASON_LENGTH
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.QuickReason
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickReasonsViewModel(private val repository: SafeMoneyRepository) : ViewModel() {
    val reasons: StateFlow<List<QuickReason>?> =
        repository.observeQuickReasons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var message by mutableStateOf<String?>(null)
        private set

    fun messageShown() {
        message = null
    }

    /** 成功時呼叫 onDone，讓畫面清空輸入框或關閉對話框。 */
    fun add(text: String, onDone: () -> Unit) = launchSafely { repository.addQuickReason(text); onDone() }

    fun rename(id: Long, text: String, onDone: () -> Unit) = launchSafely { repository.renameQuickReason(id, text); onDone() }

    fun delete(reason: QuickReason) = launchSafely {
        repository.deleteQuickReason(reason.id)
        message = "已刪除「${reason.text}」"
    }

    fun reset() = launchSafely {
        repository.resetQuickReasons()
        message = "已換回預設的快捷原因"
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IllegalArgumentException) {
                message = e.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReasonsScreen(
    onBack: () -> Unit,
    viewModel: QuickReasonsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val reasons by viewModel.reasons.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var newText by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<QuickReason?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快捷原因") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmReset = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "換回預設")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = reasons ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            modifier = Modifier.imePadding(),
        ) {
            item(key = "hint") {
                Text(
                    "記一筆時點一下就能填入原因。最近 90 天用得越多的會排越前面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item(key = "add") {
                val submit = { if (newText.isNotBlank()) viewModel.add(newText) { newText = "" } }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                ) {
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it.take(MAX_REASON_LENGTH) },
                        label = { Text("新增原因") },
                        placeholder = { Text("例：沒買宵夜") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = submit, enabled = newText.isNotBlank()) { Text("加入") }
                }
            }
            if (list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "清單是空的，記錄頁不會顯示快捷原因。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(list, key = { it.id }) { reason ->
                ListItem(
                    headlineContent = { Text(reason.text) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = reason }) {
                                Icon(Icons.Filled.Edit, contentDescription = "修改「${reason.text}」")
                            }
                            IconButton(onClick = { viewModel.delete(reason) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "刪除「${reason.text}」")
                            }
                        }
                    },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    editing?.let { reason ->
        var text by rememberSaveable(reason.id) { mutableStateOf(reason.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("修改原因") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_REASON_LENGTH) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.rename(reason.id, text) { editing = null } }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("換回預設的快捷原因？") },
            text = { Text("目前清單會被預設的 6 個原因取代。已經記下的紀錄不受影響。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.reset()
                }) { Text("換回預設") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }
}
