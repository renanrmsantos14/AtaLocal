package br.com.betinhos.atalocal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val type: String,
    val content: String,
    val modelVersion: String?,
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
    val editedByUser: Boolean = false
)
