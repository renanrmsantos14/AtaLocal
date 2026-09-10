package br.com.betinhos.atalocal.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.data.ProcessingJobEntity
import br.com.betinhos.atalocal.domain.MeetingStatus

class PipelineWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val database = DatabaseProvider.get(applicationContext)
        database.processingJobDao().upsert(
            ProcessingJobEntity(
                meetingId = meetingId,
                status = MeetingStatus.QUEUED,
                checkpoint = "queued"
            )
        )
        return Result.success()
    }

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
    }
}
