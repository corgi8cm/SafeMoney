package io.github.corgi8cm.safemoney.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.domain.AllocationInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 模擬兩支手機透過同一個雲端同步檔互相同步。 */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private val today = LocalDate.of(2026, 11, 9)
    private var now = Instant.parse("2026-11-09T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private class MemoryStore : SyncStore {
        var text: String? = null
        var writes = 0
        override suspend fun read() = text
        override suspend fun write(text: String) {
            this.text = text
            writes++
        }
        override suspend fun delete() {
            text = null
        }
    }

    private val cloud = MemoryStore()
    private val dbs = mutableListOf<SafeMoneyDatabase>()

    private inner class Phone {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SafeMoneyDatabase::class.java)
            .allowMainThreadQueries().build().also { dbs += it }
        val repo = SafeMoneyRepository(db, clock)
        val engine = SyncEngine(repo, cloud, clock)

        suspend fun goals() = repo.observeGoals().first().map { it.goal.name to it.saved }.sortedBy { it.first }
        suspend fun goalId(name: String) = repo.observeGoals().first().first { it.goal.name == name }.goal.id
        /** 不含本機 id 的內容，用來比較兩支手機是否一致。 */
        suspend fun content() = repo.exportSyncDoc()
    }

    @After
    fun tearDown() = dbs.forEach { it.close() }

    private fun later() {
        now = now.plusSeconds(60)
    }

    @Test
    fun secondPhoneReceivesEverything() = runTest {
        val a = Phone()
        val coat = a.repo.addGoal("大衣", 7_000, today, emoji = "🧥")
        a.repo.addSaving("沒買飲料", today, listOf(AllocationInput(coat, 60)))
        a.repo.addQuickReason("沒買宵夜")
        a.engine.sync()

        val b = Phone()
        val result = b.engine.sync()
        assertTrue(result.changedLocal)
        assertEquals(listOf("大衣" to 60L), b.goals())
        assertEquals("🧥", b.repo.observeGoals().first().single().goal.emoji)
        assertEquals(listOf("沒買宵夜"), b.repo.observeQuickReasons().first().map { it.text })
        assertEquals(a.content(), b.content())
    }

    @Test
    fun editsAndDeletionsOnBothPhonesConverge() = runTest {
        val a = Phone()
        val b = Phone()
        val coat = a.repo.addGoal("大衣", 7_000, today)
        a.repo.addGoal("耳機", 1_000, today)
        a.engine.sync()
        b.engine.sync()

        // A 在大衣記一筆、B 刪掉耳機並改大衣的金額
        later()
        a.repo.addSaving("外食", today, listOf(AllocationInput(coat, 100)))
        later()
        b.repo.deleteGoal(b.goalId("耳機"))
        val bCoat = b.repo.observeGoal(b.goalId("大衣")).first()!!.goal
        b.repo.updateGoal(bCoat.copy(amount = 8_000))

        a.engine.sync()
        b.engine.sync()
        a.engine.sync()

        assertEquals(listOf("大衣" to 100L), a.goals())
        assertEquals(a.goals(), b.goals())
        assertEquals(8_000, a.repo.observeGoals().first().single().goal.amount)
        assertEquals(a.content(), b.content())

        // 都一致之後再同步，不會再上傳
        val writes = cloud.writes
        assertFalse(a.engine.sync().uploaded)
        assertFalse(b.engine.sync().uploaded)
        assertEquals(writes, cloud.writes)
    }

    @Test
    fun undoAfterSyncedDeleteRevivesOnOtherPhone() = runTest {
        val a = Phone()
        val b = Phone()
        val id = a.repo.addGoal("大衣", 7_000, today)
        a.engine.sync()
        b.engine.sync()

        later()
        val deleted = a.repo.deleteGoal(id)!!
        a.engine.sync()
        b.engine.sync()
        assertEquals(emptyList<Pair<String, Long>>(), b.goals())

        later()
        a.repo.undoDeleteGoal(deleted)
        a.engine.sync()
        b.engine.sync()
        assertEquals(listOf("大衣" to 0L), b.goals())
    }

    @Test
    fun completionAndArchiveStatusSync() = runTest {
        val a = Phone()
        val b = Phone()
        val cup = a.repo.addGoal("杯子", 100, today)
        val shoes = a.repo.addGoal("鞋子", 500, today)
        a.repo.addSaving("飲料", today, listOf(AllocationInput(cup, 100)))
        later()
        a.repo.setArchived(shoes, archived = true)
        a.engine.sync()
        b.engine.sync()

        val statuses = b.repo.observeGoals().first().associate { it.goal.name to it.goal.status }
        assertEquals(GoalStatus.COMPLETED, statuses["杯子"])
        assertEquals(GoalStatus.ARCHIVED, statuses["鞋子"])
        assertEquals(a.content(), b.content())
    }

    @Test
    fun localEditDuringSyncIsNotOverwritten() = runTest {
        val a = Phone()
        a.repo.addGoal("大衣", 7_000, today)
        a.engine.sync()
        val b = Phone()
        b.engine.sync()
        // 合併前讀到的內容已經過期：applySyncDoc 會拒絕
        val stale = b.repo.exportSyncDoc()
        later()
        b.repo.addGoal("新的", 100, today)
        assertFalse(b.repo.applySyncDoc(stale, expected = stale))
        assertEquals(listOf("大衣" to 0L, "新的" to 0L), b.goals())
    }
}
