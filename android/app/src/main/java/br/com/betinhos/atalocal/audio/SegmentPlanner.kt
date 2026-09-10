package br.com.betinhos.atalocal.audio

data class AudioSegmentPlan(val sequence: Int, val startMs: Long)

class SegmentPlanner(
    private val segmentDurationMs: Long = 60_000,
    private val overlapMs: Long = 1_000
) {
    init {
        require(segmentDurationMs > overlapMs)
        require(overlapMs >= 0)
    }

    fun next(startMs: Long, elapsedMs: Long): AudioSegmentPlan {
        require(startMs >= 0 && elapsedMs >= startMs)
        val sequence = ((elapsedMs - startMs) / segmentDurationMs).toInt()
        return AudioSegmentPlan(sequence, startMs + sequence * segmentDurationMs)
    }
}

fun recoverSegments(fileNames: List<String>): List<String> = fileNames
    .filter { it.endsWith(".wav") && !it.endsWith(".wav.tmp") }
    .sorted()
