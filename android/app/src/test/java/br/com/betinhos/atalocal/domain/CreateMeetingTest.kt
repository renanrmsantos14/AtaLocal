package br.com.betinhos.atalocal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CreateMeetingTest {
    @Test fun createsDraftWithTrimmedTitleAndOptionalNote() {
        val meeting = createMeeting("  Reunião semanal  ", "  pauta comercial  ")

        assertNotNull(meeting.id)
        assertEquals("Reunião semanal", meeting.title)
        assertEquals("pauta comercial", meeting.note)
        assertEquals(MeetingStatus.DRAFT, meeting.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankTitle() {
        createMeeting("   ", null)
    }
}
