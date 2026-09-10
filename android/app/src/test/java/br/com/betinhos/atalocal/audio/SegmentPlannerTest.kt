package br.com.betinhos.atalocal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlannerTest {
    @Test fun createsSequentialSegmentsWithConfiguredDuration() {
        val planner = SegmentPlanner(segmentDurationMs = 60_000, overlapMs = 1_000)

        assertEquals(0, planner.next(startMs = 0, elapsedMs = 58_999).sequence)
        assertEquals(1, planner.next(startMs = 0, elapsedMs = 59_000).sequence)
        assertEquals(59_000, planner.next(startMs = 0, elapsedMs = 59_000).startMs)
    }

    @Test fun recoveryIgnoresTemporaryFilesAndKeepsCompletedOrder() {
        val recovered = recoverSegments(
            listOf("segment-000.wav", "segment-001.wav.tmp", "segment-002.wav")
        )

        assertEquals(listOf("segment-000.wav", "segment-002.wav"), recovered)
        assertTrue(recovered.zipWithNext().all { it.first < it.second })
    }

    @Test fun keepsTheMostRecentSamplesForSegmentOverlap() {
        val tail = AudioTail(3)
        tail.append(shortArrayOf(1, 2), 0, 2)
        tail.append(shortArrayOf(3, 4, 5), 0, 3)

        assertEquals(listOf<Short>(3, 4, 5), tail.snapshot().toList())
    }
}
