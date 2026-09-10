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
        val stepMs = segmentDurationMs - overlapMs
        val sequence = ((elapsedMs - startMs) / stepMs).toInt()
        return AudioSegmentPlan(sequence, startMs + sequence * stepMs)
    }
}

class AudioTail(private val capacity: Int) {
    private val samples = ShortArray(capacity)
    private var count = 0
    private var cursor = 0

    init { require(capacity > 0) }

    fun append(source: ShortArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= source.size)
        repeat(length) { index ->
            samples[cursor] = source[offset + index]
            cursor = (cursor + 1) % capacity
            count = (count + 1).coerceAtMost(capacity)
        }
    }

    fun snapshot(): ShortArray {
        val result = ShortArray(count)
        val start = if (count == capacity) cursor else 0
        repeat(count) { index -> result[index] = samples[(start + index) % capacity] }
        return result
    }
}

fun recoverSegments(fileNames: List<String>): List<String> = fileNames
    .filter { it.endsWith(".wav") && !it.endsWith(".wav.tmp") }
    .sorted()
