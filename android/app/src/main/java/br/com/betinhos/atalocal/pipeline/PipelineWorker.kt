package br.com.betinhos.atalocal.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.data.ProcessingJobEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import br.com.betinhos.atalocal.transcription.JniWhisperEngine
import br.com.betinhos.atalocal.models.selectModel
import br.com.betinhos.atalocal.summarization.JniLlamaEngine
import br.com.betinhos.atalocal.summarization.buildFactualPrompt
import br.com.betinhos.atalocal.summarization.parseMinutes
import br.com.betinhos.atalocal.data.ArtifactEntity
import kotlinx.coroutines.flow.first
import java.io.File

class PipelineWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val database = DatabaseProvider.get(applicationContext)
        val meetingDao = database.meetingDao()
        val modelPath = inputData.getString(KEY_MODEL_PATH)
        val language = inputData.getString(KEY_LANGUAGE) ?: "pt"
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
                val transcript = engine.transcribe(File(modelPath), audio, language)
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
            val models = database.modelInstallDao().observeAll().first()
            val llamaPath = selectModel(models, "llm")
                ?: return fail(database, meetingId, "Modelo LLM não instalado")
            meetingDao.updateStatus(meetingId, MeetingStatus.GENERATING)
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.GENERATING, 0f, "summary"))
            val transcript = dao.listAll(meetingId).joinToString("\n") { "[${it.startMs}ms] ${it.text}" }
            val minutes = parseMinutes(JniLlamaEngine().generate(File(llamaPath), buildFactualPrompt(transcript)))
            database.artifactDao().upsert(ArtifactEntity(
                id = "$meetingId-minutes", meetingId = meetingId, type = "minutes",
                content = br.com.betinhos.atalocal.export.minutesToMarkdown(minutes), modelVersion = models.first { it.filePath == llamaPath }.version
            ))
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.READY, 1f, "complete"))
            meetingDao.updateStatus(meetingId, MeetingStatus.READY)
            Result.success()
        } catch (error: Throwable) {
            if (runAttemptCount < 2) {
                database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.QUEUED, error = "Tentativa ${runAttemptCount + 1} falhou; tentando novamente"))
                meetingDao.updateStatus(meetingId, MeetingStatus.QUEUED)
                return Result.retry()
            }
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
        const val KEY_LANGUAGE = "language"
    }
}
