package br.com.betinhos.atalocal.pipeline

import br.com.betinhos.atalocal.data.ProcessingJobEntity
import br.com.betinhos.atalocal.domain.MeetingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PipelineSchedulerTest {
    @Test fun preservesCheckpointWhenRecoveryRequeuesTranscription() {
        val previous = ProcessingJobEntity(
            meetingId = "meeting-1",
            status = MeetingStatus.TRANSCRIBING,
            progress = .5f,
            checkpoint = "segment-3/6",
            error = "tentativa anterior falhou"
        )

        val queued = PipelineScheduler.queueJob("meeting-1", previous)

        assertEquals(MeetingStatus.TRANSCRIBING, queued.status)
        assertEquals("segment-3/6", queued.checkpoint)
        assertEquals(null, queued.error)
    }

    @Test fun startsNewMeetingAtQueue() {
        val queued = PipelineScheduler.queueJob("meeting-2", null)

        assertEquals(MeetingStatus.QUEUED, queued.status)
        assertEquals("queued", queued.checkpoint)
    }
}
