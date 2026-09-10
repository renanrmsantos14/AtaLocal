package br.com.betinhos.atalocal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.betinhos.atalocal.domain.MeetingStatus

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val durationSeconds: Long = 0,
    val status: MeetingStatus = MeetingStatus.DRAFT,
    val note: String? = null,
    val error: String? = null
)
