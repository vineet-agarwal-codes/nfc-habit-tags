package com.habittags.core.space

import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.Recipe
import com.habittags.core.rules.resolveWindow
import com.habittags.core.rules.windowContains
import com.habittags.core.store.AppState
import com.habittags.core.store.manualTag
import com.habittags.core.store.paramsFor
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/** A place in the home running one installed recipe. [colour] is one of [Spaces.COLOURS]. */
@Serializable
data class Space(val recipeId: String, val name: String, val colour: String)

object Spaces {
    /** In assignment order: the first unused one goes to each new space, so colours stay distinct. */
    val COLOURS = listOf("tomato", "deep_blue", "green", "ochre", "teal", "plum")

    fun defaultName(recipe: Recipe): String =
        recipe.space?.name?.takeIf { it.isNotBlank() }
            ?: recipe.title.substringBefore(":", "").trim().takeIf { it.isNotEmpty() }
            ?: recipe.title

    /** The hint if free, else the first unused colour, else wrap around by how many are taken. */
    fun nextColour(used: Collection<String>, hint: String?): String =
        hint?.takeIf { it in COLOURS && it !in used }
            ?: COLOURS.firstOrNull { it !in used }
            ?: COLOURS[used.size % COLOURS.size]

    /**
     * Gives every installed recipe a space and drops spaces of uninstalled ones. Recipes not in [recipes]
     * (catalog not loaded yet) are left alone until they are. Run after every state change.
     */
    fun ensure(state: AppState, recipes: (String) -> Recipe?): AppState {
        var spaces = state.spaces.filterKeys { it in state.installedRecipes }
        for (id in state.installedRecipes.sorted()) {
            if (id in spaces) continue
            val recipe = recipes(id) ?: continue
            val colour = nextColour(spaces.values.map { it.colour }, recipe.space?.colour)
            spaces = spaces + (id to Space(id, defaultName(recipe), colour))
        }
        return if (spaces == state.spaces) state else state.copy(spaces = spaces)
    }

    /** A blank name falls back to the recipe's default. */
    fun rename(state: AppState, recipe: Recipe, name: String): AppState {
        val current = state.spaces[recipe.id] ?: return state
        val next = name.trim().ifEmpty { defaultName(recipe) }
        return state.copy(spaces = state.spaces + (recipe.id to current.copy(name = next)))
    }

    fun recolour(state: AppState, recipeId: String, colour: String): AppState {
        val current = state.spaces[recipeId] ?: return state
        if (colour !in COLOURS) return state
        return state.copy(spaces = state.spaces + (recipeId to current.copy(colour = colour)))
    }
}

/** What a space's card shows on Home. */
sealed interface SpaceStatus {
    data class Running(val modeKey: String, val endsAtMs: Long?, val strictUntilMs: Long?, val reaches: Int) : SpaceStatus
    /** A toggle recipe outside its hours; [time] is when it next turns on ("21:00"). */
    data class StartsAt(val time: String) : SpaceStatus
    /** A one-shot recipe: each tap runs it. */
    data object TapToRun : SpaceStatus
    data class Idle(val lastUsedMs: Long?) : SpaceStatus
}

fun spaceStatus(state: AppState, recipe: Recipe, nowMs: Long, tz: TimeZone): SpaceStatus {
    state.active.entries.firstOrNull { (_, m) -> m.recipeId == recipe.id && (m.autoOffAt == null || nowMs < m.autoOffAt) }
        ?.let { (key, m) -> return SpaceStatus.Running(key, m.autoOffAt, m.strictUntil, m.reaches) }
    if (recipe.mode == Mode.oneShot) return SpaceStatus.TapToRun
    val params = state.paramsFor(state.manualTag(recipe.id, recipe.title).tagId, recipe.id)
    val windows = recipe.rules
        .filter { r -> r.actions.any { it is BlockGroups || it is Dnd } }
        .mapNotNull { it.`when`?.time?.let { w -> resolveWindow(w, recipe.params, params) } }
    val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).time
    if (windows.isNotEmpty() && windows.none { windowContains(it, now) }) {
        return SpaceStatus.StartsAt(windows.first().substringBefore("-"))
    }
    return SpaceStatus.Idle(state.tapLog.lastOrNull { it.recipeId == recipe.id }?.atMs)
}
