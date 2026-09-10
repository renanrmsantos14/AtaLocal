package br.com.betinhos.atalocal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingStatusTest {
    @Test fun progressesThroughTheHappyPath() {
        var status = MeetingStatus.DRAFT
        repeat(7) { status = status.next()!! }
        assertEquals(MeetingStatus.READY, status)
        assertTrue(status.isTerminal())
    }

    @Test fun terminalStatesCannotProgress() {
        assertEquals(null, MeetingStatus.FAILED.next())
        assertEquals(null, MeetingStatus.CANCELLED.next())
    }

    @Test fun exposesPortugueseUserFacingLabels() {
        assertEquals("Transcrevendo áudio", MeetingStatus.TRANSCRIBING.userLabel())
        assertEquals("Gerando ata", MeetingStatus.GENERATING.userLabel())
        assertEquals("Pronta", MeetingStatus.READY.userLabel())
        assertEquals("Falhou", MeetingStatus.FAILED.userLabel())
    }
}
