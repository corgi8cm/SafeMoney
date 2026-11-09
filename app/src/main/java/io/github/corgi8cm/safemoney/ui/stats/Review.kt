package io.github.corgi8cm.safemoney.ui.stats

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.corgi8cm.safemoney.domain.PeriodReview
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.shortWithWeekday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

/** 首頁「上個月回顧」卡片按 ✕ 收起的是哪個月。 */
class ReviewCardSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("review", Context.MODE_PRIVATE)

    private val _dismissed = MutableStateFlow(
        prefs.getString(KEY_DISMISSED, null)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    )
    val dismissed: StateFlow<YearMonth?> = _dismissed.asStateFlow()

    fun dismiss(month: YearMonth) {
        prefs.edit().putString(KEY_DISMISSED, month.toString()).apply()
        _dismissed.value = month
    }

    private companion object {
        const val KEY_DISMISSED = "dismissed_month"
    }
}

/**
 * 一段期間的回顧內容。存成圖片分享時，整張卡片（含最下面的 App 名稱）就是圖片的內容。
 */
@Composable
fun ReviewCard(review: PeriodReview, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${review.period.label}回顧", style = MaterialTheme.typography.titleMedium)
            if (review.isEmpty) {
                Text("這段期間沒有省錢紀錄", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "省了 ${formatMoney(review.total)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("${review.activeDays} 天有記錄，共 ${review.count} 筆", style = MaterialTheme.typography.bodyMedium)
                review.comparisonText(::formatMoney)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                review.bestDay?.let { best ->
                    ReviewLine("省最多的一天", "${best.start.shortWithWeekday()} ${formatMoney(best.amount)}")
                }
                if (review.topReasons.isNotEmpty()) {
                    Text("最有效的原因", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    review.topReasons.forEachIndexed { i, r ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${i + 1}. ${r.reason}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatMoney(r.amount))
                        }
                    }
                }
                if (review.completedGoals.isNotEmpty()) {
                    Text("回本的目標 🎉", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    Text(review.completedGoals.joinToString("、"))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "省回來 SafeMoney",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(value)
    }
}

/** 把回顧圖片存到暫存資料夾，交給系統的分享選單（可以傳給朋友、存到相簿或雲端）。 */
suspend fun shareReviewImage(context: Context, bitmap: Bitmap, title: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        File(dir, "review.png").also { f -> f.outputStream().use { soft.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/** 圖片背景（卡片圓角外面）用的底色。 */
fun Modifier.reviewImageBackground(color: androidx.compose.ui.graphics.Color): Modifier = background(color).padding(12.dp)
