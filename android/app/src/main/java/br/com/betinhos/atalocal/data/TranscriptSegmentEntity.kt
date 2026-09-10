package br.com.betinhos.atalocal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcript_segments")
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float?,
    val editedText: String? = null
)
