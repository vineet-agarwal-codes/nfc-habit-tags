package com.habittags.core.zone

import com.habittags.core.usage.UsageEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZoneEventsTest {
    private val at = Instant.parse("2026-11-15T19:00:00Z")
    private val tz = TimeZone.of("Asia/Kolkata")

    private fun bigSample(aps: Int) = ZoneSample(
        wifi = (0 until aps).map { WifiReading("aa:bb:cc:dd:%02x:%02x".format0(it), -40 - it % 60, 5180) },
        magMean = 42f, magStd = 1.5f, gravity = listOf(0.1f, 0.2f, 9.8f), light = 120f,
    )

    private fun String.format0(i: Int) = replaceFirst("%02x", (i / 256).toString(16).padStart(2, '0')).replaceFirst("%02x", (i % 256).toString(16).padStart(2, '0'))

    private fun UsageEvent.size() = payload.toString().length

    @Test
    fun recordingProducesMetaPlusSamplesWithLocalDayZoneAndUtcInstant() {
        val rec = ZoneRecording("rec1", "Desk", MODE_POCKET, at.toEpochMilliseconds(), List(4) { bigSample(10) })
        val events = ZoneEvents.recording(rec, at, tz)
        assertEquals(listOf("zone_recording") + List(4) { "zone_sample" }, events.map { it.type })
        for (e in events) {
            assertEquals("2026-11-16", e.day) // 00:30 next day in Kolkata
            assertEquals("Asia/Kolkata", e.tz)
            assertEquals("2026-11-15T19:00:00Z", e.at)
            assertEquals(1, e.v)
        }
        assertEquals("rec1", events[3].payload["rec"]!!.jsonPrimitive.content)
        assertEquals("2", events[3].payload["i"]!!.jsonPrimitive.content)
        assertEquals("pocket", events[1].payload["mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun largeScansAreTrimmedToStrongestUnderThePayloadLimit() {
        for (aps in listOf(5, 40, 150, 400)) {
            val e = ZoneEvents.observed("s1", "tracking", bigSample(aps), Guess("Desk", 0.8, mapOf("Desk" to 0.8)), at, tz)
            assertTrue(e.size() <= 4096, "$aps APs → ${e.size()} bytes")
        }
        val trimmed = bigSample(400).trimForPayload()
        assertTrue(trimmed.wifi.isNotEmpty())
        assertEquals(trimmed.wifi.sortedByDescending { it.r }, trimmed.wifi)
        assertEquals(bigSample(5).wifi.size, bigSample(5).trimForPayload().wifi.size)
    }

    @Test
    fun feedbackMarksCorrectness() {
        val cases = listOf(Triple("Desk", "Desk", true), Triple("Desk", "Bed", false), Triple(null, "Bed", false))
        for ((guessed, actual, correct) in cases) {
            val e = ZoneEvents.feedback("s1", guessed, actual, "notification", at, tz)
            assertEquals(correct.toString(), e.payload["correct"]!!.jsonPrimitive.content)
        }
    }
}
