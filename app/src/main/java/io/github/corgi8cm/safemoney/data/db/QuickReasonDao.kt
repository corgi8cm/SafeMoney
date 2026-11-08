package io.github.corgi8cm.safemoney.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface QuickReasonDao {
    /** 管理用：依加入順序。 */
    @Query("SELECT * FROM quick_reason ORDER BY position, id")
    fun observeAll(): Flow<List<QuickReason>>

    @Query("SELECT * FROM quick_reason ORDER BY position, id")
    suspend fun getAll(): List<QuickReason>

    /** 記錄頁用：[since] 之後用得越多的排越前面，次數相同依加入順序。 */
    @Query(
        """
        SELECT quick_reason.* FROM quick_reason
        LEFT JOIN (
            SELECT reason, COUNT(*) AS uses FROM saving WHERE date >= :since GROUP BY reason
        ) AS usage ON usage.reason = quick_reason.text
        ORDER BY COALESCE(usage.uses, 0) DESC, quick_reason.position, quick_reason.id
        """
    )
    fun observeByUsage(since: LocalDate): Flow<List<QuickReason>>

    @Query("SELECT * FROM quick_reason WHERE text = :text")
    suspend fun getByText(text: String): QuickReason?

    @Query("SELECT COALESCE(MAX(position), -1) FROM quick_reason")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(reason: QuickReason): Long

    @Insert
    suspend fun insertAll(reasons: List<QuickReason>)

    @Update
    suspend fun update(reason: QuickReason)

    @Query("DELETE FROM quick_reason WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM quick_reason")
    suspend fun deleteAll()
}
