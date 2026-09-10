package br.com.betinhos.atalocal.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import br.com.betinhos.atalocal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val tag: String, val apkUrl: String)

object AppUpdater {
    private const val RELEASES_URL = "https://api.github.com/repos/renanrmsantos14/AtaLocal/releases/latest"
    private const val APK_SUFFIX = "-kotlin-android-arm64.apk"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "AtaLocal-Android")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode !in 200..299) error("GitHub respondeu ${connection.responseCode}")
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name").removePrefix("v")
            val apk = release.getJSONArray("assets").let { assets ->
                (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(APK_SUFFIX) }
            } ?: return@withContext null
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) null else UpdateInfo(tag, apk.getString("browser_download_url"))
        } finally { connection.disconnect() }
    }

    suspend fun downloadAndInstall(context: Context, update: UpdateInfo) = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "atalocal-${update.tag}.apk")
        val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        try {
            if (connection.responseCode !in 200..299) error("Download respondeu ${connection.responseCode}")
            connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } finally { connection.disconnect() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        return (0..2).firstNotNullOfOrNull { index ->
            (remoteParts.getOrElse(index) { 0 } - currentParts.getOrElse(index) { 0 }).takeIf { it != 0 }
        }?.let { it > 0 } ?: false
    }
}
