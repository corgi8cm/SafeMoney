package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.db.DEFAULT_QUICK_REASONS
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.domain.AllocationInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class QuickReasonsTest {
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

    private suspend fun texts() = repo.observeQuickReasons().first().map { it.text }

    private suspend fun byUsage() = repo.observeQuickReasonsByUsage(today).first()

    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("should be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun addTrimsAndRejectsBlankOrDuplicate() = runTest {
        repo.addQuickReason("  沒買飲料 ")
        repo.addQuickReason("自己帶便當")
        assertEquals(listOf("沒買飲料", "自己帶便當"), texts())
        assertRejected { repo.addQuickReason("沒買飲料") }
        assertRejected { repo.addQuickReason("   ") }
        assertRejected { repo.addQuickReason("字".repeat(MAX_REASON_LENGTH + 1)) }
    }

    @Test
    fun renameAndDelete() = runTest {
        repo.addQuickReason("沒買飲料")
        repo.addQuickReason("沒搭計程車")
        val (drink, taxi) = repo.observeQuickReasons().first()
        assertRejected { repo.renameQuickReason(drink.id, "沒搭計程車") }
        repo.renameQuickReason(drink.id, "沒買手搖")
        repo.renameQuickReason(drink.id, "沒買手搖") // 改成一樣的名字不算重複
        repo.deleteQuickReason(taxi.id)
        assertEquals(listOf("沒買手搖"), texts())
    }

    @Test
    fun recordScreenOrderFollowsRecentUsage() = runTest {
        repo.resetQuickReasons()
        val goal = repo.addGoal("大衣", 7_000, today)
        suspend fun use(reason: String, daysAgo: Long) {
            repo.addSaving(reason, today.minusDays(daysAgo), listOf(AllocationInput(goal, 10)))
        }
        use("沒喝咖啡", 0)
        use("沒喝咖啡", 1)
        use("沒網購", 2)
        // 超過 90 天的不算
        repeat(3) { use("少吃零食", 120) }

        assertEquals(listOf("沒喝咖啡", "沒網購"), byUsage().take(2))
        // 其餘依原本順序
        assertEquals(DEFAULT_QUICK_REASONS - setOf("沒喝咖啡", "沒網購"), byUsage().drop(2))
    }

    @Test
    fun restoreReplacesReasonsOnlyWhenBackupHasThem() = runTest {
        repo.addQuickReason("我的原因")
        val snapshot = repo.snapshot(null)

        repo.restore(snapshot.copy(quickReasons = null))
        assertEquals(listOf("我的原因"), texts())

        repo.restore(snapshot.copy(quickReasons = listOf("A", "B")))
        assertEquals(listOf("A", "B"), texts())
    }
}
