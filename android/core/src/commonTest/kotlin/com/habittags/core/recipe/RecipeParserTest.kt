package com.habittags.core.recipe

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeParserTest {
    private val specs = listOf(ParamSpec("autoOffMinutes", "duration", "Auto-off", 90, 15, 480))

    @Test
    fun bindInt() {
        val cases = listOf(
            Triple("90", emptyMap<String, Int>(), 90),
            Triple("\"{{autoOffMinutes}}\"", emptyMap(), 90),
            Triple("\"{{autoOffMinutes}}\"", mapOf("autoOffMinutes" to 240), 240),
            Triple("\"{{autoOffMinutes}}\"", mapOf("autoOffMinutes" to 9999), 480),
            Triple("\"{{autoOffMinutes}}\"", mapOf("autoOffMinutes" to 1), 15),
        )
        for ((raw, user, expected) in cases) {
            assertEquals(expected, bindInt(Json.parseToJsonElement(raw), specs, user), "case $raw $user")
        }
        assertEquals(null, bindInt(null, specs, emptyMap()))
    }

    @Test
    fun bindIntRejectsUnknownOrMalformed() {
        for (raw in listOf("\"{{missing}}\"", "\"ninety\"")) {
            assertFailsWith<IllegalArgumentException>(raw) { bindInt(Json.parseToJsonElement(raw), specs, emptyMap()) }
        }
    }

    @Test
    fun parsesEveryActionType() {
        val catalog = parseCatalog(FIXTURE)
        val recipe = catalog.recipes.single()
        assertEquals("Desk: Deep Work", recipe.title)
        assertEquals(Mode.toggle, recipe.mode)
        assertEquals(
            listOf(BlockGroups::class, Dnd::class, Timer::class, Checklist::class, OpenApp::class, Log::class),
            recipe.rules.single().actions.map { it::class },
        )
        assertEquals(listOf("social"), (recipe.rules.single().actions[0] as BlockGroups).groups)
        assertEquals("social", catalog.appGroups.single().id)
        assertTrue(isUsable(recipe))
    }

    @Test
    fun newerSchemaIsUnusable() {
        val recipe = parseCatalog(FIXTURE.replace("\"schemaVersion\": 1", "\"schemaVersion\": 3")).recipes.single()
        assertFalse(isUsable(recipe))
    }

    @Test
    fun unknownActionDecodesAsUnsupported() {
        val recipe = parseCatalog(FIXTURE.replace("{\"type\": \"dnd\"}", "{\"type\": \"flashlight\"}")).recipes.single()
        assertEquals(Unsupported("flashlight"), recipe.rules.single().actions[1])
        assertFalse(isUsable(recipe))
    }

    companion object {
        val FIXTURE = """
            {
              "appGroups": [{"id": "social", "title": "Social", "androidPackages": ["com.instagram.android"]}],
              "recipes": [{
                "id": "desk-deep-work",
                "schemaVersion": 1,
                "version": 1,
                "title": "Desk: Deep Work",
                "description": "Focus at your desk",
                "placementHint": "Under your desk edge",
                "mode": "toggle",
                "params": [{"key": "autoOffMinutes", "type": "duration", "label": "Auto-off", "default": 90, "min": 15, "max": 480}],
                "autoOffMinutes": "{{autoOffMinutes}}",
                "futureField": true,
                "rules": [{"actions": [
                  {"type": "blockGroups", "groups": ["social"]},
                  {"type": "dnd"},
                  {"type": "timer", "minutes": 25},
                  {"type": "checklist", "title": "Start", "items": ["Water"]},
                  {"type": "openApp", "androidPackage": "com.example"},
                  {"type": "log", "label": "Desk"}
                ]}]
              }]
            }
        """.trimIndent()
    }
}
