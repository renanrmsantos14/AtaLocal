package br.com.betinhos.atalocal.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts WHERE meetingId = :meetingId AND type = :type ORDER BY generatedAtEpochMs DESC")
    fun observe(meetingId: String, type: String = "minutes"): Flow<List<ArtifactEntity>>

    @Upsert
    suspend fun upsert(artifact: ArtifactEntity)

    @Query("DELETE FROM artifacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM artifacts WHERE meetingId = :meetingId")
    suspend fun deleteForMeeting(meetingId: String)
}
