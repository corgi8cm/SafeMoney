package io.github.corgi8cm.safemoney.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.corgi8cm.safemoney.reminder.ReminderNotifier
import java.time.LocalTime
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.display
import io.github.corgi8cm.safemoney.ui.common.localDate

private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageReasons: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(ReminderNotifier.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        // 從系統設定回來時更新通知權限狀態
        canNotify = ReminderNotifier.canNotify(context)
        onPauseOrDispose { }
    }
    var pickTime by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        canNotify = ReminderNotifier.canNotify(context)
        if (granted) {
            viewModel.setReminderEnabled(true)
        } else {
            viewModel.showMessage("沒有通知權限就無法提醒，可以到系統設定開啟")
        }
    }
    val onReminderToggle: (Boolean) -> Unit = { on ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canNotify
        if (on && needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminderEnabled(on)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }
    val crashLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let(viewModel::exportCrashLog) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::pickRestore) }

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
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (viewModel.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            SectionTitle("記錄")
            SettingRow(
                icon = Icons.Filled.Edit,
                title = "快捷原因",
                subtitle = "新增、修改或刪除記一筆時的常用原因",
                enabled = true,
                onClick = onManageReasons,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("每日提醒")
            ListItem(
                leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                headlineContent = { Text("提醒我記錄") },
                supportingContent = { Text("今天已經記過就不會打擾") },
                trailingContent = { Switch(checked = reminder.enabled, onCheckedChange = null) },
                modifier = Modifier.clickable { onReminderToggle(!reminder.enabled) },
            )
            SettingRow(
                icon = Icons.Filled.DateRange,
                title = "提醒時間",
                subtitle = "每天 %02d:%02d".format(reminder.time.hour, reminder.time.minute),
                enabled = reminder.enabled,
                onClick = { pickTime = true },
            )
            if (reminder.enabled && !canNotify) {
                ListItem(
                    headlineContent = {
                        Text("通知已被關閉，提醒不會出現", color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = { Text("點這裡到系統設定開啟通知") },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("備份與還原")
            Text(
                "資料只存在這支手機上，解除安裝或換手機前記得先匯出備份。" +
                    "備份檔可以存到雲端硬碟，或傳到新手機再還原。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingRow(
                icon = Icons.Filled.Share,
                title = "匯出備份",
                subtitle = "存成 JSON 檔（目前 ${counts.goals} 個目標、${counts.savings} 筆紀錄）",
                enabled = !viewModel.busy,
                onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
            )
            SettingRow(
                icon = Icons.Filled.Refresh,
                title = "從備份還原",
                subtitle = "選擇備份檔，會取代目前手機上的所有資料",
                enabled = !viewModel.busy,
                onClick = { restoreLauncher.launch(BACKUP_MIME_TYPES) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("錯誤紀錄")
            Text(
                "App 當掉時會把原因記在這支手機上（不會上傳）。回報問題時可以匯出給開發者。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (viewModel.hasCrashLog) {
                SettingRow(
                    icon = Icons.Filled.Share,
                    title = "匯出錯誤紀錄",
                    subtitle = "存成文字檔",
                    enabled = !viewModel.busy,
                    onClick = { crashLogLauncher.launch(viewModel.crashLogFileName()) },
                )
                SettingRow(
                    icon = Icons.Filled.Delete,
                    title = "清除錯誤紀錄",
                    subtitle = "刪除手機上記下的錯誤原因",
                    enabled = !viewModel.busy,
                    onClick = viewModel::clearCrashLog,
                )
            } else {
                ListItem(headlineContent = { Text("目前沒有錯誤紀錄") })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("關於")
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("省回來 SafeMoney") },
                supportingContent = { Text("版本 ${viewModel.appVersion}") },
            )
        }
    }

    if (pickTime) {
        ReminderTimeDialog(
            initial = reminder.time,
            onConfirm = {
                pickTime = false
                viewModel.setReminderTime(it)
            },
            onDismiss = { pickTime = false },
        )
    }

    viewModel.pendingRestore?.let { data ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("從備份還原？") },
            text = {
                Text(
                    "備份檔（${data.exportedAt.localDate().display()} 匯出）內有 " +
                        "${data.goals.size} 個目標、${data.savings.size} 筆紀錄。\n\n" +
                        "還原後，手機上目前的 ${counts.goals} 個目標、${counts.savings} 筆紀錄會被取代，無法復原。",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text("取代並還原", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒時間") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
