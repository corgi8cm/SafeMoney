package io.github.corgi8cm.safemoney.ui.goal

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.DateField
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
