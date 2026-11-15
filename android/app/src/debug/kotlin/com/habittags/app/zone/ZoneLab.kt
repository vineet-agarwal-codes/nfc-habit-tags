package com.habittags.app.zone

import android.content.Context
import com.habittags.core.zone.Guess
import com.habittags.core.zone.ZoneClassifier
import com.habittags.core.zone.ZoneEvents
import com.habittags.core.zone.ZoneRecording
import com.habittags.core.zone.ZoneSample
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.util.UUID

/** The lab's actions, shared by the screen, the tracking service and notification answers. */
object ZoneLab {
    const val SCANS_PER_RECORDING = 4
    private const val RECORD_TIMEOUT_MS = 4 * 60_000L
    private const val THROTTLED_RETRY_MS = 15_000L

    sealed interface RecordProgress {
        data class Scanning(val done: Int) : RecordProgress
        data class Throttled(val done: Int) : RecordProgress
    }

    /** Records until [SCANS_PER_RECORDING] fresh scans (cached/throttled ones don't count); null on timeout. */
    suspend fun record(context: Context, spot: String, mode: String, onProgress: (RecordProgress) -> Unit): ZoneRecording? {
        val source = ZoneSensing.scanSource(context)
        val samples = mutableListOf<ZoneSample>()
        val deadline = System.currentTimeMillis() + RECORD_TIMEOUT_MS
        onProgress(RecordProgress.Scanning(0))
        while (samples.size < SCANS_PER_RECORDING && System.currentTimeMillis() < deadline) {
            val scan = source.scan()
            if (scan.fresh) {
                samples += ZoneSensing.sample(context, scan)
                onProgress(RecordProgress.Scanning(samples.size))
            } else {
                onProgress(RecordProgress.Throttled(samples.size))
                delay(THROTTLED_RETRY_MS)
            }
        }
        if (samples.size < SCANS_PER_RECORDING) return null
        val now = Clock.System.now()
        val rec = ZoneRecording(UUID.randomUUID().toString(), spot, mode, now.toEpochMilliseconds(), samples)
        ZoneLabStore.addRecording(context, rec)
        ZoneLabStore.enqueue(context, ZoneEvents.recording(rec, now, TimeZone.currentSystemDefault()))
        ZoneLabStore.upload(context)
        return rec
    }

    data class Observation(val id: String, val sample: ZoneSample, val guess: Guess?)

    /** Takes one sample (fresh scan if Android allows, else the cached list, flagged), guesses and queues it. */
    suspend fun observe(context: Context, source: String): Observation {
        val sample = ZoneSensing.sample(context, ZoneSensing.scanSource(context).scan())
        val guess = ZoneClassifier.guess(ZoneLabStore.recordings.value, sample)
        val id = UUID.randomUUID().toString()
        ZoneLabStore.enqueue(context, listOf(ZoneEvents.observed(id, source, sample, guess, Clock.System.now(), TimeZone.currentSystemDefault())))
        return Observation(id, sample, guess)
    }

    suspend fun answer(context: Context, sampleId: String, guessed: String?, actual: String, source: String) {
        ZoneLabStore.enqueue(context, listOf(ZoneEvents.feedback(sampleId, guessed, actual, source, Clock.System.now(), TimeZone.currentSystemDefault())))
        ZoneLabStore.upload(context)
    }
}
