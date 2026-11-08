package io.github.corgi8cm.safemoney.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val GOAL_WITH_SAVED = """
    SELECT goal.*,
        COALESCE(
            (SELECT SUM(saving_allocation.amount) FROM saving_allocation
             WHERE saving_allocation.goalId = goal.id),
            0
        ) AS saved
    FROM goal
"""

@Dao
interface GoalDao {
    @Insert
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun getById(id: Long): Goal?

    @Query("$GOAL_WITH_SAVED ORDER BY goal.createdAt ASC, goal.id ASC")
    fun observeAllWithSaved(): Flow<List<GoalWithSaved>>

    @Query("$GOAL_WITH_SAVED WHERE goal.id = :id")
    fun observeWithSaved(id: Long): Flow<GoalWithSaved?>

    @Query("$GOAL_WITH_SAVED WHERE goal.id = :id")
    suspend fun getWithSaved(id: Long): GoalWithSaved?
}
