package com.habittags.core.catalog

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private val old = "2026-11-01T00:00:00Z"
    private val snapshot = decodeRemoteCatalog(
        recipesJson(
            Triple("gym", recipeDoc("gym"), old), Triple("bed", recipeDoc("bed"), old),
            Triple("desk", recipeDoc("desk"), old), Triple("door", recipeDoc("door"), old),
            Triple("new1", recipeDoc("new1"), "2026-11-10T00:00:00Z"), Triple("new2", recipeDoc("new2"), "2026-11-14T00:00:00Z"),
            Triple("new3", recipeDoc("new3"), "2026-11-12T00:00:00Z"), Triple("new4", recipeDoc("new4"), "2026-11-13T00:00:00Z"),
            Triple("oct31", recipeDoc("oct31"), "2026-10-31T11:00:00Z"),
            Triple("future", recipeDoc("future", schemaVersion = 3), "2026-11-14T00:00:00Z"),
        ),
        GROUPS,
        """[{"recipe_id":"gym","weekly_active":40,"installs":100},{"recipe_id":"bed","weekly_active":30,"installs":500},
            {"recipe_id":"desk","weekly_active":12,"installs":80},{"recipe_id":"door","weekly_active":3,"installs":3}]""",
        0,
    )

    @Test
    fun order() {
        data class Case(val installed: Set<String>, val expected: List<String>)
        val cases = listOf(
            // only 3 recent recipes are boosted (newest first); new1 and the 15-day-old oct31 rank as 0/0
            Case(emptySet(), listOf("new2", "new4", "new3", "gym", "bed", "desk", "door", "new1", "oct31")),
            Case(setOf("gym", "new2"), listOf("new4", "new3", "new1", "bed", "desk", "door", "oct31")),
            Case(snapshot.catalog.recipes.map { it.id }.toSet(), emptyList()),
        )
        for (c in cases) assertEquals(c.expected, discoverOrder(snapshot, c.installed, now).map { it.id }, "${c.installed}")
    }

    @Test
    fun retiredRecipesAreHidden() {
        val retired = snapshot.copy(meta = snapshot.meta + ("gym" to snapshot.meta.getValue("gym").copy(retired = true)))
        assertEquals(false, discoverOrder(retired, emptySet(), now).any { it.id == "gym" })
    }

    @Test
    fun labels() {
        val cases = listOf(
            RecipeStats(320, 1240) to listOf("Used by 320 people this week", "1,240 installs"),
            RecipeStats(4, 1240) to listOf("1,240 installs"),
            RecipeStats(320, 4) to listOf("Used by 320 people this week"),
            RecipeStats(3, 3) to listOf("New"),
            RecipeStats(5, 1_234_567) to listOf("Used by 5 people this week", "1,234,567 installs"),
            null to emptyList(),
        )
        for ((stats, expected) in cases) assertEquals(expected, statLabel(stats), "$stats")
    }
}
