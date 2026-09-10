package br.com.betinhos.atalocal.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelDownloader(private val directory: File) {
    suspend fun download(spec: ModelSpec, onProgress: (Long, Long) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = File(directory, spec.id)
        val partial = File(directory, "${spec.id}.download")
        var downloaded = if (partial.isFile) partial.length() else 0L
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
        }
        try {
            if (downloaded > 0 && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                partial.delete()
                downloaded = 0
            }
            val expected = if (connection.contentLengthLong > 0) downloaded + connection.contentLengthLong else spec.sizeBytes
            connection.inputStream.use { input ->
                FileOutputStream(partial, downloaded > 0).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, expected)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        check(downloaded == spec.sizeBytes) { "Tamanho inválido para ${spec.id}: $downloaded" }
        check(sha256(partial) == spec.sha256.lowercase()) { "SHA-256 inválido para ${spec.id}" }
        check(partial.renameTo(target)) { "Não foi possível instalar ${spec.id}" }
        target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
