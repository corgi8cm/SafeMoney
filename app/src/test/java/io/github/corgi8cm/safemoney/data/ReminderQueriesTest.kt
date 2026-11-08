package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.domain.AllocationInput
import io.github.corgi8cm.safemoney.domain.NearestGoal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ReminderQueriesTest {
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

    @Test
    fun recordedTodayOnlyCountsToday() = runTest {
        val id = repo.addGoal("大衣", 7_000, today)
        repo.addSaving("飲料", today.minusDays(1), listOf(AllocationInput(id, 60)))
        assertFalse(repo.hasSavingOn(today))
        repo.addSaving("外食", today, listOf(AllocationInput(id, 100)))
        assertTrue(repo.hasSavingOn(today))
    }

    @Test
    fun nearestGoalIgnoresCompletedAndArchived() = runTest {
        assertNull(repo.nearestActiveGoal())
        val coat = repo.addGoal("大衣", 7_000, today)
        val phones = repo.addGoal("耳機", 1_000, today)
        val done = repo.addGoal("杯子", 100, today)
        val archived = repo.addGoal("封存", 50, today)
        repo.addSaving("飲料", today, listOf(AllocationInput(coat, 6_500), AllocationInput(done, 100)))
        repo.setArchived(archived, archived = true)

        assertEquals(NearestGoal("大衣", 500), repo.nearestActiveGoal())
        repo.setArchived(coat, archived = true)
        assertEquals(NearestGoal("耳機", 1_000), repo.nearestActiveGoal())
        assertTrue(phones > 0)
    }
}
