package br.com.betinhos.atalocal.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.data.ProcessingJobEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import br.com.betinhos.atalocal.transcription.JniWhisperEngine
import java.io.File

class PipelineWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val database = DatabaseProvider.get(applicationContext)
        val meetingDao = database.meetingDao()
        val modelPath = inputData.getString(KEY_MODEL_PATH)
        if (modelPath.isNullOrBlank() || !File(modelPath).isFile) {
            return fail(database, meetingId, "Modelo Whisper não instalado")
        }
        val audioDirectory = File(inputData.getString(KEY_AUDIO_DIRECTORY) ?: "")
        val segments = audioDirectory.listFiles { file -> file.extension == "wav" }?.sortedBy { it.name }.orEmpty()
        if (segments.isEmpty()) return fail(database, meetingId, "Nenhum segmento de áudio finalizado")

        return try {
            meetingDao.updateStatus(meetingId, MeetingStatus.TRANSCRIBING)
            val dao = database.transcriptSegmentDao()
            val engine = JniWhisperEngine()
            dao.deleteForMeeting(meetingId)
            segments.forEachIndexed { index, audio ->
                val transcript = engine.transcribe(File(modelPath), audio)
                dao.upsertAll(transcript.mapIndexed { itemIndex, item ->
                    br.com.betinhos.atalocal.data.TranscriptSegmentEntity(
                        id = "$meetingId-$index-$itemIndex",
                        meetingId = meetingId,
                        startMs = item.startMs + index * 60_000,
                        endMs = item.endMs + index * 60_000,
                        text = item.text,
                        confidence = item.confidence
                    )
                })
                database.processingJobDao().upsert(
                    ProcessingJobEntity(meetingId, MeetingStatus.TRANSCRIBING,
                        (index + 1).toFloat() / segments.size, index.toString())
                )
            }
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.TRANSCRIBED, 1f, "complete"))
            meetingDao.updateStatus(meetingId, MeetingStatus.TRANSCRIBED)
            Result.success()
        } catch (error: Throwable) {
            fail(database, meetingId, error.message ?: "Falha nativa do Whisper")
        }
    }

    private suspend fun fail(database: br.com.betinhos.atalocal.data.AtaLocalDatabase, id: String, message: String): Result {
        database.processingJobDao().upsert(ProcessingJobEntity(id, MeetingStatus.FAILED, error = message))
        database.meetingDao().updateStatus(id, MeetingStatus.FAILED, error = message)
        return Result.failure()
    }

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        const val KEY_MODEL_PATH = "whisper_model_path"
        const val KEY_AUDIO_DIRECTORY = "audio_directory"
    }
}
