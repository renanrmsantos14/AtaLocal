package br.com.betinhos.atalocal.audio

import java.io.File

data class ActiveRecording(val meetingId: String, val startedAtEpochMs: Long)

class RecordingSessionStore(private val meetingsDirectory: File) {
    fun start(meetingId: String, startedAtEpochMs: Long) {
        marker(meetingId).apply {
            parentFile?.mkdirs()
            writeText(startedAtEpochMs.toString())
        }
    }

    fun touch(meetingId: String) {
        marker(meetingId).setLastModified(System.currentTimeMillis())
    }

    fun clear(meetingId: String) {
        marker(meetingId).delete()
    }

    fun active(nowEpochMs: Long = System.currentTimeMillis(), maxHeartbeatAgeMs: Long = 15_000L): ActiveRecording? =
        meetingsDirectory.listFiles().orEmpty()
            .mapNotNull { directory ->
                val marker = File(directory, MARKER_NAME)
                if (!marker.isFile) return@mapNotNull null
                val startedAt = marker.readText().trim().toLongOrNull() ?: return@mapNotNull null
                if (nowEpochMs - marker.lastModified() > maxHeartbeatAgeMs) return@mapNotNull null
                ActiveRecording(directory.name, startedAt)
            }
            .firstOrNull()

    fun isStale(meetingId: String, nowEpochMs: Long = System.currentTimeMillis(), maxHeartbeatAgeMs: Long = 15_000L): Boolean {
        val file = marker(meetingId)
        return file.isFile && nowEpochMs - file.lastModified() > maxHeartbeatAgeMs
    }

    private fun marker(meetingId: String): File = File(File(meetingsDirectory, meetingId), MARKER_NAME)

    companion object { private const val MARKER_NAME = "recording.active" }
}
