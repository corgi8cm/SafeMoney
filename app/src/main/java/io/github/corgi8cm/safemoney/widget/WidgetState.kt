package io.github.corgi8cm.safemoney.widget

import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.progress
import io.github.corgi8cm.safemoney.data.summarize
import io.github.corgi8cm.safemoney.domain.Progress
import io.github.corgi8cm.safemoney.domain.Summary

data class WidgetGoal(val id: Long, val name: String, val progress: Progress)

/** 小工具要顯示的內容。 */
data class WidgetState(
    val summary: Summary,
    /** 進行中的目標，最接近回本的排前面。 */
    val activeGoals: List<WidgetGoal>,
    val hasAnyGoal: Boolean,
)

fun widgetState(goals: List<GoalWithSaved>): WidgetState = WidgetState(
    summary = summarize(goals),
    activeGoals = goals
        .filter { it.goal.status == GoalStatus.ACTIVE }
        .map { WidgetGoal(it.goal.id, listOfNotNull(it.goal.emoji, it.goal.name).joinToString(" "), it.progress()) }
        .sortedWith(compareByDescending<WidgetGoal> { it.progress.fraction }.thenBy { it.id }),
    hasAnyGoal = goals.any { it.goal.status != GoalStatus.ARCHIVED },
)

/** 依小工具高度（dp）決定列出幾個目標：太矮只顯示總覽。 */
fun goalRowsFor(heightDp: Float): Int = ((heightDp - 96f) / 40f).toInt().coerceIn(0, 4)
