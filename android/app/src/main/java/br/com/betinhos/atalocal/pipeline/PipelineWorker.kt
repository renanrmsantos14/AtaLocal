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
import br.com.betinhos.atalocal.summarization.parseMinutesOrFallback
import br.com.betinhos.atalocal.data.ArtifactEntity
import kotlinx.coroutines.flow.first
import java.io.File
import android.util.Log

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
            meetingDao.updateStatusClearingError(meetingId, MeetingStatus.TRANSCRIBING)
            val dao = database.transcriptSegmentDao()
            val engine = JniWhisperEngine()
            val currentJob = database.processingJobDao().observe(meetingId).first()
            val existingTranscript = dao.listAll(meetingId)
            val resumeFrom = if (currentJob?.status == MeetingStatus.TRANSCRIBING) {
                completedSegmentCount(currentJob.checkpoint).coerceIn(0, segments.size)
            } else 0
            if (resumeFrom == 0) dao.deleteForMeeting(meetingId)
            segments.drop(resumeFrom).forEachIndexed { offset, audio ->
                val index = resumeFrom + offset
                val segmentProgress = index.toFloat() / segments.size
                database.processingJobDao().upsert(
                    ProcessingJobEntity(
                        meetingId,
                        MeetingStatus.TRANSCRIBING,
                        segmentProgress,
                        "processing-${index + 1}/${segments.size}"
                    )
                )
                Log.i(TAG, "Transcrevendo ${audio.name} de ${segments.size} para $meetingId")
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
                        (index + 1).toFloat() / segments.size, "segment-${index + 1}/${segments.size}")
                )
            }
            if (dao.listAll(meetingId).isEmpty() && existingTranscript.isEmpty()) {
                return fail(database, meetingId, "Nenhuma fala foi detectada. Tente gravar mais perto do microfone e processe novamente.")
            }
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.TRANSCRIBED, 1f, "complete"))
            val models = database.modelInstallDao().observeAll().first()
            val preferredLlm = applicationContext.getSharedPreferences("atalocal.settings", Context.MODE_PRIVATE).getString("default_llm_model", null)
            val llamaPath = selectModel(models, "llm", preferredLlm)
                ?: return fail(database, meetingId, "Modelo LLM não instalado")
            meetingDao.updateStatusClearingError(meetingId, MeetingStatus.GENERATING)
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.GENERATING, 0f, "summary"))
            val transcript = dao.listAll(meetingId).joinToString("\n") { "[${it.startMs}ms] ${it.text}" }
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.GENERATING, 0f, "gerando-ata"))
            Log.i(TAG, "Gerando ata para $meetingId com ${transcript.length} caracteres de transcrição")
            val minutes = parseMinutesOrFallback(JniLlamaEngine().generate(File(llamaPath), buildFactualPrompt(transcript)), transcript)
            database.artifactDao().upsert(ArtifactEntity(
                id = "$meetingId-minutes", meetingId = meetingId, type = "minutes",
                content = br.com.betinhos.atalocal.export.minutesToMarkdown(minutes), modelVersion = models.first { it.filePath == llamaPath }.version
            ))
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.READY, 1f, "complete"))
            meetingDao.updateStatusClearingError(meetingId, MeetingStatus.READY)
            Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "Falha no pipeline de $meetingId na tentativa $runAttemptCount", error)
            if (runAttemptCount < 2) {
                val checkpoint = database.processingJobDao().observe(meetingId).first()?.checkpoint
                database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.TRANSCRIBING, checkpoint = checkpoint, error = "Tentativa ${runAttemptCount + 1} falhou; retomando do último segmento salvo"))
                meetingDao.updateStatus(meetingId, MeetingStatus.TRANSCRIBING)
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
        private const val TAG = "AtaLocalPipeline"
        const val KEY_MEETING_ID = "meeting_id"
        const val KEY_MODEL_PATH = "whisper_model_path"
        const val KEY_AUDIO_DIRECTORY = "audio_directory"
        const val KEY_LANGUAGE = "language"
    }
}
