package br.com.betinhos.atalocal.settings

import br.com.betinhos.atalocal.data.MeetingEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AudioRetentionTest {
    @Test fun removesOnlyExpiredMeetingDirectories() {
        val root = createTempDir()
        try {
            val old = MeetingEntity("old", "old", 0)
            val fresh = MeetingEntity("fresh", "fresh", 95)
            File(root, "old").mkdirs(); File(root, "fresh").mkdirs()
            assertEquals(1, cleanupExpiredAudio(root, listOf(old, fresh), 100, RetentionPolicy(10)))
            assertEquals(false, File(root, "old").exists())
            assertEquals(true, File(root, "fresh").exists())
        } finally { root.deleteRecursively() }
    }
}
