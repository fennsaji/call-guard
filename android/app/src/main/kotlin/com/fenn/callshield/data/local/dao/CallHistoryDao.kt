package com.fenn.callshield.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fenn.callshield.data.local.entity.CallHistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY screenedAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<CallHistoryEntry>>

    @Query("SELECT * FROM call_history ORDER BY screenedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<CallHistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallHistoryEntry)

    @Query("SELECT COUNT(*) FROM call_history")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM call_history WHERE outcome IN ('rejected', 'silenced')")
    suspend fun blockedCount(): Int

    @Query("SELECT COUNT(*) FROM call_history")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_history WHERE outcome IN ('rejected', 'silenced')")
    fun observeBlockedCount(): Flow<Int>

    /** Prune old entries — keep last 1000 records. */
    @Query(
        """
        DELETE FROM call_history WHERE id NOT IN (
            SELECT id FROM call_history ORDER BY screenedAt DESC LIMIT 1000
        )
        """
    )
    suspend fun pruneOldEntries()

    @Query("DELETE FROM call_history")
    suspend fun deleteAll()
}
