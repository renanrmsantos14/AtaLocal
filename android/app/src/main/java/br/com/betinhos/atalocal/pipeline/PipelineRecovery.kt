package br.com.betinhos.atalocal.pipeline

import android.content.Context
import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.data.AudioSegmentEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import br.com.betinhos.atalocal.models.selectWhisperModel
import br.com.betinhos.atalocal.audio.SegmentFileStore
import br.com.betinhos.atalocal.audio.RecordingSessionStore
import kotlinx.coroutines.flow.first
import java.io.File

object PipelineRecovery {
    suspend fun recover(context: Context, database: AtaLocalDatabase) {
        val preferences = context.getSharedPreferences("atalocal.settings", Context.MODE_PRIVATE)
        val language = preferences.getString("transcription_language", "pt") ?: "pt"
        val preferredWhisper = preferences.getString("default_whisper_model", null)
        val whisperModel = selectWhisperModel(database.modelInstallDao().observeAll().first(), preferredWhisper)
        val recordingSessions = RecordingSessionStore(context.filesDir.resolve("meetings"))

        database.meetingDao().listAll().forEach { meeting ->
            val segmentsDirectory = context.filesDir.resolve("meetings").resolve(meeting.id).resolve("segments")
            var effectiveStatus = meeting.status
            if (meeting.status == MeetingStatus.RECORDING) {
                if (!recordingSessions.isStale(meeting.id)) {
                    effectiveStatus = MeetingStatus.RECORDING
                    return@forEach
                }
                recordingSessions.clear(meeting.id)
                val recovered = SegmentFileStore(segmentsDirectory).recover()
                if (recovered.isNotEmpty()) {
                    val indexed = database.audioSegmentDao().listAll(meeting.id).associateBy { it.sequence }
                    recovered.forEach { file ->
                        val sequence = Regex("segment-(\\d+)\\.wav").matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                        if (indexed[sequence] == null) {
                            database.audioSegmentDao().upsert(AudioSegmentEntity(meeting.id, sequence, file.absolutePath, durationMs(file)))
                        }
                    }
                    database.meetingDao().updateStatus(meeting.id, MeetingStatus.RECORDED, recoveredDuration(recovered))
                    effectiveStatus = MeetingStatus.RECORDED
                } else {
                    database.meetingDao().updateStatus(meeting.id, MeetingStatus.CANCELLED, error = "A gravação foi interrompida antes de salvar um segmento.")
                    effectiveStatus = MeetingStatus.CANCELLED
                }
            }

            when (effectiveStatus) {
                MeetingStatus.RECORDED, MeetingStatus.QUEUED, MeetingStatus.TRANSCRIBING ->
                    PipelineScheduler.enqueue(context, meeting.id, whisperModel, language)
                MeetingStatus.TRANSCRIBED, MeetingStatus.GENERATING ->
                    PipelineScheduler.regenerateSummary(context, meeting.id)
                else -> Unit
            }
        }
    }

    private fun recoveredDuration(files: List<File>): Long = files.sumOf { file ->
        durationMs(file) / 1_000L
    }

    private fun durationMs(file: File): Long = ((file.length() - 44L).coerceAtLeast(0L) * 1_000L / (RecordingConstants.BYTES_PER_SAMPLE * RecordingConstants.SAMPLE_RATE)).coerceAtLeast(0L)

    private object RecordingConstants {
        const val BYTES_PER_SAMPLE = 2L
        const val SAMPLE_RATE = 16_000L
    }
}
