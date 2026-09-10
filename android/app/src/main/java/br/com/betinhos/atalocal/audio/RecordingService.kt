package br.com.betinhos.atalocal.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import android.os.SystemClock
import kotlin.concurrent.thread

class RecordingService : Service() {
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private var running = false
    @Volatile private var paused = false
    private var segmentStore: SegmentFileStore? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gravação", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_PAUSE) {
            paused = !paused
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        if (!running) startCapture(intent?.getStringExtra(EXTRA_DIRECTORY), intent?.getStringExtra(EXTRA_MEETING_ID))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        recorder?.stop()
        captureThread?.join(1_000)
        recorder?.release()
        recorder = null
        super.onDestroy()
    }

    private fun startCapture(directory: String?, meetingId: String?) {
        currentMeetingId = meetingId
        val root = File(directory ?: filesDir.resolve("segments").path)
        segmentStore = SegmentFileStore(root)
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        check(minimum > 0) { "AudioRecord não suportado neste aparelho" }
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, minimum * 2)
        running = true
        recorder?.startRecording()
        captureThread = thread(name = "atalocal-audio") { capture(minimum) }
    }

    private fun capture(bufferSize: Int) {
        val store = requireNotNull(segmentStore)
        val buffer = ShortArray(bufferSize)
        var sequence = 0
        var samplesInSegment = 0
        var writer = WavSegmentWriter(store.temporary(sequence)).also { it.open() }
        var lastLevelReport = 0L
        try {
            while (running) {
                if (paused) {
                    Thread.sleep(100)
                    continue
                }
                val count = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (count <= 0) continue
                val now = SystemClock.elapsedRealtime()
                if (now - lastLevelReport >= 150) {
                    var peak = 0
                    for (index in 0 until count) peak = maxOf(peak, kotlin.math.abs(buffer[index].toInt()))
                    sendBroadcast(Intent(ACTION_LEVEL).setPackage(packageName).putExtra(EXTRA_LEVEL, peak / 32767f))
                    lastLevelReport = now
                }
                var offset = 0
                while (offset < count && running) {
                    val available = minOf(count - offset, SAMPLES_PER_SEGMENT - samplesInSegment)
                    writer.write(buffer.copyOfRange(offset, offset + available), available)
                    offset += available
                    samplesInSegment += available
                    if (samplesInSegment == SAMPLES_PER_SEGMENT) {
                        writer.close()
                        store.commit(sequence)
                        sequence += 1
                        samplesInSegment = 0
                        writer = WavSegmentWriter(store.temporary(sequence)).also { it.open() }
                    }
                }
            }
        } finally {
            writer.close()
            if (samplesInSegment > 0) store.commit(sequence)
            sendBroadcast(
                Intent(ACTION_STOPPED)
                    .setPackage(packageName)
                    .putExtra(EXTRA_MEETING_ID, currentMeetingId)
            )
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("AtaLocal está gravando")
        .setContentText("O áudio permanece neste aparelho")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .build()

    companion object {
        const val ACTION_STOP = "br.com.betinhos.atalocal.audio.STOP"
        const val ACTION_TOGGLE_PAUSE = "br.com.betinhos.atalocal.audio.TOGGLE_PAUSE"
        const val ACTION_LEVEL = "br.com.betinhos.atalocal.audio.LEVEL"
        const val ACTION_STOPPED = "br.com.betinhos.atalocal.audio.STOPPED"
        const val EXTRA_LEVEL = "microphone_level"
        const val EXTRA_DIRECTORY = "segment_directory"
        const val EXTRA_MEETING_ID = "meeting_id"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLES_PER_SEGMENT = SAMPLE_RATE * 60
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001
    }

    private var currentMeetingId: String? = null
}
