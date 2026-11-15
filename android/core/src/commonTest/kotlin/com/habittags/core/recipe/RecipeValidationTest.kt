package com.habittags.core.recipe

import com.habittags.core.catalog.decodeRemoteCatalog
import com.habittags.core.catalog.recipesJson
import com.habittags.core.catalog.withoutInvalidRecipes
import com.habittags.core.catalog.GROUPS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeValidationTest {
    private fun doc(
        params: String = """[{"key":"autoOff","type":"duration","label":"Auto-off","default":90,"min":15,"max":480}]""",
        autoOff: String = "\"{{autoOff}}\"",
        whenTime: String = "22:00-06:00",
        actions: String = """[{"type":"dnd"},{"type":"timer","minutes":"{{autoOff}}"}]""",
        endActions: String = "[]",
        schemaVersion: Int = 1,
    ) = """{"id":"r","schemaVersion":$schemaVersion,"version":1,"title":"R","description":"d","placementHint":"p","mode":"toggle",
        "params":$params,"autoOffMinutes":$autoOff,"rules":[{"when":{"time":"$whenTime"},"actions":$actions}],"endActions":$endActions}"""

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
