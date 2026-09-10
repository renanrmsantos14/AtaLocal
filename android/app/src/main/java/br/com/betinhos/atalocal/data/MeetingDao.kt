package br.com.betinhos.atalocal.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<MeetingEntity>>

    @Upsert
    suspend fun upsert(meeting: MeetingEntity)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: String)
}
