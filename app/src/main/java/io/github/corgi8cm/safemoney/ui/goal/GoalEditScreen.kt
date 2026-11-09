package io.github.corgi8cm.safemoney.ui.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.DateField
import io.github.corgi8cm.safemoney.ui.common.GOAL_COLORS
import io.github.corgi8cm.safemoney.ui.common.GOAL_COLOR_NAMES
import io.github.corgi8cm.safemoney.ui.common.GOAL_EMOJIS
import io.github.corgi8cm.safemoney.ui.common.GoalAvatar
import io.github.corgi8cm.safemoney.ui.common.MoneyField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    LaunchedEffect(viewModel.done) { if (viewModel.done) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEdit) "編輯目標" else "新增目標") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                value = viewModel.name,
                onValueChange = { viewModel.name = it.take(40) },
                label = { Text("買了什麼") },
                placeholder = { Text("例：大衣") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(
                value = viewModel.amountText,
                onValueChange = { viewModel.amountText = it },
                label = "花了多少（要省回來的金額）",
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                date = viewModel.purchaseDate,
                onDateChange = { viewModel.purchaseDate = it },
                label = "購買日期",
            )
            GoalStylePicker(
                name = viewModel.name,
                color = viewModel.color,
                emoji = viewModel.emoji,
                onColorChange = { viewModel.color = it },
                onEmojiChange = { viewModel.emoji = it },
            )
            OutlinedTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it.take(200) },
                label = { Text("備註（選填）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = viewModel::save,
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalStylePicker(
    name: String,
    color: Int?,
    emoji: String?,
    onColorChange: (Int?) -> Unit,
    onEmojiChange: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalAvatar(name = name.ifBlank { "?" }, emoji = emoji, color = color, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Text("顏色與圖示（選填）", style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleOption(selected = color == null, label = "預設顏色", onClick = { onColorChange(null) }) {
                Text("預設", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
            }
            GOAL_COLORS.forEachIndexed { i, c ->
                StyleOption(selected = color == c, label = "${GOAL_COLOR_NAMES[i]}色", onClick = { onColorChange(c) }) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(c)))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleOption(selected = emoji == null, label = "不用圖示", onClick = { onEmojiChange(null) }) {
                Text("無", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
            GOAL_EMOJIS.forEach { e ->
                StyleOption(selected = emoji == e, label = e, onClick = { onEmojiChange(e) }) {
                    Text(e, fontSize = with(LocalDensity.current) { 20.dp.toSp() })
                }
            }
        }
    }
}

@Composable
private fun StyleOption(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, border, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}
