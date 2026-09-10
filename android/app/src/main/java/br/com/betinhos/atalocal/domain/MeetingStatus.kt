package br.com.betinhos.atalocal.domain

enum class MeetingStatus {
    DRAFT, RECORDING, RECORDED, QUEUED, TRANSCRIBING, TRANSCRIBED,
    GENERATING, READY, FAILED, CANCELLED
}

fun MeetingStatus.userLabel(): String = when (this) {
    MeetingStatus.DRAFT -> "Rascunho"
    MeetingStatus.RECORDING -> "Gravando"
    MeetingStatus.RECORDED -> "Áudio salvo"
    MeetingStatus.QUEUED -> "Na fila"
    MeetingStatus.TRANSCRIBING -> "Transcrevendo áudio"
    MeetingStatus.TRANSCRIBED -> "Transcrição pronta"
    MeetingStatus.GENERATING -> "Gerando ata"
    MeetingStatus.READY -> "Pronta"
    MeetingStatus.FAILED -> "Falhou"
    MeetingStatus.CANCELLED -> "Cancelada"
}

fun MeetingStatus.next(): MeetingStatus? = when (this) {
    MeetingStatus.DRAFT -> MeetingStatus.RECORDING
    MeetingStatus.RECORDING -> MeetingStatus.RECORDED
    MeetingStatus.RECORDED -> MeetingStatus.QUEUED
    MeetingStatus.QUEUED -> MeetingStatus.TRANSCRIBING
    MeetingStatus.TRANSCRIBING -> MeetingStatus.TRANSCRIBED
    MeetingStatus.TRANSCRIBED -> MeetingStatus.GENERATING
    MeetingStatus.GENERATING -> MeetingStatus.READY
    MeetingStatus.READY, MeetingStatus.FAILED, MeetingStatus.CANCELLED -> null
}

fun MeetingStatus.isTerminal(): Boolean = this in setOf(
    MeetingStatus.READY, MeetingStatus.FAILED, MeetingStatus.CANCELLED
)
