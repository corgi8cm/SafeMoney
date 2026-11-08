package io.github.corgi8cm.safemoney.ui.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import kotlin.math.sin
import kotlin.random.Random

/**
 * 回本慶祝：彩帶動畫 + 「XX 省回來了！」。
 * 有超額時讓使用者選一個進行中的目標轉過去，或直接略過。
 */
@Composable
fun CelebrationDialog(
    ui: CelebrationUi,
    busy: Boolean,
    onTransfer: (toGoalId: Long) -> Unit,
    onNext: () -> Unit,
) {
    Dialog(
        onDismissRequest = onNext,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // key 讓每個新目標都重新撒一次彩帶
            Confetti(key = ui.goal.goal.id, modifier = Modifier.fillMaxSize())
            CelebrationCard(ui, busy, onTransfer, onNext)
        }
    }
}

@Composable
private fun CelebrationCard(
    ui: CelebrationUi,
    busy: Boolean,
    onTransfer: (Long) -> Unit,
    onNext: () -> Unit,
) {
    val pop = remember(ui.goal.goal.id) { Animatable(0.6f) }
    LaunchedEffect(ui.goal.goal.id) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    var targetId by rememberSaveable(ui.goal.goal.id) { mutableStateOf(ui.transferTargets.firstOrNull()?.goal?.id) }
    val canTransfer = ui.overflow > 0 && ui.transferTargets.isNotEmpty()

    Card(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "「${ui.goal.goal.name}」省回來了！",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatMoney(ui.goal.goal.amount)} 全部靠省錢賺回來，太厲害了！",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (canTransfer) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "還多省了 ${formatMoney(ui.overflow)}，要轉給哪個目標？",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    ui.transferTargets.forEach { row ->
                        val selected = row.goal.id == targetId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected, onClick = { targetId = row.goal.id }, role = Role.RadioButton)
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(row.goal.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNext, enabled = !busy) { Text("先不用") }
                    Button(
                        onClick = { targetId?.let(onTransfer) },
                        enabled = !busy && targetId != null,
                    ) { Text("轉過去") }
                }
            } else {
                if (ui.overflow > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "還多省了 ${formatMoney(ui.overflow)}！新增下一個目標後可以從目標頁轉過去。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onNext, enabled = !busy) { Text("太棒了") }
            }
        }
    }
}

private val CONFETTI_COLORS = listOf(
    Color(0xFFF9A825), Color(0xFF2E7D5B), Color(0xFFE53935),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFFF7043),
)

private class Piece(
    val x: Float, // 0..1 水平位置
    val delay: Float, // 0..0.3 起跑延遲
    val speed: Float, // 下落速度倍率
    val size: Float, // dp
    val spin: Float, // 旋轉圈數
    val sway: Float, // 左右搖擺幅度（0..1 寬度）
    val color: Color,
)

/** 從畫面上方灑下的彩帶，約 3 秒落完。 */
@Composable
private fun Confetti(key: Any, modifier: Modifier = Modifier) {
    val pieces = remember(key) {
        val random = Random(key.hashCode())
        List(90) {
            Piece(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.3f,
                speed = 0.7f + random.nextFloat() * 0.6f,
                size = 6f + random.nextFloat() * 6f,
                spin = 1f + random.nextFloat() * 3f,
                sway = 0.02f + random.nextFloat() * 0.05f,
                color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
            )
        }
    }
    val time = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        time.animateTo(1f, tween(durationMillis = 3_000, easing = LinearEasing))
    }

    Canvas(modifier) {
        val t = time.value
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f) * p.speed
            if (local <= 0f || local > 1.1f) return@forEach
            val y = -40f + local * (size.height + 80f)
            val x = (p.x + sin(local * 12f + p.x * 10f) * p.sway) * size.width
            val w = p.size.dp.toPx()
            val h = w * 0.5f
            rotate(degrees = local * 360f * p.spin, pivot = Offset(x, y)) {
                drawRect(color = p.color, topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h))
            }
        }
    }
}
