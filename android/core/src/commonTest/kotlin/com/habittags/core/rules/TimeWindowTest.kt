package com.habittags.core.rules

import com.habittags.core.recipe.Log
import com.habittags.core.recipe.ParamSpec
import com.habittags.core.recipe.Rule
import com.habittags.core.recipe.When
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeWindowTest {
    private fun rule(window: String?, label: String) = Rule(window?.let { When(it) }, listOf(Log(label)))
    private fun label(r: Rule?) = (r?.actions?.single() as Log?)?.label
    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun midnightWrap() {
        val night = listOf(rule("22:00-06:00", "A"), rule("06:00-22:00", "B"))
        val cases = mapOf("23:59" to "A", "00:00" to "A", "05:59" to "A", "06:00" to "B", "21:59" to "B", "22:00" to "A")
        for ((time, expected) in cases) assertEquals(expected, label(selectRule(night, t(time))), time)
    }

    @Test
    fun outsideWindowIsNull() {
        for (time in listOf("08:59", "17:00")) {
            assertEquals(null, label(selectRule(listOf(rule("09:00-17:00", "A")), t(time))), time)
        }
    }

    @Test
    fun fallsBackToRuleWithoutWindow() {
        val rules = listOf(rule("09:00-17:00", "A"), rule(null, "B"))
        assertEquals("B", label(selectRule(rules, t("18:00"))))
        assertEquals("A", label(selectRule(rules, t("09:00"))))
    }

    @Test
    fun malformedWindowThrows() {
        assertFailsWith<IllegalArgumentException> { selectRule(listOf(rule("25:00-06:00", "A")), t("12:00")) }
    }

    private val timeSpecs = listOf(
        ParamSpec("sleepStart", "time", "Sleep from", 1260, 0, 1439),
        ParamSpec("sleepEnd", "time", "until", 300, 0, 1439),
    )

    @Test
    fun placeholderWindowsResolveFromParams() {
        // window, user values, expected resolved window
        val cases = listOf(
            Triple("{{sleepStart}}-{{sleepEnd}}", emptyMap<String, Int>(), "21:00-05:00"),
            Triple("{{sleepStart}}-{{sleepEnd}}", mapOf("sleepStart" to 1380, "sleepEnd" to 420), "23:00-07:00"),
            Triple("{{sleepStart}}-06:30", mapOf("sleepStart" to 0), "00:00-06:30"),
            Triple("{{sleepStart}}-{{sleepEnd}}", mapOf("sleepStart" to 5000, "sleepEnd" to -3), "23:59-00:00"),
            Triple("22:00-06:00", mapOf("sleepStart" to 0), "22:00-06:00"),
        )
        for ((window, values, expected) in cases) assertEquals(expected, resolveWindow(window, timeSpecs, values), "$window $values")
    }

    @Test
    fun placeholderWindowSelectionWrapsMidnight() {
        val rules = listOf(rule("{{sleepStart}}-{{sleepEnd}}", "night"), rule(null, "day"))
        val user = mapOf("sleepStart" to 1380, "sleepEnd" to 420) // 23:00-07:00
        val cases = mapOf("22:59" to "day", "23:00" to "night", "03:00" to "night", "06:59" to "night", "07:00" to "day", "15:10" to "day")
        for ((time, expected) in cases) assertEquals(expected, label(selectRule(rules, t(time), timeSpecs, user)), time)
    }

    @Test
    fun unknownPlaceholderInWindowThrows() {
        assertFailsWith<IllegalArgumentException> { resolveWindow("{{nope}}-05:00", timeSpecs, emptyMap()) }
    }

    @Test
    fun formatsMinutesOfDay() {
        val cases = mapOf(0 to "00:00", 300 to "05:00", 1260 to "21:00", 1439 to "23:59", 65 to "01:05")
        for ((minutes, expected) in cases) assertEquals(expected, formatMinutes(minutes))
    }
}
