package br.com.betinhos.atalocal.audio

const val MIN_RECORDING_FREE_BYTES = 50L * 1024L * 1024L

fun hasRecordingStorage(availableBytes: Long, minimumBytes: Long = MIN_RECORDING_FREE_BYTES): Boolean =
    availableBytes >= minimumBytes
