package br.com.betinhos.atalocal.pipeline

import android.content.Context
import br.com.betinhos.atalocal.data.AtaLocalDatabase
import br.com.betinhos.atalocal.domain.MeetingStatus
import br.com.betinhos.atalocal.models.selectWhisperModel
import br.com.betinhos.atalocal.audio.SegmentFileStore
import kotlinx.coroutines.flow.first
import java.io.File

object PipelineRecovery {
    suspend fun recover(context: Context, database: AtaLocalDatabase) {
        val preferences = context.getSharedPreferences("atalocal.settings", Context.MODE_PRIVATE)
        val language = preferences.getString("transcription_language", "pt") ?: "pt"
        val whisperModel = selectWhisperModel(database.modelInstallDao().observeAll().first())

        database.meetingDao().listAll().forEach { meeting ->
            val segmentsDirectory = context.filesDir.resolve("meetings").resolve(meeting.id).resolve("segments")
            var effectiveStatus = meeting.status
            if (meeting.status == MeetingStatus.RECORDING) {
                val recovered = SegmentFileStore(segmentsDirectory).recover()
                if (recovered.isNotEmpty()) {
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
        ((file.length() - 44L).coerceAtLeast(0L) / (RecordingConstants.BYTES_PER_SAMPLE * RecordingConstants.SAMPLE_RATE)).coerceAtLeast(0L)
    }

    private object RecordingConstants {
        const val BYTES_PER_SAMPLE = 2L
        const val SAMPLE_RATE = 16_000L
    }
}
