package com.habittags.core.catalog

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun recipeDoc(id: String, title: String = id, schemaVersion: Int = 1) =
    """{"id":"$id","schemaVersion":$schemaVersion,"version":1,"title":"$title","description":"d","placementHint":"p","mode":"oneShot","rules":[{"actions":[{"type":"log","label":"x"}]}]}"""

internal fun recipesJson(vararg rows: Triple<String, String, String?>) =
    rows.joinToString(",", "[", "]") { (id, doc, publishedAt) ->
        """{"id":"$id","doc":$doc,"published_at":${publishedAt?.let { "\"$it\"" } ?: "null"},"sort_hint":10}"""
    }

internal const val GROUPS = """[{"id":"social","doc":{"id":"social","title":"Social media","androidPackages":["a"]}}]"""

class RemoteCatalogTest {
    private val four = listOf("desk", "bed", "gym", "door").map { Triple(it, recipeDoc(it), "2026-11-01T00:00:00+00:00") }

    @Test
    fun decodeDropsBrokenDocsAndKeepsNewerSchemas() {
        val snap = decodeRemoteCatalog(
            recipesJson(*four.toTypedArray(), Triple("bad", """{"id":"bad"}""", null), Triple("future", recipeDoc("future", schemaVersion = 3), null)),
            """[{"id":"social","doc":{"id":"social","title":"Social media","androidPackages":["a"]}},{"id":"broken","doc":{"id":"broken"}}]""",
            """[{"recipe_id":"gym","weekly_active":40,"installs":100}]""",
            fetchedAtMs = 7,
        )
        assertEquals(listOf("desk", "bed", "gym", "door", "future"), snap.catalog.recipes.map { it.id })
        assertEquals(listOf("social"), snap.catalog.appGroups.map { it.id })
        assertEquals(RecipeStats(40, 100), snap.stats["gym"])
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), snap.meta["desk"]?.publishedAt)
        assertEquals(10, snap.meta["desk"]?.sortHint)
        assertEquals(7, snap.fetchedAtMs)

        for (stats in listOf(null, "oops", "{}")) {
            assertEquals(emptyMap(), decodeRemoteCatalog(recipesJson(*four.toTypedArray()), GROUPS, stats, 0).stats, "$stats")
        }
    }

    @Test
    fun mergeKeepsInstalledRecipesTheAuthorRemoved() {
        val previous = decodeRemoteCatalog(recipesJson(*four.toTypedArray()), GROUPS, null, 0)
        val fresh = decodeRemoteCatalog(recipesJson(four[1], four[2]), GROUPS, null, 1)
        val merged = mergeCatalog(previous, fresh, installed = setOf("desk"))
        assertEquals(listOf("bed", "gym", "desk"), merged.catalog.recipes.map { it.id })
        assertTrue(merged.meta.getValue("desk").retired)
        assertFalse(merged.meta.getValue("bed").retired)
        assertNull(merged.meta["door"])
        assertEquals(fresh, mergeCatalog(null, fresh, setOf("desk")))
    }
}
