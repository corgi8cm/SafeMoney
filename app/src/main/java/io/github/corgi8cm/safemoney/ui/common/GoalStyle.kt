package io.github.corgi8cm.safemoney.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corgi8cm.safemoney.data.db.Goal

/** 目標可選的顏色（ARGB），中間色調，淺色與深色模式都看得清楚。 */
val GOAL_COLORS: List<Int> = listOf(
    0xFF4DB6AC, // 青綠
    0xFF81C784, // 綠
    0xFF64B5F6, // 藍
    0xFF9575CD, // 紫
    0xFFF06292, // 粉
    0xFFE57373, // 紅
    0xFFFFB74D, // 橘
    0xFFFFD54F, // 黃
    0xFFA1887F, // 棕
    0xFF90A4AE, // 灰
).map { it.toInt() }

/** 常見的「買了覺得太貴」的東西。 */
val GOAL_EMOJIS: List<String> = listOf(
    "🧥", "👟", "👜", "👗", "⌚", "💍",
    "📱", "💻", "🎧", "📷", "🎮", "📺",
    "🛋️", "🚲", "🛵", "🚗", "✈️", "🏕️",
    "🎸", "📚", "💄", "🐶", "🎁", "💸",
)

/** 沒設顏色的目標用主題色。 */
@Composable
fun Goal.accentColor(): Color = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

/** 圓形小圖示：有 emoji 顯示 emoji，否則顯示名稱第一個字。 */
@Composable
fun GoalAvatar(
    name: String,
    emoji: String?,
    color: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val accent = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.25f)),
    ) {
        val fontSize = (size.value * 0.5f).sp
        if (emoji != null) {
            Text(emoji, fontSize = fontSize)
        } else {
            Text(
                name.trim().take(1).ifEmpty { "?" },
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun GoalAvatar(goal: Goal, modifier: Modifier = Modifier, size: Dp = 40.dp) =
    GoalAvatar(goal.name, goal.emoji, goal.color, modifier, size)
