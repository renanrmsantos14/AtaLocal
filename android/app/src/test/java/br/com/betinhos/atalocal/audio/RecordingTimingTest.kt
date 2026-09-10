package br.com.betinhos.atalocal.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTimingTest {
    @Test fun excludesCurrentPauseFromElapsedDuration() {
        assertEquals(30L, recordingElapsedSeconds(0L, 90_000L, paused = true, pauseStartedAtEpochMs = 30_000L))
        assertEquals(75L, recordingElapsedSeconds(0L, 120_000L, paused = false, pausedDurationMs = 45_000L))
    }
}
