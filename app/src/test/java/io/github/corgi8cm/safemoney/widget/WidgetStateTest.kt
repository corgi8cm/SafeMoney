package io.github.corgi8cm.safemoney.widget

import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WidgetStateTest {
    private fun row(id: Long, name: String, amount: Long, saved: Long, status: GoalStatus = GoalStatus.ACTIVE) =
        GoalWithSaved(
            Goal(id, name, amount, LocalDate.of(2026, 11, 1), status = status, createdAt = Instant.EPOCH),
            saved,
        )

    @Test
    fun activeGoalsSortedClosestFirstAndSummaryExcludesArchived() {
        val state = widgetState(
            listOf(
                row(1, "大衣", 7_000, 700), // 10%
                row(2, "耳機", 1_000, 800), // 80%
                row(3, "杯子", 100, 100, GoalStatus.COMPLETED),
                row(4, "封存", 500, 0, GoalStatus.ARCHIVED),
                row(5, "鞋子", 2_000, 200), // 10%，比大衣晚建立
            ),
        )
        assertEquals(listOf("耳機", "大衣", "鞋子"), state.activeGoals.map { it.name })
        assertEquals(4, state.summary.goalCount)
        assertEquals(1, state.summary.completedCount)
        assertEquals(10_100, state.summary.totalAmount)
        assertEquals(1_800, state.summary.totalSaved)
        assertTrue(state.hasAnyGoal)
    }

    @Test
    fun onlyArchivedCountsAsNoGoal() {
        val state = widgetState(listOf(row(1, "封存", 500, 0, GoalStatus.ARCHIVED)))
        assertFalse(state.hasAnyGoal)
        assertTrue(state.activeGoals.isEmpty())
    }

    @Test
    fun goalRowsGrowWithHeight() {
        assertEquals(0, goalRowsFor(110f))
        assertEquals(1, goalRowsFor(140f))
        assertEquals(2, goalRowsFor(180f))
        assertEquals(4, goalRowsFor(600f))
    }
}
