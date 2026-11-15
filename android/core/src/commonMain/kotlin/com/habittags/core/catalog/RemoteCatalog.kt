package com.habittags.core.catalog

import com.habittags.core.recipe.AppGroup
import com.habittags.core.recipe.Catalog
import com.habittags.core.recipe.CatalogJson
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.validate
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RecipeMeta(val publishedAt: Instant? = null, val sortHint: Int = 100, val retired: Boolean = false)

@Serializable
data class RecipeStats(val weeklyActive: Int, val installs: Int)

@Serializable
data class CatalogSnapshot(
    val catalog: Catalog,
    val meta: Map<String, RecipeMeta> = emptyMap(),
    val stats: Map<String, RecipeStats> = emptyMap(),
    val fetchedAtMs: Long = 0,
)

private fun rows(json: String): List<JsonObject> =
    (CatalogJson.parseToJsonElement(json) as JsonArray).filterIsInstance<JsonObject>()

/**
 * Decodes the three PostgREST responses. Each document is decoded on its own, so one bad recipe or
 * group doesn't take the catalog down; recipes needing a newer schema are kept for "Update the app".
 */
fun decodeRemoteCatalog(recipesJson: String, groupsJson: String, statsJson: String?, fetchedAtMs: Long): CatalogSnapshot {
    val recipes = mutableListOf<Recipe>()
    val meta = mutableMapOf<String, RecipeMeta>()
    for (row in rows(recipesJson)) {
        val recipe = runCatching { CatalogJson.decodeFromJsonElement(Recipe.serializer(), row.getValue("doc")) }.getOrNull() ?: continue
        recipes += recipe
        meta[recipe.id] = RecipeMeta(
            publishedAt = row["published_at"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() },
            sortHint = row["sort_hint"]?.jsonPrimitive?.intOrNull ?: 100,
        )
    }
    val groups = rows(groupsJson).mapNotNull { row ->
        runCatching { CatalogJson.decodeFromJsonElement(AppGroup.serializer(), row.getValue("doc")) }.getOrNull()
    }
    val stats = statsJson?.let { json ->
        runCatching {
            rows(json).associate { row ->
                row.getValue("recipe_id").jsonPrimitive.content to
                    RecipeStats(row.getValue("weekly_active").jsonPrimitive.intOrNull ?: 0, row.getValue("installs").jsonPrimitive.intOrNull ?: 0)
            }
        }.getOrNull()
    } ?: emptyMap()
    return CatalogSnapshot(Catalog(recipes, groups), meta, stats, fetchedAtMs).withoutInvalidRecipes()
}

/** Drops recipes that would throw at tap or edit time; also applied to the on-disk cache. */
fun CatalogSnapshot.withoutInvalidRecipes(): CatalogSnapshot {
    val (valid, invalid) = catalog.recipes.partition { validate(it) == null }
    if (invalid.isEmpty()) return this
    val dropped = invalid.map { it.id }.toSet()
    return copy(catalog = catalog.copy(recipes = valid), meta = meta - dropped)
}

/** The fresh catalog, plus installed recipes the author removed (kept from [previous], marked retired). */
fun mergeCatalog(previous: CatalogSnapshot?, fresh: CatalogSnapshot, installed: Set<String>): CatalogSnapshot {
    val freshIds = fresh.catalog.recipes.map { it.id }.toSet()
    val kept = previous?.catalog?.recipes.orEmpty().filter { it.id in installed && it.id !in freshIds }
    if (kept.isEmpty()) return fresh
    return fresh.copy(
        catalog = fresh.catalog.copy(recipes = fresh.catalog.recipes + kept),
        meta = fresh.meta + kept.associate { it.id to (previous?.meta?.get(it.id) ?: RecipeMeta()).copy(retired = true) },
    )
}
