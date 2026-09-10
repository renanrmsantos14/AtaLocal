package br.com.betinhos.atalocal.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class PipelineCheckpointTest {
    @Test fun resumesAfterLastCompletedSegment() {
        assertEquals(2, completedSegmentCount("segment-2/5"))
        assertEquals(0, completedSegmentCount("processing-3/5"))
        assertEquals(0, completedSegmentCount(null))
    }

    @Test fun convertsActiveSegmentToLastCompletedSegmentForRetry() {
        assertEquals("segment-2/6", retryCheckpoint("processing-3/6"))
    }

    @Test fun keepsCompletedCheckpointUnchanged() {
        assertEquals("segment-3/6", retryCheckpoint("segment-3/6"))
    }
}
