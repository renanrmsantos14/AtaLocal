package br.com.betinhos.atalocal.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptSegmentDao {
    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId ORDER BY startMs")
    suspend fun listAll(meetingId: String): List<TranscriptSegmentEntity>

    @Query("UPDATE transcript_segments SET editedText = :text WHERE id = :id")
    suspend fun updateEditedText(id: String, text: String)

    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId ORDER BY startMs")
    fun observeAll(meetingId: String): Flow<List<TranscriptSegmentEntity>>

    @Upsert
    suspend fun upsertAll(segments: List<TranscriptSegmentEntity>)

    @Query("DELETE FROM transcript_segments WHERE meetingId = :meetingId")
    suspend fun deleteForMeeting(meetingId: String)
}
