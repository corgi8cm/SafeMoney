package io.github.corgi8cm.safemoney.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.corgi8cm.safemoney.ui.common.formatMoney

/** 長條圖的一根長條。 */
data class ChartBar(val label: String, val value: Long, val description: String)

/**
 * 簡單的長條圖（不依賴圖表函式庫）。點一下長條會選取它，由呼叫端顯示該期間的金額。
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = bars.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    val barColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            val selected = index == selectedIndex
            val color = when {
                selectedIndex == null || selected -> barColor
                else -> dimColor
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(if (selected) null else index) },
                    )
                    .semantics { contentDescription = bar.description },
            ) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor.copy(alpha = 0.4f)),
                ) {
                    if (bar.value > 0) {
                        val fraction = (bar.value.toFloat() / max).coerceIn(0.03f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(color),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                )
            }
        }
    }
}

/** 一列水平比例條：名稱、金額與佔比。 */
@Composable
fun ShareRow(label: String, amount: Long, total: Long, modifier: Modifier = Modifier) {
    val fraction = if (total > 0) amount.toFloat() / total else 0f
    Column(modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(formatMoney(amount), style = MaterialTheme.typography.bodyMedium)
            Text(
                " · ${Math.round(fraction * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.secondary),
            )
        }
    }
}
