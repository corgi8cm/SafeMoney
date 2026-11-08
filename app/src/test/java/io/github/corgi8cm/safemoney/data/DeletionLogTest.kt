package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.db.Deletion
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.domain.AllocationInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 同步的地基：修改時間與刪除紀錄（墓碑）有正確留下。 */
@RunWith(AndroidJUnit4::class)
class DeletionLogTest {
    private val today = LocalDate.of(2026, 11, 8)
    private var now = Instant.parse("2026-11-08T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
    private lateinit var db: SafeMoneyDatabase
    private lateinit var repo: SafeMoneyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SafeMoneyDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SafeMoneyRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun later() {
        now = now.plusSeconds(60)
    }

    private suspend fun deletions() = repo.deletions().map { it.entity to it.uid }.toSet()

    @Test
    fun editsBumpUpdatedAt() = runTest {
        val id = repo.addGoal("大衣", 7_000, today)
        val goal = repo.observeGoal(id).first()!!.goal
        assertEquals(now, goal.updatedAt)

        later()
        repo.updateGoal(goal.copy(name = "羊毛大衣"))
        val updated = repo.observeGoal(id).first()!!.goal
        assertEquals(now, updated.updatedAt)
        assertEquals(goal.uid, updated.uid)

        later()
        val savingId = repo.addSaving("飲料", today, listOf(AllocationInput(id, 60))).savingId
        later()
        repo.updateSaving(savingId, "手搖飲", today, listOf(AllocationInput(id, 70)))
        assertEquals(now, repo.getSaving(savingId)!!.saving.updatedAt)
    }

    @Test
    fun deletingGoalRecordsGoalAndOrphanedSavingsAndBumpsSplitOnes() = runTest {
        val coat = repo.addGoal("大衣", 7_000, today)
        val shoes = repo.addGoal("鞋子", 2_000, today)
        val only = repo.addSaving("飲料", today, listOf(AllocationInput(coat, 60))).savingId
        val split = repo.addSaving("外食", today, listOf(AllocationInput(coat, 50), AllocationInput(shoes, 50))).savingId
        val coatUid = repo.observeGoal(coat).first()!!.goal.uid
        val onlyUid = repo.getSaving(only)!!.saving.uid

        later()
        val deleted = repo.deleteGoal(coat)!!
        assertEquals(setOf(Deletion.GOAL to coatUid, Deletion.SAVING to onlyUid), deletions())
        assertEquals(now, repo.getSaving(split)!!.saving.updatedAt)

        later()
        repo.undoDeleteGoal(deleted)
        assertEquals(emptySet<Pair<String, String>>(), deletions())
        assertEquals(now, repo.observeGoal(coat).first()!!.goal.updatedAt)
    }

    @Test
    fun deletingSavingAndReasonRecordsTombstonesUntilUndo() = runTest {
        val goal = repo.addGoal("大衣", 7_000, today)
        val savingId = repo.addSaving("飲料", today, listOf(AllocationInput(goal, 60))).savingId
        repo.addQuickReason("沒買宵夜")
        val reason = repo.observeQuickReasons().first().single()

        val deletedSaving = repo.deleteSaving(savingId)!!
        repo.deleteQuickReason(reason.id)
        assertEquals(
            setOf(Deletion.SAVING to deletedSaving.saving.uid, Deletion.QUICK_REASON to reason.uid),
            deletions(),
        )

        repo.undoDeleteSaving(deletedSaving)
        repo.restoreQuickReason(reason)
        assertEquals(emptySet<Pair<String, String>>(), deletions())
    }

    @Test
    fun restoreMarksLocalOnlyDataAsDeleted() = runTest {
        val kept = repo.addGoal("大衣", 7_000, today)
        val backup = repo.snapshot(null)
        val extra = repo.addGoal("只在手機上", 100, today)
        val extraUid = repo.observeGoal(extra).first()!!.goal.uid

        repo.restore(backup)
        assertTrue(Deletion.GOAL to extraUid in deletions())
        assertEquals(listOf(kept), repo.observeGoals().first().map { it.goal.id })
    }
}
