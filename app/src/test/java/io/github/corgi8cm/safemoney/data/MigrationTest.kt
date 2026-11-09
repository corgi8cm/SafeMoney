package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.corgi8cm.safemoney.data.db.DEFAULT_QUICK_REASONS
import io.github.corgi8cm.safemoney.data.db.MANUAL_MIGRATIONS
import io.github.corgi8cm.safemoney.data.db.MIGRATION_2_3
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 確認舊版資料庫升級後資料都還在。之後每次改 schema 都要在這裡加一個版本。 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SafeMoneyDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2KeepsDataAndAddsDefaults() = runTest {
        val day = LocalDate.of(2026, 11, 1).toEpochDay()
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO goal (id, name, amount, purchaseDate, note, color, status, createdAt, completedAt) " +
                    "VALUES (1, '大衣', 7000, $day, '冬天', NULL, 'ACTIVE', 0, NULL)"
            )
            execSQL("INSERT INTO saving (id, reason, date, createdAt) VALUES (1, '沒買飲料', $day, 0)")
            execSQL("INSERT INTO saving_allocation (savingId, goalId, amount) VALUES (1, 1, 60)")
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true).close()

        // 用 Room 開啟升級後的資料庫，透過 repository 讀取
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SafeMoneyDatabase::class.java,
            name,
        ).addMigrations(*MANUAL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val repo = SafeMoneyRepository(db)
            val goal = repo.observeGoals().first().single()
            assertEquals("大衣", goal.goal.name)
            assertEquals("冬天", goal.goal.note)
            assertNull(goal.goal.emoji)
            assertEquals(60, goal.saved)
            assertEquals("沒買飲料", repo.observeAllSavings().first().single().saving.reason)
            assertEquals(DEFAULT_QUICK_REASONS, repo.observeQuickReasons().first().map { it.text })
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate2To3AddsUniqueUidsAndUpdatedAt() = runTest {
        val day = LocalDate.of(2026, 11, 1).toEpochDay()
        val created = 1_762_000_000_000L
        helper.createDatabase(name, 2).apply {
            execSQL(
                "INSERT INTO goal (id, name, amount, purchaseDate, note, color, status, createdAt, completedAt, emoji) " +
                    "VALUES (1, '大衣', 7000, $day, NULL, NULL, 'ACTIVE', $created, NULL, '🧥')"
            )
            execSQL(
                "INSERT INTO goal (id, name, amount, purchaseDate, note, color, status, createdAt, completedAt, emoji) " +
                    "VALUES (2, '耳機', 1000, $day, NULL, NULL, 'ACTIVE', $created, NULL, NULL)"
            )
            execSQL("INSERT INTO saving (id, reason, date, createdAt) VALUES (1, '沒買飲料', $day, $created)")
            execSQL("INSERT INTO saving_allocation (savingId, goalId, amount) VALUES (1, 1, 60)")
            execSQL("INSERT INTO quick_reason (text, position) VALUES ('沒買飲料', 0)")
            close()
        }

        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SafeMoneyDatabase::class.java,
            name,
        ).addMigrations(*MANUAL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val repo = SafeMoneyRepository(db)
            val goals = repo.observeGoals().first().map { it.goal }
            assertEquals(listOf("大衣", "耳機"), goals.map { it.name })
            assertTrue(goals.all { it.uid.isNotBlank() })
            assertEquals(2, goals.map { it.uid }.toSet().size)
            assertTrue(goals.all { it.updatedAt == Instant.ofEpochMilli(created) })
            val saving = repo.observeAllSavings().first().single()
            assertTrue(saving.saving.uid.isNotBlank())
            assertEquals(Instant.ofEpochMilli(created), saving.saving.updatedAt)
            assertEquals(60, saving.allocations.single().amount)
            assertTrue(repo.observeQuickReasons().first().single().uid.isNotBlank())
            assertEquals(emptyList<Any>(), repo.deletions())
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate3To4AddsEmptyRecurringTable() = runTest {
        val day = LocalDate.of(2026, 11, 1).toEpochDay()
        val created = 1_762_000_000_000L
        helper.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO goal (id, name, amount, purchaseDate, note, color, status, createdAt, completedAt, emoji, uid, updatedAt) " +
                    "VALUES (1, '大衣', 7000, $day, NULL, NULL, 'ACTIVE', $created, NULL, NULL, 'g1', $created)"
            )
            execSQL("INSERT INTO saving (id, reason, date, createdAt, uid, updatedAt) VALUES (1, '沒買飲料', $day, $created, 's1', $created)")
            execSQL("INSERT INTO saving_allocation (savingId, goalId, amount) VALUES (1, 1, 60)")
            close()
        }

        helper.runMigrationsAndValidate(name, 4, true).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SafeMoneyDatabase::class.java,
            name,
        ).addMigrations(*MANUAL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val repo = SafeMoneyRepository(db)
            assertEquals(60, repo.observeGoals().first().single().saved)
            assertEquals("s1", repo.observeAllSavings().first().single().saving.uid)
            assertTrue(repo.observeRecurring().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
