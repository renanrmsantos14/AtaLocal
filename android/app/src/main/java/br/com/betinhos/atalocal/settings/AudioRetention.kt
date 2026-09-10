package br.com.betinhos.atalocal.settings

import br.com.betinhos.atalocal.data.MeetingEntity
import java.io.File

fun cleanupExpiredAudio(root: File, meetings: List<MeetingEntity>, now: Long, policy: RetentionPolicy): Int {
    var deleted = 0
    meetings.filter { policy.shouldDelete(now, it.createdAtEpochMs) }.forEach { meeting ->
        val audio = File(root, meeting.id)
        if (audio.isDirectory && audio.deleteRecursively()) deleted++
    }
    return deleted
}
