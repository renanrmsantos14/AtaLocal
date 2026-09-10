package br.com.betinhos.atalocal.audio

import java.io.File

class SegmentFileStore(private val directory: File) {
    fun temporary(sequence: Int): File = File(directory, fileName(sequence) + ".tmp")

    fun completed(sequence: Int): File = File(directory, fileName(sequence))

    fun commit(sequence: Int): File {
        val source = temporary(sequence)
        require(source.isFile) { "Segmento temporário ausente: ${source.name}" }
        val target = completed(sequence)
        check(source.renameTo(target)) { "Não foi possível finalizar ${source.name}" }
        return target
    }

    fun recover(): List<File> = recoverSegments(directory.list()?.toList().orEmpty())
        .map { File(directory, it) }

    private fun fileName(sequence: Int): String = "segment-%03d.wav".format(sequence)
}
