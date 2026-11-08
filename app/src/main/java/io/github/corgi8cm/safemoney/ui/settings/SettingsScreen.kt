package io.github.corgi8cm.safemoney.ui.settings

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.remember
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
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }
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
            SectionTitle("關於")
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("省回來 SafeMoney") },
                supportingContent = { Text("版本 ${viewModel.appVersion}") },
            )
        }
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
