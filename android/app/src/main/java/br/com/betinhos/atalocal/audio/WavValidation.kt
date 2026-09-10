package br.com.betinhos.atalocal.audio

import java.io.File
import java.io.RandomAccessFile

fun isValidPcm16MonoWav(file: File): Boolean {
    if (!file.isFile || file.length() <= 44L) return false
    return runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE" &&
                String(header, 12, 4, Charsets.US_ASCII) == "fmt " &&
                littleEndianShort(header, 20) == 1 &&
                littleEndianShort(header, 22) == 1 &&
                littleEndianInt(header, 24) == RecordingService.SAMPLE_RATE &&
                littleEndianShort(header, 34) == 16 &&
                String(header, 36, 4, Charsets.US_ASCII) == "data"
        }
    }.getOrDefault(false)
}

private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
    littleEndianShort(bytes, offset) or (littleEndianShort(bytes, offset + 2) shl 16)
