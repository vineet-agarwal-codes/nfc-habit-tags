package com.habittags.core.mode

import com.habittags.core.recipe.Action
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.bindInt
import com.habittags.core.rules.selectRule
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Serializable
data class InstalledTag(val tagId: String, val recipeId: String, val label: String, val params: Map<String, Int>)

@Serializable
data class ActiveMode(
    val tagId: String,
    val recipeId: String,
    val startedAt: Long,
    val autoOffAt: Long?,
    val strictUntil: Long?,
    val blockedGroups: Set<String>,
    val dnd: Boolean,
    /** Apps resolved when the mode started, so later group edits can't weaken a running mode. */
    val blockedPackages: Set<String> = emptySet(),
)

sealed interface TapResult {
    data object UnknownTag : TapResult
    data object RecipeUnavailable : TapResult
    data object AlreadyOn : TapResult
    data class Started(val state: Map<String, ActiveMode>, val effects: List<Action>) : TapResult
    data class Ended(val state: Map<String, ActiveMode>, val effects: List<Action>) : TapResult
    data class StrictRefused(val remainingMs: Long) : TapResult
    data class Ran(val effects: List<Action>) : TapResult
}

private const val MINUTE_MS = 60_000L

private fun ActiveMode.isLive(nowMs: Long) = autoOffAt == null || nowMs < autoOffAt

object ModeEngine {
    fun onTap(
        active: Map<String, ActiveMode>,
        tag: InstalledTag?,
        recipe: Recipe?,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        groups: Map<String, Set<String>> = emptyMap(),
    ): TapResult {
        if (tag == null) return TapResult.UnknownTag
        if (recipe == null) return TapResult.RecipeUnavailable
        val nowMs = now.toEpochMilliseconds()
        val key = modeKey(active, tag, nowMs)
        val current = active[key]?.takeIf { it.isLive(nowMs) }
        if (recipe.mode == Mode.toggle && current != null) {
            val strictUntil = current.strictUntil
            if (strictUntil != null && nowMs < strictUntil) return TapResult.StrictRefused(strictUntil - nowMs)
            return TapResult.Ended(active - key, recipe.endActions)
        }
        val actions = selectRule(recipe.rules, now.toLocalDateTime(timeZone).time)?.actions ?: return TapResult.Ran(emptyList())
        val effects = actions.filter { it !is BlockGroups && it !is Dnd }
        // A toggle rule with nothing to keep on (e.g. a daytime tap on the bed tag) just runs its effects.
        if (recipe.mode == Mode.oneShot || effects.size == actions.size) return TapResult.Ran(effects)

        val autoOff = bindInt(recipe.autoOffMinutes, recipe.params, tag.params)?.takeIf { it > 0 }
        val strict = bindInt(recipe.strictMinutes, recipe.params, tag.params)?.takeIf { it > 0 }
        val blockedGroups = actions.filterIsInstance<BlockGroups>().flatMap { it.groups }.toSet()
        val mode = ActiveMode(
            tagId = tag.tagId,
            recipeId = recipe.id,
            startedAt = nowMs,
            autoOffAt = autoOff?.let { nowMs + it * MINUTE_MS },
            strictUntil = strict?.let { nowMs + it * MINUTE_MS },
            blockedGroups = blockedGroups,
            dnd = actions.any { it is Dnd },
            blockedPackages = blockedGroups.flatMap { groups[it].orEmpty() }.toSet(),
        )
        return TapResult.Started(active + (tag.tagId to mode), effects)
    }

    /** A recipe runs at most once: a tap acts on the recipe's running mode, whichever tag (or the app) started it. */
    fun modeKey(active: Map<String, ActiveMode>, tag: InstalledTag, nowMs: Long): String =
        tag.tagId.takeIf { active[it]?.isLive(nowMs) == true }
            ?: active.values.firstOrNull { it.recipeId == tag.recipeId && it.isLive(nowMs) }?.tagId
            ?: tag.tagId

    /** Starting from the app is a tap that may only start, never end, a mode. */
    fun startManually(
        active: Map<String, ActiveMode>,
        tag: InstalledTag,
        recipe: Recipe?,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        groups: Map<String, Set<String>> = emptyMap(),
    ): TapResult {
        val nowMs = now.toEpochMilliseconds()
        if (active.values.any { it.recipeId == tag.recipeId && it.isLive(nowMs) }) return TapResult.AlreadyOn
        return onTap(active, tag, recipe, now, timeZone, groups)
    }

    fun expire(active: Map<String, ActiveMode>, nowMs: Long, recipes: (String) -> Recipe?): Pair<Map<String, ActiveMode>, List<Action>> {
        val (expired, remaining) = active.values.partition { it.autoOffAt != null && nowMs >= it.autoOffAt }
        val effects = expired.flatMap { recipes(it.recipeId)?.endActions.orEmpty() }
        return remaining.associateBy { it.tagId } to effects
    }

    fun endViaGate(active: Map<String, ActiveMode>, tagId: String): Map<String, ActiveMode> = active - tagId

    fun blockedPackages(active: Map<String, ActiveMode>): Set<String> = active.values.flatMap { it.blockedPackages }.toSet()

    fun dndWanted(active: Map<String, ActiveMode>): Boolean = active.values.any { it.dnd }
}
