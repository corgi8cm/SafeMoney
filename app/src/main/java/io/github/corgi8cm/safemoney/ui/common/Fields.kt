package io.github.corgi8cm.safemoney.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import io.github.corgi8cm.safemoney.domain.Progress
import java.time.LocalDate

private const val MAX_AMOUNT_DIGITS = 9

/** 只接受數字的金額欄位，前面顯示 "$"。 */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(MAX_AMOUNT_DIGITS)
            onValueChange(digits)
        },
        label = { Text(label) },
        prefix = { Text("$") },
        enabled = enabled,
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        modifier = modifier,
    )
}

/** 點一下就跳出日曆選日期，不能選未來的日子。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) showPicker = true }
    }

    OutlinedTextField(
        value = date.display(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "選擇日期") },
        singleLine = true,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
    )

    if (showPicker) {
        val todayMillis = LocalDate.now().toPickerMillis()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toPickerMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(pickerMillisToLocalDate(it)) }
                    showPicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun GoalProgressBar(progress: Progress, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.fraction },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
    )
}
