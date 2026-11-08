package io.github.corgi8cm.safemoney.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Goal::class, Saving::class, SavingAllocation::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SafeMoneyDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun savingDao(): SavingDao

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
                ).build().also { instance = it }
            }
    }
}
