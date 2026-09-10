package br.com.betinhos.atalocal.audio

fun recordingElapsedSeconds(
    startedAtEpochMs: Long,
    nowEpochMs: Long,
    paused: Boolean,
    pauseStartedAtEpochMs: Long = 0L,
    pausedDurationMs: Long = 0L
): Long {
    val pausedNow = if (paused) (nowEpochMs - pauseStartedAtEpochMs).coerceAtLeast(0L) else 0L
    return ((nowEpochMs - startedAtEpochMs - pausedDurationMs - pausedNow) / 1_000L).coerceAtLeast(0L)
}
