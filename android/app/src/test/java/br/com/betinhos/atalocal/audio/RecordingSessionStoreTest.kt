package br.com.betinhos.atalocal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecordingSessionStoreTest {
    @Test fun discoversOnlyFreshRecordingSession() {
        val root = Files.createTempDirectory("atalocal-sessions-").toFile()
        try {
            val store = RecordingSessionStore(root)
            store.start("meeting-1", 123L)
            assertEquals(ActiveRecording("meeting-1", 123L), store.active(maxHeartbeatAgeMs = Long.MAX_VALUE))
            store.clear("meeting-1")
            assertNull(store.active(maxHeartbeatAgeMs = Long.MAX_VALUE))
        } finally { root.deleteRecursively() }
    }
}
