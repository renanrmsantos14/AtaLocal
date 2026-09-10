package br.com.betinhos.atalocal.pipeline

private val COMPLETED_SEGMENT = Regex("segment-(\\d+)/(\\d+)")

fun completedSegmentCount(checkpoint: String?): Int = checkpoint
    ?.let { COMPLETED_SEGMENT.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
    ?.coerceAtLeast(0)
    ?: 0
