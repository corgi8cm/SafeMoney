package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.data.db.total
import io.github.corgi8cm.safemoney.domain.AllocationInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SafeMoneyRepositoryTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val today = LocalDate.of(2026, 11, 8)

    private lateinit var db: SafeMoneyDatabase
    private lateinit var repo: SafeMoneyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SafeMoneyDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SafeMoneyRepository(db, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun saved(goalId: Long): Long = repo.observeGoal(goalId).first()!!.saved

    private suspend fun status(goalId: Long): GoalStatus = repo.observeGoal(goalId).first()!!.goal.status

    @Test
    fun addGoalTrimsInputAndStartsActiveWithZeroSaved() = runTest {
        val id = repo.addGoal("  大衣 ", 7_000, today, note = "  ")
        val row = repo.observeGoal(id).first()!!
        assertEquals("大衣", row.goal.name)
        assertNull(row.goal.note)
        assertEquals(GoalStatus.ACTIVE, row.goal.status)
        assertEquals(now, row.goal.createdAt)
        assertEquals(0, row.saved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addGoalRejectsNonPositiveAmount() = runTest {
        repo.addGoal("大衣", 0, today)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addGoalRejectsBlankName() = runTest {
        repo.addGoal("   ", 100, today)
    }

    @Test
    fun savingSplitAcrossGoalsCountsTowardEach() = runTest {
        val coat = repo.addGoal("大衣", 7_000, today)
        val earphones = repo.addGoal("耳機", 3_000, today)

        repo.addSaving("沒叫外送", today, listOf(AllocationInput(coat, 60), AllocationInput(earphones, 40)))
        repo.addSaving("沒買飲料", today, listOf(AllocationInput(coat, 55)))

        assertEquals(115, saved(coat))
        assertEquals(40, saved(earphones))
        assertEquals(2, repo.observeSavingsForGoal(coat).first().size)
        assertEquals(1, repo.observeSavingsForGoal(earphones).first().size)
        assertEquals(100, repo.observeSavingsForGoal(earphones).first().single().total())
    }

    @Test(expected = IllegalArgumentException::class)
    fun addSavingRejectsUnknownGoal() = runTest {
        repo.addSaving("沒買飲料", today, listOf(AllocationInput(999, 50)))
    }

    @Test
    fun goalCompletesWhenReachedAndReopensWhenSavingDeleted() = runTest {
        val goal = repo.addGoal("耳機", 100, today)
        repo.addSaving("a", today, listOf(AllocationInput(goal, 40)))
        assertEquals(GoalStatus.ACTIVE, status(goal))

        val second = repo.addSaving("b", today, listOf(AllocationInput(goal, 60)))
        val completed = repo.observeGoal(goal).first()!!.goal
        assertEquals(GoalStatus.COMPLETED, completed.status)
        assertEquals(now, completed.completedAt)

        repo.deleteSaving(second)
        val reopened = repo.observeGoal(goal).first()!!.goal
        assertEquals(GoalStatus.ACTIVE, reopened.status)
        assertNull(reopened.completedAt)
        assertEquals(40, saved(goal))
    }

    @Test
    fun loweringGoalAmountCanCompleteIt() = runTest {
        val id = repo.addGoal("大衣", 7_000, today)
        repo.addSaving("a", today, listOf(AllocationInput(id, 5_000)))
        val goal = repo.observeGoal(id).first()!!.goal

        repo.updateGoal(goal.copy(amount = 5_000))
        assertEquals(GoalStatus.COMPLETED, status(id))

        // 傳入的 status 會被忽略，由已省金額決定
        repo.updateGoal(goal.copy(amount = 6_000, status = GoalStatus.COMPLETED))
        assertEquals(GoalStatus.ACTIVE, status(id))
    }

    @Test
    fun updateSavingReplacesAllocationsAndRefreshesBothGoals() = runTest {
        val a = repo.addGoal("A", 100, today)
        val b = repo.addGoal("B", 100, today)
        val saving = repo.addSaving("x", today, listOf(AllocationInput(a, 100)))
        assertEquals(GoalStatus.COMPLETED, status(a))

        repo.updateSaving(saving, "y", today.minusDays(1), listOf(AllocationInput(b, 100)))

        assertEquals(0, saved(a))
        assertEquals(GoalStatus.ACTIVE, status(a))
        assertEquals(100, saved(b))
        assertEquals(GoalStatus.COMPLETED, status(b))
        val updated = repo.observeAllSavings().first().single()
        assertEquals("y", updated.saving.reason)
        assertEquals(today.minusDays(1), updated.saving.date)
    }

    @Test
    fun deleteGoalRemovesOrphanSavingsButKeepsSharedOnes() = runTest {
        val coat = repo.addGoal("大衣", 7_000, today)
        val earphones = repo.addGoal("耳機", 3_000, today)
        repo.addSaving("只給大衣", today, listOf(AllocationInput(coat, 50)))
        repo.addSaving("拆分", today, listOf(AllocationInput(coat, 60), AllocationInput(earphones, 40)))

        repo.deleteGoal(coat)

        assertNull(repo.observeGoal(coat).first())
        val remaining = repo.observeAllSavings().first()
        assertEquals(1, remaining.size)
        assertEquals("拆分", remaining.single().saving.reason)
        assertEquals(40, remaining.single().total())
        assertEquals(40, saved(earphones))
    }

    @Test
    fun summaryExcludesArchivedGoals() = runTest {
        val a = repo.addGoal("A", 1_000, today)
        val b = repo.addGoal("B", 500, today)
        val c = repo.addGoal("C", 300, today)
        repo.addSaving("x", today, listOf(AllocationInput(a, 200), AllocationInput(b, 500)))
        repo.addSaving("y", today, listOf(AllocationInput(c, 100)))
        repo.setArchived(c, archived = true)

        val summary = repo.observeSummary().first()
        assertEquals(2, summary.goalCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1_500, summary.totalAmount)
        assertEquals(700, summary.totalSaved)
        assertNotNull(summary.progress)
    }

    @Test
    fun unarchiveRestoresCompletionFromSavedAmount() = runTest {
        val id = repo.addGoal("A", 100, today)
        repo.addSaving("x", today, listOf(AllocationInput(id, 100)))
        repo.setArchived(id, archived = true)
        assertEquals(GoalStatus.ARCHIVED, status(id))

        repo.setArchived(id, archived = false)
        assertEquals(GoalStatus.COMPLETED, status(id))
    }

    @Test
    fun emptySummaryHasNoProgress() = runTest {
        val summary = repo.observeSummary().first()
        assertEquals(0, summary.goalCount)
        assertNull(summary.progress)
        assertTrue(repo.observeGoals().first().isEmpty())
    }
}
