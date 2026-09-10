package br.com.betinhos.atalocal.settings

import br.com.betinhos.atalocal.data.MeetingEntity
import java.io.File

fun cleanupExpiredAudio(root: File, meetings: List<MeetingEntity>, now: Long, policy: RetentionPolicy): Int {
    var deleted = 0
    val nowDays = now / MILLIS_PER_DAY
    meetings.filter { policy.shouldDelete(nowDays, it.createdAtEpochMs / MILLIS_PER_DAY) }.forEach { meeting ->
        val audio = File(root, meeting.id)
        if (audio.isDirectory && audio.deleteRecursively()) deleted++
    }
    return deleted
}

private const val MILLIS_PER_DAY = 86_400_000L
