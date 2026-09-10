package br.com.betinhos.atalocal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.betinhos.atalocal.domain.MeetingStatus

@Entity(tableName = "processing_jobs")
data class ProcessingJobEntity(
    @PrimaryKey val meetingId: String,
    val status: MeetingStatus,
    val progress: Float = 0f,
    val checkpoint: String? = null,
    val error: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
