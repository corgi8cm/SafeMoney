package io.github.corgi8cm.safemoney.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import io.github.corgi8cm.safemoney.ui.common.LocalAppMessenger
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.corgi8cm.safemoney.domain.FORECAST_WINDOW_DAYS
import io.github.corgi8cm.safemoney.domain.Forecast
import io.github.corgi8cm.safemoney.domain.PeriodTotal
import io.github.corgi8cm.safemoney.ui.AppViewModelProvider
import io.github.corgi8cm.safemoney.ui.common.describe
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.shortWithWeekday

private enum class ChartRange { DAILY, WEEKLY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current

    Scaffold(
        snackbarHost = { SnackbarHost(messenger.hostState) },
        topBar = {
            TopAppBar(
                title = { Text("省錢統計") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding))
            !state.hasSavings -> EmptyStats(Modifier.padding(padding))
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(StatsMode.OVERVIEW to "總覽", StatsMode.MONTH to "每月回顧", StatsMode.YEAR to "每年回顧")
                        .forEach { (value, label) ->
                            FilterChip(selected = mode == value, onClick = { viewModel.setMode(value) }, label = { Text(label) })
                        }
                }
                val current = review
                if (mode == StatsMode.OVERVIEW || current == null) {
                    OverviewCard(state)
                    TrendCard(state)
                    if (state.reasons.isNotEmpty()) ReasonsCard(state)
                } else {
                    ReviewSection(current, onPrevious = viewModel::previous, onNext = viewModel::next, onError = messenger::show)
                }
            }
        }
    }
}

@Composable
private fun ReviewSection(
    state: ReviewUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onError: (String) -> Unit,
) {
    val review = state.review
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = state.canPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一段")
        }
        Text(
            review.period.label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = state.canNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一段")
        }
    }

    // 存成圖片時，畫下這一塊（含外圍的底色）
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Box(
        Modifier
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .reviewImageBackground(MaterialTheme.colorScheme.background),
    ) {
        ReviewCard(review)
    }
    if (!review.isEmpty) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareReviewImage(context, bitmap, "分享${review.period.label}回顧")
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        onError("無法產生圖片：${e.message ?: "未知錯誤"}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("存成圖片分享")
        }
    }
}

@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📊", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text("還沒有省錢紀錄", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "記下第一筆省錢之後，這裡會顯示每天、每週的趨勢和各原因的佔比。",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewCard(state: StatsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                StatTile("累計省下", formatMoney(state.totalSaved), Modifier.weight(1f))
                StatTile("近 $FORECAST_WINDOW_DAYS 天", formatMoney(state.recentTotal), Modifier.weight(1f))
                StatTile("連續記錄", "${state.streak} 天", Modifier.weight(1f))
            }
            val forecast = state.overallForecast
            if (forecast != null) {
                Spacer(Modifier.height(16.dp))
                val text = if (forecast == Forecast.Completed) {
                    "進行中的目標都回本了 🎉"
                } else {
                    "進行中的目標還差 ${formatMoney(state.activeRemaining)}。\n" +
                        forecast.describe(state.today, subject = "全部回本")
                }
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrendCard(state: StatsUiState) {
    var range by rememberSaveable { mutableStateOf(ChartRange.DAILY) }
    var selected by rememberSaveable(range) { mutableStateOf<Int?>(null) }
    val periods: List<PeriodTotal> = if (range == ChartRange.DAILY) state.daily else state.weekly

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = range == ChartRange.DAILY,
                    onClick = { range = ChartRange.DAILY },
                    label = { Text("每日（近 $DAILY_CHART_DAYS 天）") },
                )
                FilterChip(
                    selected = range == ChartRange.WEEKLY,
                    onClick = { range = ChartRange.WEEKLY },
                    label = { Text("每週（近 $WEEKLY_CHART_WEEKS 週）") },
                )
            }
            Spacer(Modifier.height(8.dp))

            val index = selected?.takeIf { it in periods.indices }
            val headline = if (index != null) {
                val p = periods[index]
                val what = if (range == ChartRange.DAILY) p.start.shortWithWeekday() else "${weekLabel(p)} 這週"
                "$what 省了 ${formatMoney(p.amount)}"
            } else {
                val what = if (range == ChartRange.DAILY) "近 $DAILY_CHART_DAYS 天" else "近 $WEEKLY_CHART_WEEKS 週"
                "$what 共省 ${formatMoney(periods.sumOf { it.amount })}"
            }
            Text(headline, style = MaterialTheme.typography.titleMedium)
            Text(
                "點長條看那段期間省了多少",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            val bars = periods.map { p ->
                if (range == ChartRange.DAILY) {
                    ChartBar(
                        label = p.start.dayOfMonth.toString(),
                        value = p.amount,
                        description = "${p.start.shortWithWeekday()} ${formatMoney(p.amount)}",
                    )
                } else {
                    ChartBar(
                        label = weekLabel(p),
                        value = p.amount,
                        description = "${weekLabel(p)} 起的一週 ${formatMoney(p.amount)}",
                    )
                }
            }
            BarChart(bars = bars, selectedIndex = index, onSelect = { selected = it })
        }
    }
}

private fun weekLabel(p: PeriodTotal) = "${p.start.monthValue}/${p.start.dayOfMonth}"

@Composable
private fun ReasonsCard(state: StatsUiState) {
    val total = state.reasons.sumOf { it.amount }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("省在哪裡", style = MaterialTheme.typography.titleMedium)
            Text(
                "所有紀錄依原因加總",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            state.reasons.forEach { ShareRow(it.reason, it.amount, total) }
        }
    }
}
