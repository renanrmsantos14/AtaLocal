package br.com.betinhos.atalocal.summarization

import java.io.File

interface LlamaEngine {
    fun generate(model: File, prompt: String, maxTokens: Int = 1024): String
}

class JniLlamaEngine : LlamaEngine {
    override fun generate(model: File, prompt: String, maxTokens: Int): String {
        require(model.isFile) { "Modelo LLM ausente: ${model.name}" }
        return LlamaNative.generate(model.absolutePath, prompt, maxTokens)
    }
}

object LlamaNative {
    init { System.loadLibrary("llama_jni") }
    @JvmStatic external fun generate(modelPath: String, prompt: String, maxTokens: Int): String
}
