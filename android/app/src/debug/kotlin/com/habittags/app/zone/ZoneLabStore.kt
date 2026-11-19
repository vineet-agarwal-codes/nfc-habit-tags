package com.habittags.app.zone

import android.content.Context
import android.util.Log
import com.habittags.app.HabitTagsApp
import com.habittags.core.store.withDeviceId
import com.habittags.core.usage.UsageEvent
import com.habittags.core.zone.ZoneRecording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Lab recordings (used for live guesses) and the queue of lab events waiting to upload. */
object ZoneLabStore {
    val DEFAULT_SPOTS = listOf("Bed", "Desk", "Kitchen", "Washroom", "Sofa")
    /** report_events rejects calls with more than 500 events. */
    const val MAX_EVENTS_PER_CALL = 500

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val recordingsSerializer = ListSerializer(ZoneRecording.serializer())
    private val eventsSerializer = ListSerializer(UsageEvent.serializer())
    private val lock = Any()
    private val uploading = Mutex()
    private var loaded = false

    private val _recordings = MutableStateFlow<List<ZoneRecording>>(emptyList())
    val recordings: StateFlow<List<ZoneRecording>> = _recordings.asStateFlow()
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()
    private val _uploadFailed = MutableStateFlow(false)
    /** True after an upload attempt failed and until one succeeds; the screen only shows the queue then. */
    val uploadFailed: StateFlow<Boolean> = _uploadFailed.asStateFlow()

    private fun recordingsFile(c: Context) = File(c.filesDir, "zone_recordings.json")
    private fun outboxFile(c: Context) = File(c.filesDir, "zone_outbox.json")

    private fun <T> read(file: File, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    private fun <T> write(file: File, serializer: kotlinx.serialization.KSerializer<List<T>>, value: List<T>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        tmp.renameTo(file)
    }

    fun load(context: Context) = synchronized(lock) {
        if (loaded) return
        _recordings.value = read(recordingsFile(context), recordingsSerializer)
        _pending.value = read(outboxFile(context), eventsSerializer).size
        loaded = true
    }

    fun spots(): List<String> = (DEFAULT_SPOTS + _recordings.value.map { it.spot }).distinct()

    fun addRecording(context: Context, rec: ZoneRecording) = synchronized(lock) {
        load(context)
        val next = _recordings.value + rec
        write(recordingsFile(context), recordingsSerializer, next)
        _recordings.value = next
    }

    fun enqueue(context: Context, events: List<UsageEvent>) = synchronized(lock) {
        load(context)
        val next = read(outboxFile(context), eventsSerializer) + events
        write(outboxFile(context), eventsSerializer, next)
        _pending.value = next.size
    }

    /** Sends queued events in chunks of [MAX_EVENTS_PER_CALL]; a failed chunk and everything after it stay queued. */
    suspend fun upload(context: Context): Boolean = uploading.withLock {
        val app = HabitTagsApp.from(context)
        val deviceId = app.update { it.withDeviceId { UUID.randomUUID().toString() } }.deviceId!!
        val queued = synchronized(lock) { load(context); read(outboxFile(context), eventsSerializer) }
        for (chunk in queued.chunked(MAX_EVENTS_PER_CALL)) {
            try {
                app.backend.report(deviceId, chunk)
            } catch (e: Exception) {
                Log.w(TAG, "zone upload failed; ${_pending.value} events stay queued", e)
                _uploadFailed.value = true
                return@withLock false
            }
            synchronized(lock) {
                // Only drop what was sent: events queued meanwhile stay.
                val remaining = read(outboxFile(context), eventsSerializer).toMutableList()
                chunk.forEach { remaining.remove(it) }
                write(outboxFile(context), eventsSerializer, remaining)
                _pending.value = remaining.size
            }
        }
        _uploadFailed.value = false
        true
    }

    private const val TAG = "ZoneLab"
}
