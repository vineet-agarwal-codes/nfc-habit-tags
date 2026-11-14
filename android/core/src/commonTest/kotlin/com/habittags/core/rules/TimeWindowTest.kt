package com.habittags.core.rules

import com.habittags.core.recipe.Log
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
}
