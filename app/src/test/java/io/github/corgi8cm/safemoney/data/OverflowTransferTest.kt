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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class OverflowTransferTest {
    private val day1 = LocalDate.of(2026, 11, 1)
    private val day2 = LocalDate.of(2026, 11, 2)
    private val day3 = LocalDate.of(2026, 11, 3)

    private lateinit var db: SafeMoneyDatabase
    private lateinit var repo: SafeMoneyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SafeMoneyDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SafeMoneyRepository(db, Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun saved(goalId: Long): Long = repo.observeGoal(goalId).first()!!.saved

    private suspend fun status(goalId: Long): GoalStatus = repo.observeGoal(goalId).first()!!.goal.status

    @Test
    fun addSavingReportsNewlyCompletedGoalsOnlyOnce() = runTest {
        val goal = repo.addGoal("耳機", 100, day1)
        assertEquals(emptyList<Long>(), repo.addSaving("a", day1, listOf(AllocationInput(goal, 40))).completedGoalIds)
        assertEquals(listOf(goal), repo.addSaving("b", day2, listOf(AllocationInput(goal, 60))).completedGoalIds)
        // 已經回本後再存，不會再慶祝一次
        assertEquals(emptyList<Long>(), repo.addSaving("c", day3, listOf(AllocationInput(goal, 10))).completedGoalIds)
    }

    @Test
    fun transferMovesOverflowFromNewestSavingIntoSplit() = runTest {
        val coat = repo.addGoal("大衣", 100, day1)
        val shoes = repo.addGoal("鞋子", 1_000, day1)
        repo.addSaving("a", day1, listOf(AllocationInput(coat, 60)))
        val last = repo.addSaving("b", day2, listOf(AllocationInput(coat, 80))).savingId

        val result = repo.transferOverflow(coat, shoes)

        assertEquals(40, result.transferred)
        assertEquals(emptyList<Long>(), result.completedGoalIds)
        assertEquals(100, saved(coat))
        assertEquals(GoalStatus.COMPLETED, status(coat))
        assertEquals(40, saved(shoes))
        val lastSaving = repo.getSaving(last)!!
        assertEquals(80, lastSaving.total()) // 紀錄總額不變
        assertEquals(
            mapOf(coat to 40L, shoes to 40L),
            lastSaving.allocations.associate { it.goalId to it.amount },
        )
    }

    @Test
    fun transferSpansSeveralSavingsAndRemovesFullyMovedAllocations() = runTest {
        val coat = repo.addGoal("大衣", 95, day1)
        val shoes = repo.addGoal("鞋子", 1_000, day1)
        repo.addSaving("a", day1, listOf(AllocationInput(coat, 90)))
        val middle = repo.addSaving("b", day2, listOf(AllocationInput(coat, 10))).savingId
        val newest = repo.addSaving("c", day3, listOf(AllocationInput(coat, 10))).savingId

        val result = repo.transferOverflow(coat, shoes) // 110 - 95 = 15

        assertEquals(15, result.transferred)
        assertEquals(95, saved(coat))
        assertEquals(15, saved(shoes))
        assertEquals(listOf(shoes to 10L), repo.getSaving(newest)!!.allocations.map { it.goalId to it.amount })
        assertEquals(
            mapOf(coat to 5L, shoes to 5L),
            repo.getSaving(middle)!!.allocations.associate { it.goalId to it.amount },
        )
    }

    @Test
    fun transferMergesIntoExistingAllocationOfTarget() = runTest {
        val coat = repo.addGoal("大衣", 50, day1)
        val shoes = repo.addGoal("鞋子", 1_000, day1)
        val saving = repo.addSaving(
            "拆分", day1, listOf(AllocationInput(coat, 70), AllocationInput(shoes, 30)),
        ).savingId

        repo.transferOverflow(coat, shoes)

        assertEquals(
            mapOf(coat to 50L, shoes to 50L),
            repo.getSaving(saving)!!.allocations.associate { it.goalId to it.amount },
        )
    }

    @Test
    fun transferThatCompletesTargetReportsIt() = runTest {
        val coat = repo.addGoal("大衣", 100, day1)
        val socks = repo.addGoal("襪子", 30, day1)
        repo.addSaving("a", day1, listOf(AllocationInput(coat, 150)))

        val result = repo.transferOverflow(coat, socks)

        assertEquals(50, result.transferred)
        assertEquals(listOf(socks), result.completedGoalIds)
        assertEquals(GoalStatus.COMPLETED, status(socks))
        assertEquals(50, saved(socks))
    }

    @Test
    fun transferWithoutOverflowDoesNothing() = runTest {
        val coat = repo.addGoal("大衣", 100, day1)
        val shoes = repo.addGoal("鞋子", 1_000, day1)
        repo.addSaving("a", day1, listOf(AllocationInput(coat, 100)))

        assertEquals(0, repo.transferOverflow(coat, shoes).transferred)
        assertEquals(100, saved(coat))
        assertEquals(0, saved(shoes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferToSameGoalIsRejected() = runTest {
        val coat = repo.addGoal("大衣", 100, day1)
        repo.transferOverflow(coat, coat)
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferToArchivedGoalIsRejected() = runTest {
        val coat = repo.addGoal("大衣", 100, day1)
        val old = repo.addGoal("舊目標", 100, day1)
        repo.addSaving("a", day1, listOf(AllocationInput(coat, 150)))
        repo.setArchived(old, archived = true)
        repo.transferOverflow(coat, old)
    }
}
