package br.com.betinhos.atalocal.domain

import br.com.betinhos.atalocal.data.MeetingEntity
import java.util.UUID

fun createMeeting(title: String, note: String?): MeetingEntity {
    val normalizedTitle = title.trim()
    require(normalizedTitle.isNotEmpty()) { "O título da reunião é obrigatório" }
    return MeetingEntity(
        id = UUID.randomUUID().toString(),
        title = normalizedTitle,
        createdAtEpochMs = System.currentTimeMillis(),
        note = note?.trim()?.takeIf { it.isNotEmpty() }
    )
}
