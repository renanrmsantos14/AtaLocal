package br.com.betinhos.atalocal.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings")
    suspend fun listAll(): List<MeetingEntity>
    @Query("SELECT * FROM meetings WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<MeetingEntity?>
    @Query("SELECT * FROM meetings ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<MeetingEntity>>

    @Upsert
    suspend fun upsert(meeting: MeetingEntity)

    @Query("UPDATE meetings SET status = :status, durationSeconds = CASE WHEN :durationSeconds > 0 THEN :durationSeconds ELSE durationSeconds END, error = CASE WHEN :error IS NOT NULL THEN :error ELSE error END WHERE id = :id")
    suspend fun updateStatus(id: String, status: br.com.betinhos.atalocal.domain.MeetingStatus, durationSeconds: Long = 0, error: String? = null)

    @Query("UPDATE meetings SET status = :status, error = NULL WHERE id = :id")
    suspend fun updateStatusClearingError(id: String, status: br.com.betinhos.atalocal.domain.MeetingStatus)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: String)
}
