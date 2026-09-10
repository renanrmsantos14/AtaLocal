package br.com.betinhos.atalocal.diagnostics

import android.content.Context
import android.os.Build
import android.os.StatFs
import br.com.betinhos.atalocal.data.ModelInstallEntity

data class DiagnosticsSnapshot(
    val appVersion: String,
    val androidVersion: String,
    val availableStorageBytes: Long,
    val installedModels: List<ModelInstallEntity>,
    val lastError: String?
)

fun collectDiagnostics(context: Context, models: List<ModelInstallEntity>, lastError: String?): DiagnosticsSnapshot {
    val stats = StatFs(context.filesDir.path)
    return DiagnosticsSnapshot(
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
        availableStorageBytes = stats.availableBytes,
        installedModels = models,
        lastError = lastError
    )
}
