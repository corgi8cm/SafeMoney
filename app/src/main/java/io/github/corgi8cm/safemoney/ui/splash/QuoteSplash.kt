package io.github.corgi8cm.safemoney.ui.splash

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** 開場動畫的句子（2026/11/09 審核過）。新增句子加在最後面。 */
val SPLASH_QUOTES: List<String> = listOf(
    "省錢歸省錢，該花的還是得花。",
    "荷包不夠，省錢來湊。",
    "今天少買一杯，明天多花一點。",
    "錢包今天也謝謝你。",
    "別人貪婪，我們省錢。",
    "買貴了沒關係，再省就有。",
    "這是一個錢包友善 APP。",
    "省錢不用急，再急也沒你花得快。",
    "自己造的孽自己省回來。",
    "你該不會又花錢了？",
)

/** 隨機挑一句，但不會跟上一次一樣。 */
fun pickQuoteIndex(count: Int, last: Int?, random: Random = Random.Default): Int {
    require(count > 0)
    if (count == 1) return 0
    val candidates = (0 until count).filter { it != last }
    return candidates[random.nextInt(candidates.size)]
}

/** 開場動畫的開關與「上一次是哪一句」。 */
class SplashSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("splash", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    /** 挑這次要顯示的句子，並記住它。 */
    fun nextQuote(): String {
        val last = prefs.getInt(KEY_LAST, -1).takeIf { it in SPLASH_QUOTES.indices }
        val index = pickQuoteIndex(SPLASH_QUOTES.size, last)
        prefs.edit().putInt(KEY_LAST, index).apply()
        return SPLASH_QUOTES[index]
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST = "last_quote"
    }
}

/** 手機設定裡開了「移除動畫」。 */
fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** 整段開場的長度。 */
const val SPLASH_DURATION_MS = 1_500L

/**
 * 開場動畫：回本進度環轉一圈，接著淡入一句話，總共約 1.5 秒；點一下就跳過。
 * [animate] 為 false（手機關掉動畫）時直接顯示整個環和句子。
 */
@Composable
fun QuoteSplash(quote: String, animate: Boolean, onDone: () -> Unit) {
    val done = rememberUpdatedState(onDone)
    val ring = remember { Animatable(if (animate) 0f else 1f) }
    val text = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            ring.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
            text.animateTo(1f, tween(durationMillis = 300))
            delay(SPLASH_DURATION_MS - 1_000)
        } else {
            delay(SPLASH_DURATION_MS)
        }
        done.value()
    }

    val primary = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done.value() }
            .clearAndSetSemantics {
                contentDescription = quote
                role = Role.Button
                onClick(label = "略過") { done.value(); true }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(112.dp)) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(primary.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    primary, -90f, 360f * ring.value, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                quote,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer { alpha = text.value },
            )
        }
    }
}
