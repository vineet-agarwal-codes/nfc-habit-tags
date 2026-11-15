package com.habittags.core.catalog

import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.isUsable
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

const val NEW_RECIPE_BOOST_SLOTS = 3
val NEW_RECIPE_WINDOW = 14.days
const val MIN_SHOWN_STAT = 5

/**
 * Recipes for Discover: usable, not installed, not retired. Up to 3 published in the last 14 days go
 * first (newest first) so they can collect usage; the rest rank by weekly active phones.
 */
fun discoverOrder(snapshot: CatalogSnapshot, installed: Set<String>, now: Instant): List<Recipe> {
    fun meta(r: Recipe) = snapshot.meta[r.id] ?: RecipeMeta()
    val candidates = snapshot.catalog.recipes.filter { isUsable(it) && it.id !in installed && !meta(it).retired }
    val boosted = candidates
        .filter { r -> meta(r).publishedAt?.let { now - it <= NEW_RECIPE_WINDOW && it <= now } == true }
        .sortedByDescending { meta(it).publishedAt }
        .take(NEW_RECIPE_BOOST_SLOTS)
    val rest = (candidates - boosted.toSet()).sortedWith(
        compareByDescending<Recipe> { snapshot.stats[it.id]?.weeklyActive ?: 0 }
            .thenByDescending { snapshot.stats[it.id]?.installs ?: 0 }
            .thenBy { meta(it).sortHint }
            .thenBy { it.title },
    )
    return boosted + rest
}

/** Card lines; numbers under 5 are hidden, and "New" replaces both when neither is shown. */
fun statLabel(stats: RecipeStats?): List<String> {
    if (stats == null) return emptyList()
    val lines = buildList {
        if (stats.weeklyActive >= MIN_SHOWN_STAT) add("Used by ${grouped(stats.weeklyActive)} people this week")
        if (stats.installs >= MIN_SHOWN_STAT) add("${grouped(stats.installs)} installs")
    }
    return lines.ifEmpty { listOf("New") }
}

private fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()
