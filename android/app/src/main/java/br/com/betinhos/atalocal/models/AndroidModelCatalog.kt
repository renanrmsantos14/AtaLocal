package br.com.betinhos.atalocal.models

object AndroidModelCatalog {
    val whisperSmall = ModelSpec(
        id = "whisper-small-q5_1.bin",
        kind = "whisper",
        version = "1",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        sizeBytes = 190_085_487
    )

    val whisperLargeTurbo = ModelSpec(
        id = "whisper-large-v3-turbo-q5_0.bin",
        kind = "whisper",
        version = "1",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        sizeBytes = 574_041_195
    )

    val qwen3 = ModelSpec(
        id = "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        kind = "llm",
        version = "1",
        url = "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        sha256 = "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
        sizeBytes = 2_497_281_120
    )

    val all = listOf(whisperSmall, whisperLargeTurbo, qwen3)
}
