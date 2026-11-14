package com.habittags.core.catalog

import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.isUsable
import com.habittags.core.recipe.parseCatalog
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SeedCatalogTest {
    private val catalog = parseCatalog(File("../app/src/main/assets/catalog.json").readText())

    @Test
    fun seedIsValid() {
        assertEquals(listOf("desk-deep-work", "bed-sleep", "gym-bag", "front-door"), catalog.recipes.map { it.id })
        assertTrue(catalog.recipes.all(::isUsable))

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
}
