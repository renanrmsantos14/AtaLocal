package br.com.betinhos.atalocal.pipeline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object PipelineScheduler {
    fun enqueue(context: Context, meetingId: String, modelPath: String? = null) {
        val request = OneTimeWorkRequestBuilder<PipelineWorker>()
            .setInputData(workDataOf(
                PipelineWorker.KEY_MEETING_ID to meetingId,
                PipelineWorker.KEY_MODEL_PATH to modelPath,
                PipelineWorker.KEY_AUDIO_DIRECTORY to context.filesDir.resolve("meetings").resolve(meetingId).resolve("segments").path
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pipeline-$meetingId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
