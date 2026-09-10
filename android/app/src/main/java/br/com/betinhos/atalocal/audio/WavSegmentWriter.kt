package br.com.betinhos.atalocal.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class WavSegmentWriter(private val file: File, private val sampleRate: Int = 16_000) {
    private var output: FileOutputStream? = null
    private var dataBytes = 0

    fun open() {
        file.parentFile?.mkdirs()
        output = FileOutputStream(file).also { it.write(ByteArray(44)) }
    }

    fun write(samples: ShortArray, count: Int) {
        val stream = requireNotNull(output)
        val bytes = ByteArray(count * 2)
        for (index in 0 until count) {
            val value = samples[index].toInt()
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value shr 8).toByte()
        }
        stream.write(bytes)
        dataBytes += bytes.size
    }

    fun close() {
        output?.close()
        output = null
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(36 + dataBytes))
            raf.writeBytes("WAVEfmt ")
            raf.writeInt(Integer.reverseBytes(16))
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.writeInt(Integer.reverseBytes(sampleRate * 2))
            raf.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(dataBytes))
        }
    }
}
