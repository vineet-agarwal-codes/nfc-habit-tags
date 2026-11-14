package com.habittags.core.rules

import com.habittags.core.recipe.Rule
import kotlinx.datetime.LocalTime

private val WINDOW = Regex("""^([01]\d|2[0-3]):([0-5]\d)-([01]\d|2[0-3]):([0-5]\d)$""")

/** Start-inclusive, end-exclusive; wraps past midnight when end <= start. */
fun windowContains(window: String, now: LocalTime): Boolean {
    val m = WINDOW.matchEntire(window) ?: throw IllegalArgumentException("Bad time window: $window")
    val (sh, sm, eh, em) = m.destructured
    val start = LocalTime(sh.toInt(), sm.toInt())
    val end = LocalTime(eh.toInt(), em.toInt())
    return if (start < end) now >= start && now < end else now >= start || now < end
}

fun selectRule(rules: List<Rule>, now: LocalTime): Rule? =
    rules.firstOrNull { rule -> rule.`when`?.let { windowContains(it.time, now) } ?: true }
