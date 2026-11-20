package com.habittags.core.app

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppEngineTest {
    private val seed = File("../app/src/main/assets/catalog.json").readText()
    private val tz = "Asia/Kolkata"

    private fun engine(state: String? = null) = AppEngine(state, seed, null, "00000000-0000-0000-0000-000000000001")
    private fun at(time: String): Long =
        LocalDateTime.parse("2026-11-15T$time").toInstant(TimeZone.of(tz)).toEpochMilliseconds()

    @Test
    fun installSetParamAndStartFromApp() {
        val e = engine()
        assertTrue(e.discover(at("10:00"), tz).any { it.id == "desk-deep-work" })
        e.install("desk-deep-work")
        assertFalse(e.discover(at("10:00"), tz).any { it.id == "desk-deep-work" })
        e.setParam("desk-deep-work", "autoOffMinutes", 120)
        assertEquals("2 h", e.params("desk-deep-work").first { it.key == "autoOffMinutes" }.valueText)

        val out = e.startManually("desk-deep-work", false, at("10:00"), tz)
        assertEquals("started", out.kind)
        assertEquals("Desk: Deep Work on · off at 12:00", out.message)
        assertEquals("ends 12:00", e.activeModes(at("10:05"), tz).single().endsText)
        assertEquals("alreadyOn", e.startManually("desk-deep-work", false, at("10:06"), tz).kind)
        assertFalse(e.uninstall("desk-deep-work"))

        e.endViaGate(out.modeKey)
        assertTrue(e.activeModes(at("10:07"), tz).isEmpty())
        assertTrue(e.uninstall("desk-deep-work"))
        assertFalse(e.isInstalled("desk-deep-work"))
    }

    @Test
    fun tagTapsToggleAndUnknownTags() {
        val e = engine()
        val id = e.createTagId()
        assertEquals("unknownTag", e.tap(id, at("10:00"), tz).kind)
        e.linkTag(id, "desk-deep-work")
        assertTrue(e.isInstalled("desk-deep-work"))
        assertEquals(listOf("Desk: Deep Work"), e.tags("desk-deep-work").map { it.label })
        assertEquals("started", e.tap(id, at("10:00"), tz).kind)
        // Desk's tag can't end it
        val again = e.tap(id, at("10:05"), tz)
        assertEquals("tagCantEnd", again.kind)
        assertEquals("Desk: Deep Work is on until 11:30. To stop early, use End early in the app.", again.message)
        assertEquals(listOf("tag", "tag"), e.state.tapLog.map { it.source })
        assertEquals(id, e.tagIdFromUrl(e.tagUrl(id)))
    }

    @Test
    fun bedOutsideHoursAndStartAnyway() {
        val e = engine()
        e.install("bed-sleep")
        assertEquals("Turns on from 20:00 · ends by 05:00 (up to 08:00 if you sleep late)", e.myRecipes(tz).single().hours)
        val out = e.startManually("bed-sleep", false, at("15:10"), tz)
        assertEquals("outsideHours", out.kind)
        assertEquals("between 20:00 and 05:00", out.windowsText)
        assertEquals("Bed: Sleep Mode only turns on between 20:00 and 05:00. It's 15:10 now, so nothing was started.", out.message)
        assertEquals("started", e.startManually("bed-sleep", true, at("15:10"), tz).kind)

        e.setParam("bed-sleep", "usualBedtime", 22 * 60 + 30)
        assertEquals("22:30", e.params("bed-sleep").first { it.key == "usualBedtime" }.valueText)
    }

    @Test
    fun autoOffRunsEndActionsOnce() {
        val e = engine()
        e.install("bed-sleep")
        e.startManually("bed-sleep", false, at("22:00"), tz)
        val off = e.activeModes(at("22:01"), tz).single().autoOffAtMs
        assertTrue(e.expire(off - 1).isEmpty())
        val effects = e.expire(off)
        assertEquals(listOf("checklist"), effects.map { it.kind })
        assertEquals("Good morning", effects.single().title)
        assertTrue(e.expire(off + 1).isEmpty())
    }

    @Test
    fun stateRoundTripAndCorruptState() {
        val e = engine()
        e.install("gym-bag")
        val again = engine(e.stateJson())
        assertTrue(again.isInstalled("gym-bag"))
        assertEquals(e.deviceId(), again.deviceId())
        assertFalse(AppEngine.isReadableState("garbage"))
        assertTrue(AppEngine.isReadableState(e.stateJson()))
        assertEquals("00000000-0000-0000-0000-000000000001", engine("garbage").deviceId())
    }

    @Test
    fun reportBodyAndSent() {
        val e = engine()
        e.install("desk-deep-work")
        e.startManually("desk-deep-work", false, at("10:00"), tz)
        assertTrue(e.reportDue(at("10:01")))
        val body = Json.parseToJsonElement(e.buildReportBody(at("10:01"), tz)).jsonObject
        assertEquals(e.deviceId(), body.getValue("device_id").jsonPrimitive.content)
        val types = body.getValue("events").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content }
        assertEquals(listOf("installed_snapshot", "tap_count"), types)
        e.reportSent(at("10:02"))
        assertFalse(e.reportDue(at("10:03")))
    }

    @Test
    fun badRemoteResponsesLeaveCatalogAlone() {
        val e = engine()
        assertFalse(e.applyRemote("{}", "[]", null, at("10:00")))
        assertTrue(e.discover(at("10:00"), tz).isNotEmpty())
    }

    @Test
    fun gymBagRunsEffects() {
        val e = engine()
        e.install("gym-bag")
        val out = e.startManually("gym-bag", false, at("18:00"), tz)
        assertEquals("ran", out.kind)
        assertEquals(setOf("timer", "checklist"), out.effects.map { it.kind }.toSet())
        assertEquals(60, out.effects.first { it.kind == "timer" }.minutes)
    }
}
