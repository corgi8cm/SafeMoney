package io.github.corgi8cm.safemoney.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deletions: List<Deletion>)

    @Query("DELETE FROM deletion WHERE uid IN (:uids)")
    suspend fun deleteByUids(uids: List<String>)

    @Query("SELECT * FROM deletion ORDER BY deletedAt")
    suspend fun getAll(): List<Deletion>
}
