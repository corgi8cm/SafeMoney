package io.github.corgi8cm.safemoney.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.corgi8cm.safemoney.MainActivity
import io.github.corgi8cm.safemoney.SafeMoneyApp
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.common.percentText

/** 主畫面小工具：整體回本進度，空間夠時列出最接近回本的幾個目標。點一下開啟 App。 */
class SafeMoneyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
            DpSize(250.dp, 260.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as SafeMoneyApp).repository
        val state = widgetState(repository.goalsSnapshot())
        provideContent {
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }
}

class SafeMoneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeMoneyWidget()
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            "省回來",
            style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(4.dp))

        val progress = state.summary.progress
        when {
            !state.hasAnyGoal || progress == null -> {
                Text(
                    "還沒有回本目標，點這裡新增",
                    style = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                )
            }
            else -> {
                Row(verticalAlignment = Alignment.Bottom, modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        formatMoney(state.summary.totalSaved),
                        style = TextStyle(color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        " / ${formatMoney(state.summary.totalAmount)}",
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        progress.percentText(),
                        style = TextStyle(color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress.fraction,
                    color = colors.primary,
                    backgroundColor = colors.surfaceVariant,
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                )
                Spacer(GlanceModifier.height(6.dp))
                val subtitle = if (state.activeGoals.isEmpty()) {
                    "全部都省回來了 🎉"
                } else {
                    "${state.summary.goalCount} 個目標，${state.summary.completedCount} 個已回本"
                }
                Text(subtitle, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)

                val rows = goalRowsFor(LocalSize.current.height.value)
                state.activeGoals.take(rows).forEach { goal ->
                    Spacer(GlanceModifier.height(10.dp))
                    GoalRow(goal)
                }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: WidgetGoal) {
    val colors = GlanceTheme.colors
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(
                goal.name,
                style = TextStyle(color = colors.onSurface, fontSize = 13.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "還差 ${formatMoney(goal.progress.remaining)}",
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = goal.progress.fraction,
            color = colors.secondary,
            backgroundColor = colors.surfaceVariant,
            modifier = GlanceModifier.fillMaxWidth().height(5.dp),
        )
    }
}
