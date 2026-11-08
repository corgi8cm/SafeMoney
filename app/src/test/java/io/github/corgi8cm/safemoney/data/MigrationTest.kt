package io.github.corgi8cm.safemoney.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.corgi8cm.safemoney.data.db.DEFAULT_QUICK_REASONS
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        ).allowMainThreadQueries().build()
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
}
