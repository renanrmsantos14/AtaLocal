package br.com.betinhos.atalocal.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioSegmentDao {
    @Query("SELECT * FROM audio_segments WHERE meetingId = :meetingId ORDER BY sequence")
    suspend fun listAll(meetingId: String): List<AudioSegmentEntity>

    @Query("SELECT * FROM audio_segments WHERE meetingId = :meetingId ORDER BY sequence")
    fun observeAll(meetingId: String): Flow<List<AudioSegmentEntity>>

    @Upsert
    suspend fun upsert(segment: AudioSegmentEntity)

    @Query("DELETE FROM audio_segments WHERE meetingId = :meetingId")
    suspend fun deleteForMeeting(meetingId: String)
}
