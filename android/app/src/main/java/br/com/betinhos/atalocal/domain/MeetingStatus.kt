package br.com.betinhos.atalocal.domain

enum class MeetingStatus {
    DRAFT, RECORDING, RECORDED, QUEUED, TRANSCRIBING, TRANSCRIBED,
    GENERATING, READY, FAILED, CANCELLED
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
