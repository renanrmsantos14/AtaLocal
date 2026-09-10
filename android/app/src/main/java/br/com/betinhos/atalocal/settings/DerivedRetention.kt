package br.com.betinhos.atalocal.settings

import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.data.MeetingEntity
import br.com.betinhos.atalocal.domain.MeetingStatus

fun expiredMeetingIds(meetings: List<MeetingEntity>, nowDays: Long, policy: RetentionPolicy): List<String> = meetings
    .filter { it.status in setOf(MeetingStatus.READY, MeetingStatus.FAILED, MeetingStatus.CANCELLED) }
    .filter { policy.shouldDelete(nowDays, it.createdAtEpochMs / MILLIS_PER_DAY) }
    .map { it.id }

suspend fun cleanupExpiredDerivedData(
    database: AtaLocalDatabase,
    meetings: List<MeetingEntity>,
    nowDays: Long,
    transcriptPolicy: RetentionPolicy,
    artifactPolicy: RetentionPolicy
): Int {
    val transcriptIds = expiredMeetingIds(meetings, nowDays, transcriptPolicy)
    val artifactIds = expiredMeetingIds(meetings, nowDays, artifactPolicy)
    transcriptIds.forEach { database.transcriptSegmentDao().deleteForMeeting(it) }
    artifactIds.forEach { database.artifactDao().deleteForMeeting(it) }
    return transcriptIds.size + artifactIds.size
}

private const val MILLIS_PER_DAY = 86_400_000L
