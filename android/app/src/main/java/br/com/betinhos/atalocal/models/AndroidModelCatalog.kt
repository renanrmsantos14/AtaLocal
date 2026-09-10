package br.com.betinhos.atalocal.models

object AndroidModelCatalog {
    val whisperTiny = ModelSpec(
        id = "whisper-tiny-q5_1.bin",
        kind = "whisper",
        version = "1",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
        sizeBytes = 32_152_673
    )

    val whisperBase = ModelSpec(
        id = "whisper-base-q5_1.bin",
        kind = "whisper",
        version = "1",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        sizeBytes = 59_707_625
    )

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

    val qwen25Small = ModelSpec(
        id = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        kind = "llm",
        version = "1",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        sizeBytes = 1_117_320_736
    )

    val qwen3 = ModelSpec(
        id = "Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        kind = "llm",
        version = "1",
        url = "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
        sha256 = "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597",
        sizeBytes = 2_497_281_120
    )

    val all = listOf(whisperTiny, whisperBase, whisperSmall, whisperLargeTurbo, qwen25Small, qwen3)
}
