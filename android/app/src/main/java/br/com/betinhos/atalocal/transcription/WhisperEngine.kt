package br.com.betinhos.atalocal.transcription

import java.io.File
import org.json.JSONArray

data class NativeTranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float?
)

interface WhisperEngine {
    fun transcribe(model: File, audio: File, language: String = "pt"): List<NativeTranscriptSegment>
}

class JniWhisperEngine : WhisperEngine {
    override fun transcribe(model: File, audio: File, language: String): List<NativeTranscriptSegment> {
        require(model.isFile) { "Modelo Whisper ausente: ${model.name}" }
        require(audio.isFile) { "Áudio ausente: ${audio.name}" }
        val raw = WhisperNative.transcribeJson(model.absolutePath, audio.absolutePath, language)
            ?: error("Whisper não retornou resultado para ${audio.name}")
        val json = JSONArray(raw)
        return List(json.length()) { index ->
            val item = json.getJSONObject(index)
            NativeTranscriptSegment(
                startMs = item.getLong("start_ms"),
                endMs = item.getLong("end_ms"),
                text = item.getString("text"),
                confidence = item.optDouble("confidence").takeUnless { it.isNaN() }?.toFloat()
            )
        }
    }
}

object WhisperNative {
    init { System.loadLibrary("whisper_jni") }

    @JvmStatic external fun transcribeJson(modelPath: String, audioPath: String, language: String): String
}
