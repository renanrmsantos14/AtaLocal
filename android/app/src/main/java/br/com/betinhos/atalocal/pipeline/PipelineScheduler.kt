package br.com.betinhos.atalocal.pipeline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.data.ProcessingJobEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import java.io.File
import java.util.concurrent.TimeUnit

object PipelineScheduler {
    suspend fun enqueue(context: Context, meetingId: String, modelPath: String? = null, language: String = "pt") {
        val database = DatabaseProvider.get(context.applicationContext)
        if (modelPath.isNullOrBlank() || !File(modelPath).isFile) {
            val message = "Modelo Whisper não instalado. Abra Modelos e instale um modelo de transcrição."
            database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.FAILED, error = message))
            database.meetingDao().updateStatus(meetingId, MeetingStatus.FAILED, error = message)
            return
        }
        database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.QUEUED, checkpoint = "queued"))
        database.meetingDao().updateStatusClearingError(meetingId, MeetingStatus.QUEUED)
        val request = OneTimeWorkRequestBuilder<PipelineWorker>()
            .setInputData(workDataOf(
                PipelineWorker.KEY_MEETING_ID to meetingId,
                PipelineWorker.KEY_MODEL_PATH to modelPath,
                PipelineWorker.KEY_LANGUAGE to language,
                PipelineWorker.KEY_AUDIO_DIRECTORY to context.filesDir.resolve("meetings").resolve(meetingId).resolve("segments").path
            ))
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pipeline-$meetingId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun regenerateSummary(context: Context, meetingId: String) {
        val database = DatabaseProvider.get(context.applicationContext)
        database.meetingDao().updateStatusClearingError(meetingId, MeetingStatus.GENERATING)
        database.processingJobDao().upsert(ProcessingJobEntity(meetingId, MeetingStatus.GENERATING, checkpoint = "summary"))
        val request = OneTimeWorkRequestBuilder<SummaryWorker>().setInputData(workDataOf(SummaryWorker.KEY_MEETING_ID to meetingId)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("summary-$meetingId", ExistingWorkPolicy.REPLACE, request)
    }
}
