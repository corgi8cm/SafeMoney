package io.github.corgi8cm.safemoney.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.domain.PendingRecurring
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationDialog
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.shortWithWeekday
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/**
 * 放在 App 最外層：App 開著時每到半夜 12 點、以及每次回到 App 時檢查定期省錢，
 * 有到期的就跳出通知；關掉通知（按按鈕或點旁邊）就記下來。
 */
@Composable
fun RecurringPromptHost(
    viewModel: RecurringPromptViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = LocalDateTime.now()
                viewModel.setToday(now.toLocalDate())
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
            }
        }
    }

    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val hasActiveGoal by viewModel.hasActiveGoal.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            messenger.show(message)
            viewModel.messageShown()
        }
    }

    val celebration = viewModel.celebration
    celebration.current?.let { ui ->
        CelebrationDialog(ui = ui, busy = celebration.busy, onTransfer = celebration::transfer, onNext = celebration::next)
    }

    viewModel.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::noticeShown,
            title = { Text("定期省錢已移除") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = viewModel::noticeShown) { Text("知道了") } },
        )
    }

    if (pending.isNotEmpty() && hasActiveGoal == true && !viewModel.busy) {
        RecurringPromptDialog(pending, onDismiss = viewModel::dismiss)
    }
}

@Composable
fun RecurringPromptDialog(pending: List<PendingRecurring>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定期省錢") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pending.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${item.date.shortWithWeekday()} ${item.rule.reason}",
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatMoney(item.rule.amount))
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "共 ${pending.size} 筆，${formatMoney(pending.sumOf { it.rule.amount })}",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "關掉這個通知就會記下來。哪天沒做到，再到紀錄裡刪掉那一筆就好。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉並記下") } },
    )
}
