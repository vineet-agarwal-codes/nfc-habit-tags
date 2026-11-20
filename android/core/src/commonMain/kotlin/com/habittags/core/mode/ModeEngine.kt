package com.habittags.core.mode

import com.habittags.core.recipe.Action
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.EndPolicy
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.bindInt
import com.habittags.core.rules.formatMinutes
import com.habittags.core.rules.resolveWindow
import com.habittags.core.rules.selectRule
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes

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
    /** Times a blocked app was opened while this mode ran. */
    val reaches: Int = 0,
    /** Started outside its hours as a nap (ends after the nap length). */
    val nap: Boolean = false,
)

sealed interface TapResult {
    data object UnknownTag : TapResult
    data object RecipeUnavailable : TapResult
    data object AlreadyOn : TapResult
    data class Started(val state: Map<String, ActiveMode>, val effects: List<Action>) : TapResult
    data class Ended(val state: Map<String, ActiveMode>, val effects: List<Action>) : TapResult
    data class StrictRefused(val remainingMs: Long) : TapResult
    data class Ran(val effects: List<Action>) : TapResult

    /** A toggle recipe tapped outside the time windows in which it turns on; `windows` are those "HH:MM-HH:MM" ranges. */
    data class OutsideHours(val windows: List<String>, val effects: List<Action>) : TapResult

