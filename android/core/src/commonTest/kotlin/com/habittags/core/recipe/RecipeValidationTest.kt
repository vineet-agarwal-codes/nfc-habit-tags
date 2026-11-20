package com.habittags.core.recipe

import com.habittags.core.catalog.decodeRemoteCatalog
import com.habittags.core.catalog.recipesJson
import com.habittags.core.catalog.withoutInvalidRecipes
import com.habittags.core.catalog.GROUPS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TIME_PARAMS =
    """[{"key":"from","type":"time","label":"From","default":1260,"min":0,"max":1439},{"key":"to","type":"time","label":"Until","default":300,"min":0,"max":1439}]"""
private const val DND = """[{"type":"dnd"}]"""

class RecipeValidationTest {
    private fun doc(
        params: String = """[{"key":"autoOff","type":"duration","label":"Auto-off","default":90,"min":15,"max":480}]""",
        autoOff: String = "\"{{autoOff}}\"",
        whenTime: String = "22:00-06:00",
        actions: String = """[{"type":"dnd"},{"type":"timer","minutes":"{{autoOff}}"}]""",
        endActions: String = "[]",
        schemaVersion: Int = 1,
        endPolicy: String = "null",
    ) = """{"id":"r","schemaVersion":$schemaVersion,"version":1,"title":"R","description":"d","placementHint":"p","mode":"toggle",
        "params":$params,"autoOffMinutes":$autoOff,"endPolicy":$endPolicy,"rules":[{"when":{"time":"$whenTime"},"actions":$actions}],"endActions":$endActions}"""

    private fun recipe(json: String) = CatalogJson.decodeFromString(Recipe.serializer(), json)

    @Test
    fun validateFlagsRecipesThatWouldThrowAtTapTime() {
        val cases = mapOf(
            "valid" to (doc() to true),
            "min above max" to (doc(params = """[{"key":"autoOff","type":"duration","label":"A","default":90,"min":500,"max":480}]""") to false),
            "default outside range" to (doc(params = """[{"key":"autoOff","type":"duration","label":"A","default":5,"min":15,"max":480}]""") to false),
            "unknown placeholder" to (doc(autoOff = "\"{{missing}}\"") to false),
            "non-placeholder string" to (doc(autoOff = "\"ninety\"") to false),
            "unknown placeholder in a timer" to (doc(actions = """[{"type":"timer","minutes":"{{nope}}"}]""") to false),
            "unknown placeholder in end actions" to (doc(endActions = """[{"type":"timer","minutes":"{{nope}}"}]""") to false),
            "bad time window" to (doc(whenTime = "25:00-06:00") to false),
            "unsupported action stays valid (shown as unusable)" to (doc(actions = """[{"type":"flashlight"}]""") to true),
            "newer schema is not checked" to (doc(whenTime = "later", schemaVersion = 99) to true),
            "offset window" to (doc(params = TIME_PARAMS, autoOff = "90", whenTime = "{{from-180}}-{{to}}", actions = DND, schemaVersion = 3) to true),
            "unknown key in an offset window" to (doc(params = TIME_PARAMS, autoOff = "90", whenTime = "{{nope-180}}-{{to}}", actions = DND, schemaVersion = 3) to false),
            "wake end policy" to (doc(params = TIME_PARAMS, autoOff = "null", whenTime = "{{from}}-{{to}}", actions = DND, schemaVersion = 3,
                endPolicy = """{"type":"wake","wakeAt":"{{to}}","usual":"{{from}}","latest":"{{to}}"}""") to true),
            "end policy with an unknown param" to (doc(params = TIME_PARAMS, autoOff = "null", whenTime = "{{from}}-{{to}}", actions = DND, schemaVersion = 3,
                endPolicy = """{"type":"wake","wakeAt":"{{nope}}","usual":"{{from}}","latest":"{{to}}"}""") to false),
            "unknown end policy" to (doc(params = TIME_PARAMS, autoOff = "null", whenTime = "{{from}}-{{to}}", actions = DND, schemaVersion = 3,
                endPolicy = """{"type":"moon","wakeAt":"{{to}}","usual":"{{from}}","latest":"{{to}}"}""") to false),
            "time params in a window" to (doc(params = TIME_PARAMS, autoOff = "90", whenTime = "{{from}}-{{to}}", actions = DND, schemaVersion = 2) to true),
            "unknown time placeholder in a window" to (doc(params = TIME_PARAMS, autoOff = "90", whenTime = "{{from}}-{{nope}}", actions = DND, schemaVersion = 2) to false),
            "time param past 23:59" to (doc(params = TIME_PARAMS.replace("\"max\":1439", "\"max\":1440"), autoOff = "90", whenTime = "{{from}}-{{to}}", actions = DND, schemaVersion = 2) to false),
            "window placeholder pointing at a duration" to (doc(whenTime = "{{autoOff}}-05:00", actions = DND, schemaVersion = 2) to false),
            "negative time param" to (doc(params = TIME_PARAMS.replace("\"min\":0", "\"min\":-1"), autoOff = "90", whenTime = "{{from}}-{{to}}", actions = DND, schemaVersion = 2) to false),
        )
        for ((name, case) in cases) {
            val (json, valid) = case
            val problem = validate(recipe(json))
            assertEquals(valid, problem == null, "$name: $problem")
        }
    }

    @Test
    fun remoteCatalogDropsInvalidRecipes() {
        val snap = decodeRemoteCatalog(
            recipesJson(Triple("ok", doc().replace("\"id\":\"r\"", "\"id\":\"ok\""), null), Triple("bad", doc(whenTime = "nope").replace("\"id\":\"r\"", "\"id\":\"bad\""), null)),
            GROUPS, null, 0,
        )
        assertEquals(listOf("ok"), snap.catalog.recipes.map { it.id })
        assertNull(snap.meta["bad"])
        assertTrue(snap.withoutInvalidRecipes() == snap)
    }
}
