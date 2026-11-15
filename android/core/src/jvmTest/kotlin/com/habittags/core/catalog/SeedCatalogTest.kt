package com.habittags.core.catalog

import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.isUsable
import com.habittags.core.recipe.validate
import com.habittags.core.recipe.parseCatalog
import com.habittags.core.recipe.CatalogJson
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class SeedCatalogTest {
    private val catalog = parseCatalog(File("../app/src/main/assets/catalog.json").readText())

    @Test
    fun seedIsValid() {
        assertEquals(listOf("desk-deep-work", "bed-sleep", "gym-bag", "front-door"), catalog.recipes.map { it.id })
        assertTrue(catalog.recipes.all(::isUsable))
        for (recipe in catalog.recipes) assertEquals(null, validate(recipe), recipe.id)

        val groupIds = catalog.appGroups.map { it.id }.toSet()
        assertEquals(setOf("social", "video", "messaging", "games"), groupIds)
        val used = catalog.recipes.flatMap { r -> (r.rules.flatMap { it.actions } + r.endActions).filterIsInstance<BlockGroups>() }
        assertTrue(used.flatMap { it.groups }.all(groupIds::contains))

        for (recipe in catalog.recipes) {
            val keys = recipe.params.map { it.key }.toSet()
            val refs = listOfNotNull(recipe.autoOffMinutes, recipe.strictMinutes)
                .filterIsInstance<JsonPrimitive>().filter { it.isString }
                .map { it.content.removePrefix("{{").removeSuffix("}}") }
            assertTrue(keys.containsAll(refs), "${recipe.id} references $refs, has $keys")
        }
    }

    @Test
    fun ciFixtureRecipesAreValid() {
        val snap = decodeRemoteCatalog(
            File("../app/src/debug/assets/fixture_backend/recipes.json").readText(),
            File("../app/src/debug/assets/fixture_backend/app_groups.json").readText(), null, 0,
        )
        val all = (CatalogJson.parseToJsonElement(File("../app/src/debug/assets/fixture_backend/recipes.json").readText()) as kotlinx.serialization.json.JsonArray).size
        assertEquals(all, snap.catalog.recipes.size)
    }
}
