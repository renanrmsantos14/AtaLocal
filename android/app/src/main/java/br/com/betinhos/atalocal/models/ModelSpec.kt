package br.com.betinhos.atalocal.models

data class ModelSpec(
    val id: String,
    val kind: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
)
