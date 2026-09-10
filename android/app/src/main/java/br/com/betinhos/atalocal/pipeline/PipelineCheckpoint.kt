package br.com.betinhos.atalocal.pipeline

private val COMPLETED_SEGMENT = Regex("segment-(\\d+)/(\\d+)")
private val PROCESSING_SEGMENT = Regex("processing-(\\d+)/(\\d+)")

fun completedSegmentCount(checkpoint: String?): Int = checkpoint
    ?.let { COMPLETED_SEGMENT.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
    ?.coerceAtLeast(0)
    ?: 0

fun retryCheckpoint(checkpoint: String?): String? {
    if (checkpoint == null) return null
    COMPLETED_SEGMENT.matchEntire(checkpoint)?.let { return checkpoint }
    return PROCESSING_SEGMENT.matchEntire(checkpoint)?.let {
        val current = it.groupValues[1].toIntOrNull() ?: return@let checkpoint
        "segment-${(current - 1).coerceAtLeast(0)}/${it.groupValues[2]}"
    } ?: checkpoint
}
