package io.github.corgi8cm.safemoney.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {
    @Insert
    suspend fun insert(rule: RecurringSaving): Long

    @Insert
    suspend fun insertAll(rules: List<RecurringSaving>)

    @Update
    suspend fun update(rule: RecurringSaving)

    @Query("DELETE FROM recurring_saving WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recurring_saving")
    suspend fun deleteAll()

    @Query("SELECT * FROM recurring_saving WHERE id = :id")
    suspend fun getById(id: Long): RecurringSaving?

    @Query("SELECT * FROM recurring_saving ORDER BY createdAt, id")
    suspend fun getAll(): List<RecurringSaving>

    @Query("SELECT * FROM recurring_saving ORDER BY createdAt, id")
    fun observeAll(): Flow<List<RecurringSaving>>
}
