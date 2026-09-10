package br.com.betinhos.atalocal.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStorageTest {
    @Test fun rejectsStorageBelowSafeMinimum() {
        assertFalse(hasRecordingStorage(49L * 1024L * 1024L))
        assertTrue(hasRecordingStorage(50L * 1024L * 1024L))
    }
}
