package br.com.betinhos.atalocal.data

import androidx.room.Entity

@Entity(tableName = "audio_segments", primaryKeys = ["meetingId", "sequence"])
data class AudioSegmentEntity(
    val meetingId: String,
    val sequence: Int,
    val path: String,
    val durationMs: Long,
    val status: String = "COMPLETE",
    val sha256: String? = null,
    val error: String? = null
)
