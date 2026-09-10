package br.com.betinhos.atalocal.models

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import br.com.betinhos.atalocal.data.DatabaseProvider
import br.com.betinhos.atalocal.data.ModelInstallEntity

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val spec = AndroidModelCatalog.all.firstOrNull { it.id == id } ?: return Result.failure()
        val dao = DatabaseProvider.get(applicationContext).modelInstallDao()
        val directory = applicationContext.filesDir.resolve("models")
        val target = directory.resolve(spec.id)
        val partial = directory.resolve("${spec.id}.download")
        dao.upsert(ModelInstallEntity(id, spec.kind, spec.version, target.path, spec.sizeBytes, spec.sha256, status = "DOWNLOADING", downloadedBytes = partial.length()))
        return try {
            val file = ModelDownloader(directory).download(spec) { done, total ->
                setProgress(Data.Builder().putLong(KEY_DONE, done).putLong(KEY_TOTAL, total).build())
            }
            dao.upsert(ModelInstallEntity(id, spec.kind, spec.version, file.path, spec.sizeBytes, spec.sha256, status = "INSTALLED", downloadedBytes = spec.sizeBytes))
            Result.success()
        } catch (error: Throwable) {
            dao.upsert(ModelInstallEntity(id, spec.kind, spec.version, target.path, spec.sizeBytes, spec.sha256, status = "FAILED", downloadedBytes = partial.length(), error = error.message))
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ID = "model_id"
        const val KEY_DONE = "downloaded_bytes"
        const val KEY_TOTAL = "total_bytes"
    }
}
