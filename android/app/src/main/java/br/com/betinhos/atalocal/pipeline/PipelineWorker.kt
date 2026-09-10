package br.com.betinhos.atalocal.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.data.ProcessingJobEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import java.io.File

class PipelineWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val database = DatabaseProvider.get(applicationContext)
        val modelPath = inputData.getString(KEY_MODEL_PATH)
        if (modelPath.isNullOrBlank() || !File(modelPath).isFile) {
            database.processingJobDao().upsert(
                ProcessingJobEntity(
                    meetingId = meetingId,
                    status = MeetingStatus.FAILED,
                    checkpoint = "whisper_model_missing",
                    error = "Modelo Whisper não instalado"
                )
            )
            return Result.failure()
        }
        database.processingJobDao().upsert(
            ProcessingJobEntity(meetingId = meetingId, status = MeetingStatus.TRANSCRIBING, checkpoint = "ready")
        )
        return Result.success()
    }

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        const val KEY_MODEL_PATH = "whisper_model_path"
    }
}
