package com.habittags.core.rules

import com.habittags.core.recipe.ParamSpec
import com.habittags.core.recipe.Rule
import com.habittags.core.recipe.bindInt
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonPrimitive

private val WINDOW = Regex("""^([01]\d|2[0-3]):([0-5]\d)-([01]\d|2[0-3]):([0-5]\d)$""")
private val PLACEHOLDER_SIDE = Regex("""^\{\{\w+\}\}$""")

/** Start-inclusive, end-exclusive; wraps past midnight when end <= start. */
fun windowContains(window: String, now: LocalTime): Boolean {
    val m = WINDOW.matchEntire(window) ?: throw IllegalArgumentException("Bad time window: $window")
    val (sh, sm, eh, em) = m.destructured
    val start = LocalTime(sh.toInt(), sm.toInt())
    val end = LocalTime(eh.toInt(), em.toInt())
    return if (start < end) now >= start && now < end else now >= start || now < end
}

/**
 * Replaces `{{param}}` sides of a window ("{{sleepStart}}-{{sleepEnd}}") with the user's time values
 * (minutes since midnight, clamped like any param); literal sides are kept as written.
 */
fun resolveWindow(window: String, spec: List<ParamSpec>, userValues: Map<String, Int>): String =
    window.split("-").joinToString("-") { side ->
        if (PLACEHOLDER_SIDE.matches(side)) formatMinutes(bindInt(JsonPrimitive(side), spec, userValues)!!) else side
    }

fun formatMinutes(minutesOfDay: Int): String {
    val m = minutesOfDay.coerceIn(0, 1439)
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}

fun selectRule(
    rules: List<Rule>,
    now: LocalTime,
    spec: List<ParamSpec> = emptyList(),
    userValues: Map<String, Int> = emptyMap(),
): Rule? = rules.firstOrNull { rule -> rule.`when`?.let { windowContains(resolveWindow(it.time, spec, userValues), now) } ?: true }
