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
import kotlinx.coroutines.runBlocking
import br.com.betinhos.atalocal.data.AudioSegmentEntity
import br.com.betinhos.atalocal.data.DatabaseProvider

class RecordingService : Service() {
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private var running = false
    @Volatile private var paused = false
    private var segmentStore: SegmentFileStore? = null
    private var sessionMeetingId: String? = null
    private val sessionStore by lazy { RecordingSessionStore(filesDir.resolve("meetings")) }

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
        try {
            startForeground(NOTIFICATION_ID, notification())
            if (!running) startCapture(intent?.getStringExtra(EXTRA_DIRECTORY), intent?.getStringExtra(EXTRA_MEETING_ID))
        } catch (error: Throwable) {
            intent?.getStringExtra(EXTRA_MEETING_ID)?.let(sessionStore::clear)
            sendBroadcast(
                Intent(ACTION_ERROR)
                    .setPackage(packageName)
                    .putExtra(EXTRA_MEETING_ID, intent?.getStringExtra(EXTRA_MEETING_ID))
                    .putExtra(EXTRA_ERROR, error.message ?: "Não foi possível iniciar a gravação")
            )
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        recorder?.let { activeRecorder ->
            if (shouldStopRecorder(activeRecorder.recordingState)) runCatching { activeRecorder.stop() }
        }
        captureThread?.join(1_000)
        recorder?.release()
        recorder = null
        super.onDestroy()
    }

    private fun startCapture(directory: String?, meetingId: String?) {
        currentMeetingId = meetingId
        sessionMeetingId = meetingId
        meetingId?.let { sessionStore.start(it, System.currentTimeMillis()) }
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
        var sequence = (store.recover().mapNotNull { Regex("segment-(\\d+)\\.wav").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull()?.plus(1) ?: 0)
        var samplesInSegment = 0
        val tail = AudioTail(OVERLAP_SAMPLES)
        var writer = WavSegmentWriter(store.temporary(sequence)).also { it.open() }
        var lastLevelReport = 0L
        var lastHeartbeat = 0L
        var captureError: String? = null
        try {
            while (running) {
                if (paused) {
                    Thread.sleep(100)
                    continue
                }
                val count = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (count < 0) {
                    if (running) throw IllegalStateException(audioReadFailureMessage(count))
                    break
                }
                if (count == 0) continue
                val now = SystemClock.elapsedRealtime()
                if (now - lastHeartbeat >= 5_000L) {
                    sessionMeetingId?.let(sessionStore::touch)
                    lastHeartbeat = now
                }
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
                    tail.append(buffer, offset, available)
                    offset += available
                    samplesInSegment += available
                    if (samplesInSegment == SAMPLES_PER_SEGMENT) {
                        writer.close()
                        val completed = store.commit(sequence)
                        persistSegment(completed, sequence, SAMPLES_PER_SEGMENT)
                        sequence += 1
                        writer = WavSegmentWriter(store.temporary(sequence)).also { it.open() }
                        val overlap = tail.snapshot()
                        writer.write(overlap, overlap.size)
                        samplesInSegment = overlap.size
                    }
                }
            }
        } catch (error: Throwable) {
            running = false
            captureError = error.message ?: "A captura do microfone foi interrompida"
        } finally {
            writer.close()
            if (samplesInSegment > 0) {
                val completed = store.commit(sequence)
                persistSegment(completed, sequence, samplesInSegment)
            }
            sessionMeetingId?.let(sessionStore::clear)
            if (captureError == null) {
                sendBroadcast(
                    Intent(ACTION_STOPPED)
                        .setPackage(packageName)
                        .putExtra(EXTRA_MEETING_ID, currentMeetingId)
                )
            } else {
                sendBroadcast(
                    Intent(ACTION_ERROR)
                        .setPackage(packageName)
                        .putExtra(EXTRA_MEETING_ID, currentMeetingId)
                        .putExtra(EXTRA_ERROR, captureError)
                )
                stopSelf()
            }
        }
    }

    private fun persistSegment(file: File, sequence: Int, sampleCount: Int) {
        val meetingId = currentMeetingId ?: return
        runBlocking {
            DatabaseProvider.get(applicationContext).audioSegmentDao().upsert(
                AudioSegmentEntity(meetingId, sequence, file.absolutePath, sampleCount * 1_000L / SAMPLE_RATE)
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
        const val ACTION_ERROR = "br.com.betinhos.atalocal.audio.ERROR"
        const val EXTRA_LEVEL = "microphone_level"
        const val EXTRA_DIRECTORY = "segment_directory"
        const val EXTRA_MEETING_ID = "meeting_id"
        const val EXTRA_ERROR = "error"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLES_PER_SEGMENT = SAMPLE_RATE * 60
        const val OVERLAP_SAMPLES = SAMPLE_RATE
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001
    }

    private var currentMeetingId: String? = null
}

internal fun audioReadFailureMessage(code: Int): String =
    "A captura do microfone foi interrompida (código $code). Verifique a permissão e tente novamente."

internal fun shouldStopRecorder(recordingState: Int): Boolean =
    recordingState == AudioRecord.RECORDSTATE_RECORDING
