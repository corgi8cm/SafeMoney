package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.backup.BackupCodec
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
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
class BackupRestoreTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val today = LocalDate.of(2026, 11, 8)

    private val dbs = mutableListOf<SafeMoneyDatabase>()

    private fun newRepo(): SafeMoneyRepository {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SafeMoneyDatabase::class.java,
        ).allowMainThreadQueries().build()
        dbs += db
        return SafeMoneyRepository(db, Clock.fixed(now, ZoneOffset.UTC))
    }

    private lateinit var source: SafeMoneyRepository

    @Before
    fun setUp() {
        source = newRepo()
    }

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
    }

    @Test
    fun exportThenRestoreOnAnotherPhoneGivesSameData() = runTest {
        val coat = source.addGoal("大衣", 7_000, today)
        val phones = source.addGoal("耳機", 100, today)
        source.addSaving("沒叫外送", today, listOf(AllocationInput(coat, 60), AllocationInput(phones, 100)))
        source.addSaving("飲料", today.minusDays(1), listOf(AllocationInput(coat, 50)))
        source.setArchived(coat, archived = true)

        val text = BackupCodec.encode(source.snapshot("0.4.0"))

        val target = newRepo()
        target.restore(BackupCodec.decode(text))

        assertEquals(source.observeGoals().first(), target.observeGoals().first())
        assertEquals(source.observeAllSavings().first(), target.observeAllSavings().first())
        assertEquals(GoalStatus.ARCHIVED, target.observeGoal(coat).first()!!.goal.status)
        assertEquals(GoalStatus.COMPLETED, target.observeGoal(phones).first()!!.goal.status)
    }

    @Test
    fun restoreReplacesExistingData() = runTest {
        val backup = run {
            val id = source.addGoal("大衣", 7_000, today)
            source.addSaving("飲料", today, listOf(AllocationInput(id, 60)))
            source.snapshot(null)
        }

        val target = newRepo()
        val old = target.addGoal("舊目標", 500, today)
        target.addSaving("舊紀錄", today, listOf(AllocationInput(old, 10)))

        target.restore(backup)

        val goals = target.observeGoals().first()
        assertEquals(listOf("大衣"), goals.map { it.goal.name })
        assertEquals(60, goals.single().saved)
        assertEquals(listOf("飲料"), target.observeAllSavings().first().map { it.saving.reason })

        // 還原後新增資料不會和還原的編號衝突
        val next = target.addGoal("新目標", 100, today)
        target.addSaving("新紀錄", today, listOf(AllocationInput(next, 10)))
        assertEquals(2, target.observeAllSavings().first().size)
    }

    @Test
    fun restoreResyncsStatusWithSavedAmount() = runTest {
        val id = source.addGoal("耳機", 100, today)
        source.addSaving("飲料", today, listOf(AllocationInput(id, 100)))
        val snapshot = source.snapshot(null)
        // 備份檔裡狀態寫錯（例如手動編輯過）：已省 100 / 100 卻標成進行中
        val tampered = snapshot.copy(
            goals = snapshot.goals.map { it.copy(status = GoalStatus.ACTIVE, completedAt = null) },
        )

        val target = newRepo()
        target.restore(tampered)

        assertEquals(GoalStatus.COMPLETED, target.observeGoal(id).first()!!.goal.status)
    }
}
