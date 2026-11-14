package com.habittags.core.recipe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RecipeParserTest {
    private val specs = listOf(ParamSpec("autoOffMinutes", "duration", "Auto-off", 90, 15, 480))

    @ParameterizedTest
    @MethodSource("bindCases")
    fun bindInt(raw: String?, user: Map<String, Int>, expected: Int?) {
        val value: JsonElement? = raw?.let { Json.parseToJsonElement(it) }
        assertEquals(expected, bindInt(value, specs, user))
    }

    @Test
    fun bindIntUnknownParamThrows() {
        assertThrows<IllegalArgumentException> { bindInt(Json.parseToJsonElement("\"{{missing}}\""), specs, emptyMap()) }
    }

    @Test
    fun bindIntMalformedStringThrows() {
        assertThrows<IllegalArgumentException> { bindInt(Json.parseToJsonElement("\"ninety\""), specs, emptyMap()) }
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
        val recipe = parseCatalog(FIXTURE.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")).recipes.single()
        assertFalse(isUsable(recipe))
    }

    @Test
    fun unknownActionDecodesAsUnsupported() {
        val recipe = parseCatalog(FIXTURE.replace("{\"type\": \"dnd\"}", "{\"type\": \"flashlight\"}")).recipes.single()
        assertEquals(Unsupported("flashlight"), recipe.rules.single().actions[1])
        assertFalse(isUsable(recipe))
    }

    companion object {
        @JvmStatic
        fun bindCases() = listOf(
            Arguments.of("90", emptyMap<String, Int>(), 90),
            Arguments.of("\"{{autoOffMinutes}}\"", emptyMap<String, Int>(), 90),
            Arguments.of("\"{{autoOffMinutes}}\"", mapOf("autoOffMinutes" to 240), 240),
            Arguments.of("\"{{autoOffMinutes}}\"", mapOf("autoOffMinutes" to 9999), 480),
            Arguments.of("\"{{autoOffMinutes}}\"", mapOf("autoOffMinutes" to 1), 15),
            Arguments.of(null, emptyMap<String, Int>(), null),
        )

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
