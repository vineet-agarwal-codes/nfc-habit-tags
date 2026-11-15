package com.habittags.core.store

import com.habittags.core.fixtures.Fixtures.MIN
import com.habittags.core.fixtures.Fixtures.at
import com.habittags.core.fixtures.Fixtures.bed
import com.habittags.core.fixtures.Fixtures.ms
import com.habittags.core.fixtures.Fixtures.recipes
import com.habittags.core.fixtures.Fixtures.tag
import com.habittags.core.fixtures.Fixtures.zone
import com.habittags.core.mode.InstalledTag
import com.habittags.core.mode.ModeEngine
import com.habittags.core.mode.TapResult
import com.habittags.core.usage.UsageEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs

class StateCodecTest {
    private val sample = AppState(
        tags = mapOf("b1" to tag("b1", bed, mapOf("autoOffMinutes" to 60))),
        groupOverrides = mapOf("social" to setOf("com.android.settings")),
        tapLog = listOf(TapLogEntry(1L, "b1", "Bed")),
    )

    @Test
    fun roundTrip() {
        assertEquals(sample, StateCodec.decode(StateCodec.encode(sample)))
    }

    @Test
    fun missingOrCorruptDataGivesEmptyState() {
        for (text in listOf(null, "", "garbage", "{\"tags\": 5}")) assertEquals(AppState(), StateCodec.decode(text), "$text")
        // decodeOrNull tells corrupt data apart from an empty state, so the caller can keep the file.
        for (text in listOf("", "garbage", "{\"tags\": 5}", "[1]")) assertNull(StateCodec.decodeOrNull(text), text)
        assertEquals(AppState(), StateCodec.decodeOrNull("{}"))
    }

    @Test
    fun expireAfterRestart() {
        val started = assertIs<TapResult.Started>(ModeEngine.onTap(emptyMap(), sample.tags.getValue("b1"), bed, at("10:00"), zone))
        val reloaded = StateCodec.decode(StateCodec.encode(sample.copy(active = started.state)))
        assertEquals(started.state, reloaded.active)
        assertEquals(emptyMap(), ModeEngine.expire(reloaded.active, ms("10:00") + 90 * MIN, recipes::get).first)
    }

    @Test
    fun v1StateMigratesInstalledRecipesFromTags() {
        val cases = listOf(
            // v1: no stateVersion → installed = recipes that have tags
            """{"tags":{"t1":{"tagId":"t1","recipeId":"desk","label":"a","params":{}},"t2":{"tagId":"t2","recipeId":"desk","label":"b","params":{}},"t3":{"tagId":"t3","recipeId":"gym","label":"c","params":{}}}}"""
                to setOf("desk", "gym"),
            // v2: kept as stored
            """{"stateVersion":2,"installedRecipes":["bed"],"tags":{"t1":{"tagId":"t1","recipeId":"desk","label":"a","params":{}}}}"""
                to setOf("bed"),
        )
        for ((json, expected) in cases) {
            val state = StateCodec.decode(json)
            assertEquals(expected, state.installedRecipes, json)
            assertEquals(2, state.stateVersion)
        }
    }

    @Test
    fun roundTripWithEventsAndRecipeIds() {
        val state = sample.copy(
            tapLog = listOf(TapLogEntry(5L, "b1", "Bed", recipeId = "bed-sleep", zone = "Asia/Kolkata")),
            pendingEvents = listOf(UsageEvent("x", 1, "2026-11-15", "Asia/Kolkata", payload = buildJsonObject { put("a", JsonPrimitive(1)) })),
            installedRecipes = setOf("bed-sleep"),
            deviceId = "d",
            lastReportMs = 9L,
        )
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }

    @Test
    fun withDeviceIdSetsOnlyOnce() {
        assertEquals("new", AppState().withDeviceId { "new" }.deviceId)
        assertEquals("old", AppState(deviceId = "old").withDeviceId { "new" }.deviceId)
    }

    @Test
    fun manualTagAndParams() {
        val t1 = InstalledTag("t1", "desk", "Desk 1", mapOf("autoOffMinutes" to 60))
        val t2 = InstalledTag("t2", "desk", "Desk 2", mapOf("autoOffMinutes" to 120))
        val state = AppState(tags = linkedMapOf("t1" to t1, "t2" to t2), recipeParams = mapOf("bed" to mapOf("autoOffMinutes" to 300)))
        // cases: recipe -> expected manual tag
        assertEquals(t1, state.manualTag("desk", "Desk"))
        assertEquals(InstalledTag("app:bed", "bed", "Bed", mapOf("autoOffMinutes" to 300)), state.manualTag("bed", "Bed"))
        assertEquals(InstalledTag("app:gym", "gym", "Gym", emptyMap()), state.manualTag("gym", "Gym"))
        assertEquals(mapOf("autoOffMinutes" to 60), state.paramsFor("t1", "desk"))
        assertEquals(mapOf("autoOffMinutes" to 300), state.paramsFor("app:bed", "bed"))
        // Tag order survives a save and reload.
        assertEquals(t1, StateCodec.decode(StateCodec.encode(state)).manualTag("desk", "Desk"))
    }

    @Test
    fun stateWithoutRecipeParamsOrSourceDecodes() {
        val text = """{"stateVersion":2,"tapLog":[{"atMs":1,"tagId":"t1","label":"Desk","recipeId":"desk"}]}"""
        val state = StateCodec.decode(text)
        assertEquals(emptyMap(), state.recipeParams)
        assertEquals("tag", state.tapLog.single().source)
    }
}
