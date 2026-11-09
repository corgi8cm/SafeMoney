package io.github.corgi8cm.safemoney.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 第一次安裝與從第 1 版升級時放入的預設快捷原因。 */
val DEFAULT_QUICK_REASONS = listOf("沒買飲料", "自己帶便當", "少吃零食", "沒搭計程車", "沒網購", "沒喝咖啡")

/** SQLite 產生的隨機編號（32 個十六進位字元），跟 [newUid] 一樣只當作不透明的字串使用。 */
private const val SQL_RANDOM_UID = "lower(hex(randomblob(16)))"

/** 新安裝時（第 3 版以後的表格）放入預設快捷原因。 */
internal fun SupportSQLiteDatabase.seedDefaultQuickReasons() {
    val now = System.currentTimeMillis()
    DEFAULT_QUICK_REASONS.forEachIndexed { index, text ->
        execSQL(
            "INSERT OR IGNORE INTO quick_reason (text, position, uid, updatedAt) VALUES (?, ?, $SQL_RANDOM_UID, ?)",
            arrayOf<Any>(text, index, now),
        )
    }
}

/** 1 → 2：新增 goal.emoji 與 quick_reason 表，並放入預設快捷原因（第 2 版的表格還沒有 uid）。 */
class Migration1To2 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        DEFAULT_QUICK_REASONS.forEachIndexed { index, text ->
            db.execSQL("INSERT OR IGNORE INTO quick_reason (text, position) VALUES (?, ?)", arrayOf<Any>(text, index))
        }
    }
}

/**
 * 2 → 3（同步的地基）：goal / saving / quick_reason 加上跨裝置唯一的 uid 與修改時間，
 * 並新增 deletion（墓碑）表。舊資料的修改時間用建立時間（快捷原因用升級當下）。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        for (table in listOf("goal", "saving", "quick_reason")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `$table` SET `uid` = $SQL_RANDOM_UID")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
        }
        db.execSQL("UPDATE `goal` SET `updatedAt` = `createdAt`")
        db.execSQL("UPDATE `saving` SET `updatedAt` = `createdAt`")
        db.execSQL("UPDATE `quick_reason` SET `updatedAt` = $now")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deletion` (`uid` TEXT NOT NULL, `entity` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))"
        )
    }
}

/** 手寫的遷移（自動遷移以外的），開資料庫與測試都要加上。 */
val MANUAL_MIGRATIONS = arrayOf(MIGRATION_2_3)

@Database(
    entities = [
        Goal::class, Saving::class, SavingAllocation::class, QuickReason::class, Deletion::class,
        RecurringSaving::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migration1To2::class),
        // 3 → 4：新增定期省錢的表格，舊資料不用動
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(Converters::class)
abstract class SafeMoneyDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun savingDao(): SavingDao
    abstract fun quickReasonDao(): QuickReasonDao
    abstract fun deletionDao(): DeletionDao
    abstract fun recurringDao(): RecurringDao

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
                ).addMigrations(*MANUAL_MIGRATIONS).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = db.seedDefaultQuickReasons()
                }).build().also { instance = it }
            }
    }
}
