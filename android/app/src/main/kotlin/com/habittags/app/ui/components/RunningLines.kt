package com.habittags.app.ui.components

import com.habittags.app.HabitTagsApp
import com.habittags.core.mode.ActiveMode
import com.habittags.core.mode.RunningText
import kotlinx.datetime.TimeZone

/** What a running mode shows wherever it appears (Home card, running screen, block cover). */
data class RunningLines(
    /** "Sleep mode is on", "Nap is on". */
    val title: String,
    /** "Bed · Sleep Mode". */
    val subtitle: String,
    /** "12 h 41 min left", or null when it doesn't end on its own. */
    val remaining: String?,
    /** "Ends tomorrow at 05:00". */
    val ends: String?,
    /** "Blocking social media, video and streaming · Do Not Disturb on". */
    val paused: String?,
    /** "You reached for a blocked app once". */
    val reaches: String?,
)

fun HabitTagsApp.runningLines(mode: ActiveMode, nowMs: Long): RunningLines {
    val recipe = recipe(mode.recipeId)
    val space = spaceName(mode.recipeId)
    val groupTitles = catalogNow().appGroups.filter { it.id in mode.blockedGroups }.map { it.title }
    val recipeName = recipe?.title?.substringAfter(": ")
    val waiting = !mode.blocking(nowMs)
    val paused = RunningText.paused(groupTitles, mode.dnd)
    return RunningLines(
        title = RunningText.title(recipe, mode, space),
        subtitle = listOfNotNull(space, if (mode.nap) "nap" else recipeName?.takeIf { it != space }).joinToString(" · "),
        // During a lock delay the big line counts down to the lock instead of to the end.
        remaining = if (waiting) RunningText.locksIn(mode.blockFrom!! - nowMs) else mode.autoOffAt?.let { RunningText.remaining(it - nowMs) },
        ends = mode.autoOffAt?.let { RunningText.ends(it, nowMs, TimeZone.currentSystemDefault()) },
        paused = if (waiting) paused?.replaceFirst("Blocking", "Then blocking") else paused,
        reaches = mode.reaches.takeIf { it > 0 }?.let(::reachText),
    )
}
