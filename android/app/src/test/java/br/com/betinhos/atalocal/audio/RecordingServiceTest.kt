package br.com.betinhos.atalocal.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingServiceTest {
    @Test fun explainsAudioRecordFailure() {
        val message = audioReadFailureMessage(-6)

        assertTrue(message.contains("microfone"))
        assertTrue(message.contains("-6"))
    }
}
