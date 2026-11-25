package io.github.corgi8cm.safemoney.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 一條提示：標題、說明。 */
data class SavingTip(val title: String, val body: String)

val COUNTS_AS_SAVING = listOf(
    SavingTip("原本習慣會花、這次沒花", "例如每天都買的飲料沒買、午餐改帶便當、走路不搭車。記平常會花的金額。"),
    SavingTip("本來就要買的東西剛好打折", "記折掉的金額（原價減掉實付）。"),
    SavingTip("換成比較便宜的選擇", "例如本來要買 120 元的，改買 80 元的，記差額 40 元。"),
)

val NOT_SAVING = listOf(
    SavingTip("想買但沒買的東西", "本來就不一定會花，記了會讓數字灌水。"),
    SavingTip("只是延後買", "之後還是會花，錢沒有真的省下來。"),
    SavingTip("為了湊折扣多買的東西", "多花的錢比折掉的還多。"),
)

const val SAVING_RULE_OF_THUMB = "問自己：「如果沒有這個決定，這筆錢今天一定會花掉嗎？」答案是「會」，就算省錢。"

/** 上方列的問號按鈕（圖示庫沒有問號，自己畫一個圓圈加「?」）。 */
@Composable
fun TipsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "怎樣算省錢" }) {
        val color = LocalContentColor.current
        Box(
            Modifier.size(22.dp).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingTipsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("怎樣算省錢？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TipSection("✅ 可以算省錢", COUNTS_AS_SAVING)
            TipSection("❌ 不建議算省錢", NOT_SAVING)
            Text("💡 判斷方法", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(SAVING_RULE_OF_THUMB, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TipSection(heading: String, tips: List<SavingTip>) {
    Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    tips.forEach { tip ->
        Column(Modifier.padding(start = 8.dp)) {
            Text(tip.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                tip.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
