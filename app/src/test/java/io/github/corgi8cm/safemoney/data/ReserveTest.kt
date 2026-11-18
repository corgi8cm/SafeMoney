package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.backup.BackupCodec
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.RESERVE_UID
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.domain.AllocationInput
import io.github.corgi8cm.safemoney.sync.SyncCodec
import io.github.corgi8cm.safemoney.sync.SyncDeletion
import io.github.corgi8cm.safemoney.sync.mergeSync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReserveTest {
    private val now = Instant.parse("2026-11-18T08:00:00Z")
    private val today = LocalDate.of(2026, 11, 18)
    private val dbs = mutableListOf<SafeMoneyDatabase>()

    private fun newRepo(): SafeMoneyRepository {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SafeMoneyDatabase::class.java)
            .allowMainThreadQueries().build()
        dbs += db
        return SafeMoneyRepository(db, Clock.fixed(now, ZoneOffset.UTC))
    }

    private val repo = newRepo()

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
    }

    private suspend fun saved(id: Long) = repo.observeGoal(id).first()!!.saved
    private suspend fun reserve() = repo.observeReserve().first()?.saved ?: 0

    @Test
    fun reserveIsNotAGoal() = runTest {
        repo.addGoal("外套", 100, today)
        val id = repo.reserveGoalId()
        assertEquals(id, repo.reserveGoalId()) // 只會有一個
        assertEquals(listOf("外套"), repo.observeGoals().first().map { it.goal.name })
        assertEquals(1, repo.observeSummary().first().goalCount)
        assertEquals(GoalStatus.RESERVE, repo.observeReserve().first()!!.goal.status)
    }

    @Test
    fun savingIntoReserveNeverCompletesIt() = runTest {
        val id = repo.reserveGoalId()
        repo.addSaving("獎金", today, listOf(AllocationInput(id, 500)))
        assertEquals(500, reserve())
        assertEquals(GoalStatus.RESERVE, repo.observeReserve().first()!!.goal.status)
    }

    @Test
    fun newGoalTakesWhatItNeedsFromTheReserve() = runTest {
        val reserveId = repo.reserveGoalId()
        repo.addSaving("一月", today.minusDays(2), listOf(AllocationInput(reserveId, 300)))
        repo.addSaving("二月", today.minusDays(1), listOf(AllocationInput(reserveId, 500)))

        // 只需要 600：存錢筒剩 200，目標一新增就回本
        val coat = repo.addGoal("外套", 600, today)
        val result = repo.fillFromReserve(coat)
        assertEquals(600, result.transferred)
        assertEquals(listOf(coat), result.completedGoalIds)
        assertEquals(600, saved(coat))
        assertEquals(200, reserve())
        // 紀錄的總額不變，只是改分到外套
        assertEquals(800, repo.observeAllSavings().first().sumOf { s -> s.allocations.sumOf { it.amount } })

        // 存錢筒不夠：能抵多少抵多少
        val shoes = repo.addGoal("鞋子", 1_000, today)
        assertEquals(200, repo.fillFromReserve(shoes).transferred)
        assertEquals(200, saved(shoes))
        assertEquals(0, reserve())
    }

    @Test
    fun overflowCanBeTransferredIntoTheReserve() = runTest {
        val coat = repo.addGoal("外套", 100, today)
        repo.addSaving("獎金", today, listOf(AllocationInput(coat, 150)))
        val targets = repo.transferTargets(excludeGoalId = coat)
        val reserveId = targets.single().goal.id
        repo.transferOverflow(coat, reserveId)
        assertEquals(100, saved(coat))
        assertEquals(50, reserve())
    }

    @Test
    fun reserveSurvivesBackupAndSync() = runTest {
        val reserveId = repo.reserveGoalId()
        repo.addSaving("獎金", today, listOf(AllocationInput(reserveId, 500)))

        val restored = newRepo()
        restored.restore(BackupCodec.decode(BackupCodec.encode(repo.snapshot("1.3.0"))))
        assertEquals(500, restored.observeReserve().first()!!.saved)

        val other = newRepo()
        val doc = SyncCodec.decode(SyncCodec.encode(repo.exportSyncDoc()))
        assertTrue(doc.goals.single().reserve)
        other.applySyncDoc(doc, other.exportSyncDoc())
        assertEquals(500, other.observeReserve().first()!!.saved)
        assertTrue(other.observeGoals().first().isEmpty())
    }

    @Test
    fun reserveCannotBeDeletedBySync() {
        runTest {
            repo.reserveGoalId()
            val local = repo.exportSyncDoc()
            val remote = local.copy(goals = emptyList(), deletions = listOf(SyncDeletion(RESERVE_UID, "goal", now.toEpochMilli())))
            assertNotNull(mergeSync(local, remote, now).goals.singleOrNull { it.uid == RESERVE_UID })
        }
    }
}
