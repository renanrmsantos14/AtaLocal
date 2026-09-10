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
        if (!running) startCapture(intent?.getStringExtra(EXTRA_DIRECTORY))
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

    private fun startCapture(directory: String?) {
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
        try {
            while (running) {
                if (paused) {
                    Thread.sleep(100)
                    continue
                }
                val count = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (count <= 0) continue
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
        const val EXTRA_DIRECTORY = "segment_directory"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLES_PER_SEGMENT = SAMPLE_RATE * 60
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001
    }
}
