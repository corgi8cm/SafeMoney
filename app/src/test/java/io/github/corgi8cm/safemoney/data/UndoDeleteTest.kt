package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.domain.AllocationInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class UndoDeleteTest {
    private val today = LocalDate.of(2026, 11, 8)
    private lateinit var db: SafeMoneyDatabase
    private lateinit var repo: SafeMoneyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SafeMoneyDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SafeMoneyRepository(db, Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun goals() = repo.observeGoals().first()
    // 分配的順序不重要，排序後再比較
    private suspend fun savings() = repo.observeAllSavings().first()
        .map { s -> s.copy(allocations = s.allocations.sortedBy { it.goalId }) }

    @Test
    fun undoDeleteSavingRestoresSameRecordAndStatus() = runTest {
        val cup = repo.addGoal("杯子", 100, today)
        val id = repo.addSaving("飲料", today, listOf(AllocationInput(cup, 100))).savingId
        val before = savings()

        val deleted = repo.deleteSaving(id)!!
        assertEquals(GoalStatus.ACTIVE, goals().single().goal.status)

        repo.undoDeleteSaving(deleted)
        assertEquals(before, savings())
        assertEquals(GoalStatus.COMPLETED, goals().single().goal.status)

        // 重複復原不會多出一筆
        repo.undoDeleteSaving(deleted)
        assertEquals(1, savings().size)
    }

    @Test
    fun undoDeleteSavingSkipsGoalsDeletedMeanwhile() = runTest {
        val coat = repo.addGoal("大衣", 7_000, today)
        val shoes = repo.addGoal("鞋子", 2_000, today)
        val id = repo.addSaving("外食", today, listOf(AllocationInput(coat, 60), AllocationInput(shoes, 40))).savingId
        val deleted = repo.deleteSaving(id)!!
        repo.deleteGoal(shoes)

        repo.undoDeleteSaving(deleted)
        assertEquals(listOf(coat), savings().single().allocations.map { it.goalId })
    }

    @Test
    fun undoDeleteGoalRestoresGoalOrphanedAndSplitRecords() = runTest {
        val coat = repo.addGoal("大衣", 7_000, today, emoji = "🧥")
        val shoes = repo.addGoal("鞋子", 2_000, today)
        repo.addSaving("飲料", today, listOf(AllocationInput(coat, 60)))
        repo.addSaving("外食", today, listOf(AllocationInput(coat, 100), AllocationInput(shoes, 50)))
        repo.setArchived(coat, archived = true)
        val goalsBefore = goals()
        val savingsBefore = savings()

        val deleted = repo.deleteGoal(coat)!!
        // 只分給大衣的紀錄整筆消失，拆分的紀錄只剩鞋子
        assertEquals(listOf("外食"), savings().map { it.saving.reason })
        assertEquals(listOf(shoes), savings().single().allocations.map { it.goalId })

        repo.undoDeleteGoal(deleted)
        assertEquals(goalsBefore, goals())
        assertEquals(savingsBefore, savings())
    }

    @Test
    fun deletingMissingThingsReturnsNull() = runTest {
        assertNull(repo.deleteGoal(42))
        assertNull(repo.deleteSaving(42))
    }
}
