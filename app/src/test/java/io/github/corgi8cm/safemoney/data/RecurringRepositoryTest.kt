package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.backup.BackupCodec
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.domain.AllocationInput
import io.github.corgi8cm.safemoney.domain.Recurrence
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

/** 可以往後撥的時鐘，模擬「過了幾天才打開 App」。 */
private class TestClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun plusDays(days: Long) {
        now = now.plusSeconds(days * 86_400)
    }
}

@RunWith(AndroidJUnit4::class)
class RecurringRepositoryTest {
    // 2026/11/09 是星期一
    private val clock = TestClock(Instant.parse("2026-11-09T08:00:00Z"))
    private val monday = LocalDate.of(2026, 11, 9)

    private val dbs = mutableListOf<SafeMoneyDatabase>()
    private lateinit var repo: SafeMoneyRepository

    private fun newRepo(): SafeMoneyRepository {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SafeMoneyDatabase::class.java,
        ).allowMainThreadQueries().build()
        dbs += db
        return SafeMoneyRepository(db, clock)
    }

    @Before
    fun setUp() {
        repo = newRepo()
    }

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private suspend fun saved(goalId: Long): Long = repo.observeGoal(goalId).first()!!.saved

    @Test
    fun recordsEveryDueDayAndFillsGoalsOneByOne() = runTest {
        val coat = repo.addGoal("外套", 100, monday)
        val phones = repo.addGoal("耳機", 1_000, monday)
        repo.addRecurring("不買咖啡", 60, Recurrence.EVERY_DAY, monday, null)

        // 三天後才打開 App：一、二、三、四 共 4 天到期
        clock.plusDays(3)
        val pending = Recurrence.pending(repo.observeRecurring().first(), today())
        assertEquals(4, pending.size)

        val result = repo.recordPendingRecurring(today()) as RecurringRecordResult.Recorded
        assertEquals(4, result.count)
        assertEquals(240, result.total)
        assertEquals(listOf(coat), result.completedGoalIds)
        // 外套補滿 100，其餘 140 給耳機
        assertEquals(100, saved(coat))
        assertEquals(140, saved(phones))
        val savings = repo.observeAllSavings().first()
        assertEquals((0L..3L).map { monday.plusDays(it) }.reversed(), savings.map { it.saving.date })
        assertTrue(savings.all { it.saving.reason == "不買咖啡" })

        // 同一天再打開不會重複記
        assertEquals(RecurringRecordResult.NothingDue, repo.recordPendingRecurring(today()))
        assertEquals(today(), repo.observeRecurring().first().single().handledThrough)
    }

    @Test
    fun deletedDayIsNotRecordedAgain() = runTest {
        val coat = repo.addGoal("外套", 10_000, monday)
        repo.addRecurring("不買咖啡", 60, Recurrence.EVERY_DAY, monday, null)
        repo.recordPendingRecurring(today())
        val record = repo.observeAllSavings().first().single()
        repo.deleteSaving(record.saving.id) // 那天破戒了

        // 模擬另一支手機還沒同步到「已經記過」：把進度退回去再記一次
        val db = dbs.single()
        val rule = db.recurringDao().getAll().single()
        db.recurringDao().update(rule.copy(handledThrough = null))
        repo.recordPendingRecurring(today())
        assertEquals(0, saved(coat))
        assertTrue(repo.observeAllSavings().first().isEmpty())
    }

    @Test
    fun noGoalLeftRemovesAllRulesWithoutRecording() = runTest {
        val coat = repo.addGoal("外套", 100, monday)
        repo.addRecurring("不買咖啡", 60, Recurrence.WEEKDAYS, monday, null)
        repo.addRecurring("帶便當", 100, Recurrence.EVERY_DAY, monday, null)
        repo.addSaving("獎金", monday, listOf(AllocationInput(coat, 100)))
        assertEquals(GoalStatus.COMPLETED, repo.observeGoal(coat).first()!!.goal.status)

        val result = repo.recordPendingRecurring(today())
        assertEquals(RecurringRecordResult.RemovedNoGoals(2), result)
        assertTrue(repo.observeRecurring().first().isEmpty())
        assertEquals(100, saved(coat))
        assertEquals(1, repo.observeAllSavings().first().size)
    }

    @Test
    fun removingRuleKeepsRecordedSavings() = runTest {
        val coat = repo.addGoal("外套", 10_000, monday)
        val id = repo.addRecurring("不買咖啡", 60, Recurrence.EVERY_DAY, monday, null)
        repo.recordPendingRecurring(today())
        repo.deleteRecurring(id)
        clock.plusDays(2)
        assertEquals(RecurringRecordResult.NothingDue, repo.recordPendingRecurring(today()))
        assertEquals(60, saved(coat))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotStartInThePast() = runTest {
        repo.addGoal("外套", 10_000, monday)
        repo.addRecurring("不買咖啡", 60, Recurrence.EVERY_DAY, monday.minusDays(1), null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun needsAGoalToFill() = runTest {
        repo.addRecurring("不買咖啡", 60, Recurrence.EVERY_DAY, monday, null)
    }

    @Test
    fun twoPhonesRecordingTheSameDayEndUpWithOneRecord() = runTest {
        repo.addGoal("外套", 10_000, monday)
        repo.addRecurring("不買咖啡", 60, Recurrence.EVERY_DAY, monday, null)

        // 第二支手機先同步到同樣的目標與設定，兩邊各自關掉通知
        val other = newRepo()
        other.applySyncDoc(repo.exportSyncDoc(), other.exportSyncDoc())
        repo.recordPendingRecurring(today())
        other.recordPendingRecurring(today())

        val merged = io.github.corgi8cm.safemoney.sync.mergeSync(repo.exportSyncDoc(), other.exportSyncDoc(), clock.instant())
        assertEquals(1, merged.savings.size)
        assertEquals(today().toString(), merged.recurring.single().handledThrough)
    }

    @Test
    fun backupKeepsRules() = runTest {
        repo.addGoal("外套", 10_000, monday)
        repo.addRecurring("不買咖啡", 60, Recurrence.WEEKDAYS, monday, monday.plusDays(30))
        val text = BackupCodec.encode(repo.snapshot("1.1.0"))

        val target = newRepo()
        target.restore(BackupCodec.decode(text))
        val rule = target.observeRecurring().first().single()
        assertEquals("不買咖啡", rule.reason)
        assertEquals(Recurrence.WEEKDAYS, rule.days)
        assertEquals(monday.plusDays(30), rule.endDate)
    }

    @Test
    fun oldBackupWithoutRulesKeepsThePhonesRules() = runTest {
        repo.addGoal("外套", 10_000, monday)
        repo.addRecurring("不買咖啡", 60, Recurrence.WEEKDAYS, monday, null)
        val old = BackupCodec.decode(BackupCodec.encode(repo.snapshot("1.0.0")).replace("\"recurring\"", "\"ignored\""))
        repo.restore(old)
        assertEquals(1, repo.observeRecurring().first().size)
    }
}
