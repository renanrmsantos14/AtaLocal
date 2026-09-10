package br.com.betinhos.atalocal.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessingJobDao {
    @Query("SELECT * FROM processing_jobs WHERE meetingId = :meetingId")
    fun observe(meetingId: String): Flow<ProcessingJobEntity?>

    @Upsert
    suspend fun upsert(job: ProcessingJobEntity)
}
