package br.com.betinhos.atalocal.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.betinhos.atalocal.data.ArtifactEntity
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.data.ProcessingJobEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import br.com.betinhos.atalocal.models.selectModel
import br.com.betinhos.atalocal.summarization.JniLlamaEngine
import br.com.betinhos.atalocal.summarization.buildFactualPrompt
import br.com.betinhos.atalocal.summarization.parseMinutes
import kotlinx.coroutines.flow.first
import java.io.File

class SummaryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val database = DatabaseProvider.get(applicationContext)
        return try {
            val models = database.modelInstallDao().observeAll().first()
            val llamaPath = selectModel(models, "llama") ?: return fail(database, meetingId, "Modelo LLM não instalado")
            val segments = database.transcriptSegmentDao().listAll(meetingId)
            if (segments.isEmpty()) return fail(database, meetingId, "Transcrição vazia")
            database.meetingDao().updateStatus(meetingId, MeetingStatus.GENERATING)
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.GENERATING, 0f, "summary"))
            val transcript = segments.joinToString("\n") { "[${it.startMs}ms] ${it.text}" }
            val minutes = parseMinutes(JniLlamaEngine().generate(File(llamaPath), buildFactualPrompt(transcript)))
            database.artifactDao().upsert(ArtifactEntity("$meetingId-minutes", meetingId, "minutes", br.com.betinhos.atalocal.export.minutesToMarkdown(minutes), models.first { it.filePath == llamaPath }.version))
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.READY, 1f, "complete"))
            database.meetingDao().updateStatus(meetingId, MeetingStatus.READY)
            Result.success()
        } catch (error: Throwable) {
            fail(database, meetingId, error.message ?: "Falha ao regenerar a ata")
        }
    }

    private suspend fun fail(database: br.com.betinhos.atalocal.data.AtaLocalDatabase, id: String, message: String): Result {
        database.processingJobDao().upsert(ProcessingJobEntity(id, MeetingStatus.FAILED, error = message))
        database.meetingDao().updateStatus(id, MeetingStatus.FAILED, error = message)
        return Result.failure()
    }

    companion object { const val KEY_MEETING_ID = "meeting_id" }
}
