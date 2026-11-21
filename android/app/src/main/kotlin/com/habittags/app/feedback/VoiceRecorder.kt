package com.habittags.app.feedback

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File

/** Records one AAC voice note (mono, 64 kbps) of at most [MAX_VOICE_MS]; about 1 MB for two minutes. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var stoppedAt = 0L

    val isRecording get() = recorder != null && stoppedAt == 0L

    fun elapsedMs(): Long = if (recorder == null) 0 else (if (stoppedAt > 0) stoppedAt else SystemClock.elapsedRealtime()) - startedAt

    fun start(out: File): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setMaxDuration(MAX_VOICE_MS.toInt())
        r.setOutputFile(out.absolutePath)
        r.setOnInfoListener { _, what, _ ->
            // At two minutes the recorder stops itself; the press release then just collects the file.
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stoppedAt = SystemClock.elapsedRealtime()
        }
        r.prepare()
        r.start()
        recorder = r
        file = out
        startedAt = SystemClock.elapsedRealtime()
        stoppedAt = 0
        true
    }.onFailure { Log.w("Feedback", "can't record", it); release() }.getOrDefault(false)

    /** Stops and returns the note, or null when it was too short (an accidental tap) or failed. */
    fun stop(remoteName: String): Attachment? {
        val r = recorder ?: return null
        val duration = elapsedMs().coerceAtMost(MAX_VOICE_MS)
        val ok = runCatching { r.stop() }.isSuccess || stoppedAt > 0
        release()
        val out = file ?: return null
        if (!ok || duration < 800 || !out.exists() || out.length() == 0L) {
            out.delete()
            return null
        }
        return Attachment(out.absolutePath, remoteName, "audio", out.length(), durationMs = duration)
    }

    fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }
}
