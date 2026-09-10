package br.com.betinhos.atalocal.transcription

import java.io.File

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
        return WhisperNative.transcribe(model.absolutePath, audio.absolutePath, language)
    }
}

private object WhisperNative {
    init { System.loadLibrary("whisper_jni") }

    external fun transcribe(modelPath: String, audioPath: String, language: String): List<NativeTranscriptSegment>
}