    /** The recipe's tag can't end it ([Recipe.tagCanEnd] false); it ends at [endsAt] or through End early. */
    data class TagCantEnd(val endsAt: Long?) : TapResult
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
    ): TapResult = tap(active, tag, recipe, now, timeZone, groups, force = false)

    private fun tap(
        active: Map<String, ActiveMode>,
        tag: InstalledTag?,
        recipe: Recipe?,
        now: Instant,
        timeZone: TimeZone,
        groups: Map<String, Set<String>>,
        force: Boolean,
    ): TapResult {
        if (tag == null) return TapResult.UnknownTag
        if (recipe == null) return TapResult.RecipeUnavailable
        val nowMs = now.toEpochMilliseconds()
        val key = modeKey(active, tag, nowMs)
        val current = active[key]?.takeIf { it.isLive(nowMs) }
        if (recipe.mode == Mode.toggle && current != null) {
            if (!recipe.tagCanEnd) return TapResult.TagCantEnd(current.autoOffAt)
            val strictUntil = current.strictUntil
            if (strictUntil != null && nowMs < strictUntil) return TapResult.StrictRefused(strictUntil - nowMs)
            return TapResult.Ended(active - key, recipe.endActions)
        }
        var actions = selectRule(recipe.rules, now.toLocalDateTime(timeZone).time, recipe.params, tag.params)?.actions.orEmpty()
        if (recipe.mode == Mode.oneShot) return TapResult.Ran(actions.filter { it !is BlockGroups && it !is Dnd })
        // A toggle rule with nothing to keep on (e.g. a daytime tap on the bed tag) just runs its effects,
        // unless the user chose "Start anyway", which uses the recipe's mode rule regardless of its hours.
        var outsideHours = false
        if (actions.none { it.keepsOn() }) {
            outsideHours = true
            val modeRule = recipe.rules.firstOrNull { r -> r.actions.any { it.keepsOn() } }
            val windows = modeWindows(recipe, tag.params)
            when {
                force && modeRule != null -> actions = modeRule.actions
                windows.isEmpty() -> return TapResult.Ran(actions)
                else -> return TapResult.OutsideHours(windows, actions)
            }
        }
        val effects = actions.filter { !it.keepsOn() }

        val autoOff = bindInt(recipe.autoOffMinutes, recipe.params, tag.params)?.takeIf { it > 0 }
        val napMinutes = recipe.endPolicy?.nap?.takeIf { outsideHours }?.let { bindInt(it, recipe.params, tag.params) }
        val strict = bindInt(recipe.strictMinutes, recipe.params, tag.params)?.takeIf { it > 0 }
        val blockedGroups = actions.filterIsInstance<BlockGroups>().flatMap { it.groups }.toSet()
        val mode = ActiveMode(
            tagId = tag.tagId,
            recipeId = recipe.id,
            startedAt = nowMs,
            autoOffAt = napMinutes?.let { nowMs + it * MINUTE_MS }
                ?: recipe.endPolicy?.let { wakeEnd(now, timeZone, it, recipe, tag.params).toEpochMilliseconds() }
                ?: autoOff?.let { nowMs + it * MINUTE_MS },
            strictUntil = strict?.let { nowMs + it * MINUTE_MS },
            blockedGroups = blockedGroups,
            dnd = actions.any { it is Dnd },
            blockedPackages = blockedGroups.flatMap { groups[it].orEmpty() }.toSet(),
            nap = napMinutes != null,
        )
        return TapResult.Started(active + (tag.tagId to mode), effects)
    }

    /** A recipe runs at most once: a tap acts on the recipe's running mode, whichever tag (or the app) started it. */
    fun modeKey(active: Map<String, ActiveMode>, tag: InstalledTag, nowMs: Long): String =
        tag.tagId.takeIf { active[it]?.isLive(nowMs) == true }
            ?: active.values.firstOrNull { it.recipeId == tag.recipeId && it.isLive(nowMs) }?.tagId
            ?: tag.tagId

    /** Starting from the app is a tap that may only start, never end, a mode; [force] ignores the recipe's hours. */
    fun startManually(
        active: Map<String, ActiveMode>,
        tag: InstalledTag,
        recipe: Recipe?,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        groups: Map<String, Set<String>> = emptyMap(),
        force: Boolean = false,
    ): TapResult {
        val nowMs = now.toEpochMilliseconds()
        if (active.values.any { it.recipeId == tag.recipeId && it.isLive(nowMs) }) return TapResult.AlreadyOn
        return tap(active, tag, recipe, now, timeZone, groups, force)
    }

    /** The "HH:MM-HH:MM" windows in which a toggle recipe turns on, resolved with the user's times. */
    fun modeWindows(recipe: Recipe, userValues: Map<String, Int>): List<String> =
        recipe.rules.filter { r -> r.actions.any { it.keepsOn() } }
            .mapNotNull { r -> r.`when`?.let { resolveWindow(it.time, recipe.params, userValues) } }

    /**
     * "wake" end policy: the next wake-up time after [start]; if that leaves less than the usual sleep
     * (usual bedtime → wake-up), start + the usual sleep instead, but never after that morning's latest
     * wake-up. Local times are turned into instants in [zone], so DST nights come out right.
     */
    fun wakeEnd(start: Instant, zone: TimeZone, policy: EndPolicy, recipe: Recipe, userValues: Map<String, Int>): Instant {
        fun minutes(v: kotlinx.serialization.json.JsonElement) = bindInt(v, recipe.params, userValues)!!
        val wakeAt = minutes(policy.wakeAt)
        val usual = minutes(policy.usual)
        val latest = minutes(policy.latest)
        val startLocal = start.toLocalDateTime(zone)
        val startMin = startLocal.hour * 60 + startLocal.minute
        val wakeDate = if (startMin < wakeAt) startLocal.date else startLocal.date.plus(1, DateTimeUnit.DAY)
        val wake = LocalDateTime(wakeDate, LocalTime(wakeAt / 60, wakeAt % 60)).toInstant(zone)
        val usualSleep = ((wakeAt - usual) % 1440 + 1440) % 1440
        val sleepLength = if (usualSleep == 0) 1440 else usualSleep
        if (wake - start >= sleepLength.minutes) return wake
        val latestDate = if (latest >= wakeAt) wakeDate else wakeDate.plus(1, DateTimeUnit.DAY)
        val latestAt = LocalDateTime(latestDate, LocalTime(latest / 60, latest % 60)).toInstant(zone)
        return minOf(start + sleepLength.minutes, latestAt)
    }

    private fun Action.keepsOn() = this is BlockGroups || this is Dnd

    fun expire(active: Map<String, ActiveMode>, nowMs: Long, recipes: (String) -> Recipe?): Pair<Map<String, ActiveMode>, List<Action>> {
        val (expired, remaining) = active.values.partition { it.autoOffAt != null && nowMs >= it.autoOffAt }
        val effects = expired.flatMap { recipes(it.recipeId)?.endActions.orEmpty() }
        return remaining.associateBy { it.tagId } to effects
    }

    fun endViaGate(active: Map<String, ActiveMode>, tagId: String): Map<String, ActiveMode> = active - tagId

    /** The foreground app switched to [pkg]: every running mode that blocks it counts one reach. */
    fun recordReach(active: Map<String, ActiveMode>, pkg: String): Map<String, ActiveMode> =
        active.mapValues { (_, m) -> if (pkg in m.blockedPackages) m.copy(reaches = m.reaches + 1) else m }

    fun blockedPackages(active: Map<String, ActiveMode>): Set<String> = active.values.flatMap { it.blockedPackages }.toSet()

    fun dndWanted(active: Map<String, ActiveMode>): Boolean = active.values.any { it.dnd }
}

/**
 * "Turns on between 21:00 and 05:00" for a toggle recipe with time windows, using the user's times;
 * with a wake-up end: "Turns on from 20:00 · ends by 05:00 (up to 08:00 if you sleep late)"; null otherwise.
 */
fun hoursText(recipe: Recipe, userValues: Map<String, Int>): String? = runCatching {
    val windows = ModeEngine.modeWindows(recipe, userValues).takeIf { it.isNotEmpty() && recipe.mode == Mode.toggle }
        ?: return@runCatching null
    val policy = recipe.endPolicy
    if (policy != null && windows.size == 1) {
        val wake = formatMinutes(bindInt(policy.wakeAt, recipe.params, userValues)!!)
        val latest = formatMinutes(bindInt(policy.latest, recipe.params, userValues)!!)
        "Turns on from ${windows.single().substringBefore('-')} · ends by $wake (up to $latest if you sleep late)"
    } else {
        "Turns on " + windows.joinToString(" or ") { w -> w.split("-").let { "between ${it[0]} and ${it[1]}" } }
    }
}.getOrNull()
