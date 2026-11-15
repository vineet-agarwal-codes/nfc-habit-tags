package com.habittags.core.usage

import com.habittags.core.store.AppState
import com.habittags.core.store.TapLogEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageEventsTest {
    private val kolkata = TimeZone.of("Asia/Kolkata")
    private fun t(iso: String) = Instant.parse(iso)
    private fun tap(iso: String, recipe: String? = "desk", zone: String? = "Asia/Kolkata") =
        TapLogEntry(t(iso).toEpochMilliseconds(), "tag", "label", recipe, zone)

    /** (recipe, day, tz) → count for the tap_count events of a report. */
    private fun counts(events: List<UsageEvent>) = events.filter { it.type == "tap_count" }
        .associate { Triple(it.payload["recipe"]!!.jsonPrimitive.content, it.day, it.tz) to it.payload["count"]!!.jsonPrimitive.int }

    @Test
    fun tapCountsPerLocalDayInTheZoneOfEachTap() {
        data class Case(val name: String, val log: List<TapLogEntry>, val now: String, val tz: TimeZone, val lastReport: String?, val expected: Map<Triple<String, String?, String?>, Int>)
        val cases = listOf(
            Case("around local midnight", listOf(tap("2026-11-14T18:29:00Z"), tap("2026-11-14T18:31:00Z")), "2026-11-15T06:00:00Z", kolkata, null,
                mapOf(Triple("desk", "2026-11-14", "Asia/Kolkata") to 1, Triple("desk", "2026-11-15", "Asia/Kolkata") to 1)),
            Case("log entries without a recipe are not reported",
                listOf(tap("2026-11-15T04:00:00Z"), tap("2026-11-15T05:00:00Z"), tap("2026-11-15T06:00:00Z"), tap("2026-11-15T06:10:00Z", recipe = null)),
                "2026-11-15T07:00:00Z", kolkata, null, mapOf(Triple("desk", "2026-11-15", "Asia/Kolkata") to 3)),
            Case("only days since the last report, counted fully",
                listOf(tap("2026-11-14T05:00:00Z"), tap("2026-11-15T03:00:00Z"), tap("2026-11-15T08:00:00Z")),
                "2026-11-15T09:00:00Z", kolkata, "2026-11-15T04:30:00Z", mapOf(Triple("desk", "2026-11-15", "Asia/Kolkata") to 2)),
            Case("nothing older than 14 days",
                listOf(tap("2026-10-26T05:00:00Z"), tap("2026-11-15T05:00:00Z")),
                "2026-11-15T09:00:00Z", kolkata, null, mapOf(Triple("desk", "2026-11-15", "Asia/Kolkata") to 1)),
            Case("zone recorded at tap time wins over the report's zone",
                listOf(tap("2026-11-15T19:00:00Z")), "2026-11-16T09:00:00Z", TimeZone.of("Europe/London"), null,
                mapOf(Triple("desk", "2026-11-16", "Asia/Kolkata") to 1)),
            Case("pre-upgrade entries without a zone use the report's zone",
                listOf(tap("2026-11-15T19:00:00Z", zone = null)), "2026-11-16T09:00:00Z", TimeZone.of("America/New_York"), null,
                mapOf(Triple("desk", "2026-11-15", "America/New_York") to 1)),
            Case("same recipe and day in two zones is one summed event, in the zone of the latest tap",
                listOf(tap("2026-11-15T03:00:00Z"), tap("2026-11-15T04:00:00Z"), tap("2026-11-15T05:00:00Z"),
                    tap("2026-11-15T12:00:00Z", zone = "Europe/London"), tap("2026-11-15T13:00:00Z", zone = "Europe/London")),
                "2026-11-15T14:00:00Z", TimeZone.of("Europe/London"), null,
                mapOf(Triple("desk", "2026-11-15", "Europe/London") to 5)),
            Case("DST change day in London",
                listOf(tap("2026-10-25T00:30:00Z", zone = "Europe/London"), tap("2026-10-25T23:30:00Z", zone = "Europe/London")),
                "2026-10-26T12:00:00Z", TimeZone.of("Europe/London"), null,
                mapOf(Triple("desk", "2026-10-25", "Europe/London") to 2)),
        )
        for (c in cases) {
            val state = AppState(tapLog = c.log, lastReportMs = c.lastReport?.let { t(it).toEpochMilliseconds() } ?: 0)
            assertEquals(c.expected, counts(buildReport(state, t(c.now), c.tz)), c.name)
        }
    }

    @Test
    fun snapshotAndPendingEvents() {
        val pending = UsageEvent("x", 1, null, null, payload = buildJsonObject { })
        val state = AppState(installedRecipes = setOf("gym", "desk"), pendingEvents = listOf(pending))
        val report = buildReport(state, t("2026-11-15T19:00:00Z"), kolkata)
        assertEquals(pending, report.first())
        val snapshot = report.single { it.type == "installed_snapshot" }
        assertEquals(JsonArray(listOf(JsonPrimitive("desk"), JsonPrimitive("gym"))), snapshot.payload["recipes"])
        assertEquals("2026-11-16" to "Asia/Kolkata", snapshot.day to snapshot.tz)

        val sent = state.afterReportSent(42L, report)
        assertEquals(emptyList(), sent.pendingEvents)
        assertEquals(42L, sent.lastReportMs)

        val queuedLater = UsageEvent("y", 1, null, null, payload = buildJsonObject { })
        val withLater = state.copy(pendingEvents = state.pendingEvents + queuedLater)
        assertEquals(listOf(queuedLater), withLater.afterReportSent(42L, report).pendingEvents)
    }

    @Test
    fun reportDueEverySixHours() {
        val state = AppState(lastReportMs = 1_000L)
        assertFalse(reportDue(state, 1_000L + 6 * 3_600_000L - 60_000L))
        assertTrue(reportDue(state, 1_000L + 6 * 3_600_000L))
    }
}
