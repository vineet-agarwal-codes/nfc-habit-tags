package com.habittags.core.zone

import com.habittags.core.usage.UsageEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** The report_events RPC drops payloads over 4096 bytes as Postgres prints them (jsonb adds a space after every colon and comma), so the compact size must stay well under it. */
const val MAX_PAYLOAD_BYTES = 3000

private val json = Json { encodeDefaults = true }

/** Drops the weakest access points until the sample fits next to [extra] payload fields. */
fun ZoneSample.trimForPayload(extraBytes: Int = 300): ZoneSample {
    var s = copy(wifi = wifi.sortedByDescending { it.r })
    while (s.wifi.isNotEmpty() && json.encodeToString(ZoneSample.serializer(), s).length + extraBytes > MAX_PAYLOAD_BYTES) {
        s = s.copy(wifi = s.wifi.dropLast(maxOf(1, s.wifi.size / 10)))
    }
    return s
}

private fun event(type: String, at: Instant, tz: TimeZone, payload: JsonObject) = UsageEvent(
    type = type, v = 1, day = at.toLocalDateTime(tz).date.toString(), tz = tz.id, at = at.toString(), payload = payload,
)

private fun JsonObject.withSample(sample: ZoneSample): JsonObject =
    JsonObject(this + json.encodeToJsonElement(sample.trimForPayload()).jsonObject)

object ZoneEvents {
    /** Metadata event plus one zone_sample per recorded sample, all tagged with the recording id. */
    fun recording(rec: ZoneRecording, at: Instant, tz: TimeZone): List<UsageEvent> {
        val meta = event("zone_recording", at, tz, buildJsonObject {
            put("rec", JsonPrimitive(rec.id))
            put("spot", JsonPrimitive(rec.spot))
            put("mode", JsonPrimitive(rec.mode))
            put("samples", JsonPrimitive(rec.samples.size))
        })
        val samples = rec.samples.mapIndexed { i, sample ->
            event("zone_sample", at, tz, buildJsonObject {
                put("source", JsonPrimitive("recording"))
                put("rec", JsonPrimitive(rec.id))
                put("spot", JsonPrimitive(rec.spot))
                put("mode", JsonPrimitive(rec.mode))
                put("i", JsonPrimitive(i))
            }.withSample(sample))
        }
        return listOf(meta) + samples
    }

    /** A sample taken by "Where am I?" ("live") or by tracking ("tracking"), with the app's guess. */
    fun observed(sampleId: String, source: String, sample: ZoneSample, guess: Guess?, at: Instant, tz: TimeZone): UsageEvent =
        event("zone_sample", at, tz, buildJsonObject {
            put("source", JsonPrimitive(source))
            put("id", JsonPrimitive(sampleId))
            guess?.let {
                put("guess", JsonPrimitive(it.spot))
                put("confidence", JsonPrimitive(it.confidence))
            }
        }.withSample(sample))

    fun feedback(sampleId: String, guessed: String?, actual: String, source: String, at: Instant, tz: TimeZone): UsageEvent =
        event("zone_guess_feedback", at, tz, buildJsonObject {
            put("id", JsonPrimitive(sampleId))
            guessed?.let { put("guessed", JsonPrimitive(it)) }
            put("actual", JsonPrimitive(actual))
            put("correct", JsonPrimitive(guessed == actual))
            put("source", JsonPrimitive(source))
        })
}
