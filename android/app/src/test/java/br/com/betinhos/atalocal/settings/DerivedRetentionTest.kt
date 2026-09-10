package br.com.betinhos.atalocal.settings

import br.com.betinhos.atalocal.data.MeetingEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DerivedRetentionTest {
    @Test fun identifiesOnlyExpiredMeetings() {
        val meetings = listOf(
            MeetingEntity("old", "old", 0, status = MeetingStatus.READY),
            MeetingEntity("fresh", "fresh", 90 * 86_400_000L, status = MeetingStatus.READY)
        )

        assertEquals(listOf("old"), expiredMeetingIds(meetings, 100, RetentionPolicy(30)))
    }
}
