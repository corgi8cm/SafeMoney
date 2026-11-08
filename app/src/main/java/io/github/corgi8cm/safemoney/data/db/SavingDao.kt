package io.github.corgi8cm.safemoney.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingDao {
    @Insert
    suspend fun insertSaving(saving: Saving): Long

    @Update
    suspend fun updateSaving(saving: Saving)

    @Query("DELETE FROM saving WHERE id = :id")
    suspend fun deleteSaving(id: Long)

    @Insert
    suspend fun insertAllocations(allocations: List<SavingAllocation>)

    @Query("DELETE FROM saving_allocation WHERE savingId = :savingId")
    suspend fun deleteAllocations(savingId: Long)

    @Insert
    suspend fun insertAllocation(allocation: SavingAllocation)

    @Update
    suspend fun updateAllocation(allocation: SavingAllocation)

    @Delete
    suspend fun deleteAllocation(allocation: SavingAllocation)

    @Query("SELECT * FROM saving_allocation WHERE savingId = :savingId AND goalId = :goalId")
    suspend fun getAllocation(savingId: Long, goalId: Long): SavingAllocation?

    /** 某目標的所有分配，最新的紀錄排前面（轉移超額時從最新的開始搬）。 */
    @Query(
        """
        SELECT saving_allocation.* FROM saving_allocation
        JOIN saving ON saving.id = saving_allocation.savingId
        WHERE saving_allocation.goalId = :goalId
        ORDER BY saving.date DESC, saving.id DESC
        """
    )
    suspend fun allocationsForGoalNewestFirst(goalId: Long): List<SavingAllocation>

    /** 刪除目標後，沒有任何分配的紀錄已無意義，一併清掉。 */
    @Query("DELETE FROM saving WHERE id NOT IN (SELECT savingId FROM saving_allocation)")
    suspend fun deleteOrphanSavings()

    @Transaction
    @Query("SELECT * FROM saving WHERE id = :id")
    suspend fun getWithAllocations(id: Long): SavingWithAllocations?

    @Transaction
    @Query(
        """
        SELECT * FROM saving
        WHERE id IN (SELECT savingId FROM saving_allocation WHERE goalId = :goalId)
        ORDER BY date DESC, id DESC
        """
    )
    fun observeForGoal(goalId: Long): Flow<List<SavingWithAllocations>>

    @Transaction
    @Query("SELECT * FROM saving ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<SavingWithAllocations>>
}
