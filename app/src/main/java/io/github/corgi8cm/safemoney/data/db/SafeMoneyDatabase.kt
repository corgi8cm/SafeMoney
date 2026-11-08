package io.github.corgi8cm.safemoney.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/** 第一次安裝與從第 1 版升級時放入的預設快捷原因。 */
val DEFAULT_QUICK_REASONS = listOf("沒買飲料", "自己帶便當", "少吃零食", "沒搭計程車", "沒網購", "沒喝咖啡")

internal fun SupportSQLiteDatabase.seedDefaultQuickReasons() {
    DEFAULT_QUICK_REASONS.forEachIndexed { index, text ->
        execSQL("INSERT OR IGNORE INTO quick_reason (text, position) VALUES (?, ?)", arrayOf<Any>(text, index))
    }
}

/** 1 → 2：新增 goal.emoji 與 quick_reason 表，並放入預設快捷原因。 */
class Migration1To2 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) = db.seedDefaultQuickReasons()
}

@Database(
    entities = [Goal::class, Saving::class, SavingAllocation::class, QuickReason::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = Migration1To2::class)],
)
@TypeConverters(Converters::class)
abstract class SafeMoneyDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun savingDao(): SavingDao
    abstract fun quickReasonDao(): QuickReasonDao

    companion object {
        private const val NAME = "safemoney.db"

        @Volatile
        private var instance: SafeMoneyDatabase? = null

        fun get(context: Context): SafeMoneyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SafeMoneyDatabase::class.java,
                    NAME,
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = db.seedDefaultQuickReasons()
                }).build().also { instance = it }
            }
    }
}
