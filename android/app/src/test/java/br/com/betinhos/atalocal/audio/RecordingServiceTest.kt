package br.com.betinhos.atalocal.audio

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordingServiceTest {
    @Test fun explainsAudioRecordFailure() {
        val message = audioReadFailureMessage(-6)

        assertTrue(message.contains("microfone"))
        assertTrue(message.contains("-6"))
    }

    @Test fun stopsOnlyAnActiveRecorder() {
        assertTrue(shouldStopRecorder(android.media.AudioRecord.RECORDSTATE_RECORDING))
        assertFalse(shouldStopRecorder(android.media.AudioRecord.RECORDSTATE_STOPPED))
    }
}
