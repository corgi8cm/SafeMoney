package io.github.corgi8cm.safemoney.ui.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class IntroPage(val emoji: String, val title: String, val body: String)

val INTRO_PAGES = listOf(
    IntroPage(
        "🧥💸",
        "買貴了？省回來！",
        "把覺得買太貴的東西加成「回本目標」，例如一件 3,000 元的外套。",
    ),
    IntroPage(
        "🥤",
        "少花一點，就記一筆",
        "今天沒買飲料省下 60 元？記下來算進目標。一筆也可以分給好幾個目標。",
    ),
    IntroPage(
        "📈",
        "看著進度一點一點回本",
        "首頁看得到每個目標的進度與預計回本日；還有統計、每日提醒與桌面小工具。",
    ),
    IntroPage(
        "🔒",
        "資料在你自己手上",
        "資料只存在手機裡。想換手機或多支手機一起用，可以在設定裡備份，或用自己的 Google 帳號同步。",
    ),
)

/**
 * 新手說明：左右滑動的幾頁介紹。[onFinish] 在按「開始使用」或「略過」時呼叫。
 */
@Composable
fun IntroPager(onFinish: () -> Unit, modifier: Modifier = Modifier, finishLabel: String = "開始使用") {
    val pagerState = rememberPagerState { INTRO_PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == INTRO_PAGES.lastIndex

    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onFinish) { Text("略過") }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                IntroPageContent(INTRO_PAGES[index])
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = "第 ${pagerState.currentPage + 1} 頁，共 ${INTRO_PAGES.size} 頁" },
            ) {
                repeat(INTRO_PAGES.size) { i ->
                    val color = if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                    Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
                }
            }
            Button(
                onClick = {
                    if (last) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            ) { Text(if (last) finishLabel else "下一步") }
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage) {
    // 內容可以捲動：字體放很大時也看得到全部文字
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
    ) {
        // 圖示是裝飾用，大小固定不跟著字體放大；讀螢幕時略過
        val emojiSize = with(LocalDensity.current) { 64.dp.toSp() }
        Text(page.emoji, fontSize = emojiSize, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.height(24.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
