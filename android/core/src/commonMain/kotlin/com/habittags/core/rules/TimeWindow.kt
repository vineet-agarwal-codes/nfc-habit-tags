package com.habittags.core.rules

import com.habittags.core.recipe.ParamSpec
import com.habittags.core.recipe.Rule
import com.habittags.core.recipe.bindInt
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonPrimitive

private val WINDOW = Regex("""^([01]\d|2[0-3]):([0-5]\d)-([01]\d|2[0-3]):([0-5]\d)$""")
private val PLACEHOLDER_SIDE = Regex("""^\{\{(\w+)([+-]\d+)?\}\}$""")

/** Start-inclusive, end-exclusive; wraps past midnight when end <= start. */
fun windowContains(window: String, now: LocalTime): Boolean {
    val m = WINDOW.matchEntire(window) ?: throw IllegalArgumentException("Bad time window: $window")
    val (sh, sm, eh, em) = m.destructured
    val start = LocalTime(sh.toInt(), sm.toInt())
    val end = LocalTime(eh.toInt(), em.toInt())
    return if (start < end) now >= start && now < end else now >= start || now < end
}

/**
 * Replaces `{{param}}` and `{{param±minutes}}` sides of a window ("{{usualBedtime-180}}-{{wakeAt}}")
 * with the user's time values (minutes since midnight, clamped like any param, then offset around the
 * clock); literal sides are kept as written.
 */
fun resolveWindow(window: String, spec: List<ParamSpec>, userValues: Map<String, Int>): String =
    sides(window).joinToString("-") { side ->
        val m = PLACEHOLDER_SIDE.matchEntire(side) ?: return@joinToString side
        val (key, offset) = m.destructured
        val base = bindInt(JsonPrimitive("{{$key}}"), spec, userValues)!!
        formatMinutes(((base + (offset.toIntOrNull() ?: 0)) % 1440 + 1440) % 1440)
    }

/** Splits "start-end" on the dash between the sides, not the minus inside an offset placeholder. */
private fun sides(window: String): List<String> {
    var depth = 0
    for ((i, c) in window.withIndex()) {
        when {
            c == '{' -> depth++
            c == '}' -> depth--
            c == '-' && depth == 0 -> return listOf(window.substring(0, i), window.substring(i + 1))
        }
    }
    return listOf(window)
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
