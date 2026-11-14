package com.habittags.core.rules

import com.habittags.core.recipe.Log
import com.habittags.core.recipe.Rule
import com.habittags.core.recipe.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalTime

class TimeWindowTest {
    private fun rule(window: String?, label: String) = Rule(window?.let { When(it) }, listOf(Log(label)))
    private fun label(r: Rule?) = (r?.actions?.single() as Log?)?.label

    private val night = listOf(rule("22:00-06:00", "A"), rule("06:00-22:00", "B"))

    @ParameterizedTest
    @CsvSource("23:59,A", "00:00,A", "05:59,A", "06:00,B", "21:59,B", "22:00,A")
    fun midnightWrap(time: String, expected: String) {
        assertEquals(expected, label(selectRule(night, LocalTime.parse(time))))
    }

    @ParameterizedTest
    @CsvSource("08:59", "17:00")
    fun outsideWindowIsNull(time: String) {
        assertEquals(null, label(selectRule(listOf(rule("09:00-17:00", "A")), LocalTime.parse(time))))
    }

    @Test
    fun fallsBackToRuleWithoutWindow() {
        val rules = listOf(rule("09:00-17:00", "A"), rule(null, "B"))
        assertEquals("B", label(selectRule(rules, LocalTime.parse("18:00"))))
        assertEquals("A", label(selectRule(rules, LocalTime.parse("09:00"))))
    }

    @Test
    fun malformedWindowThrows() {
        assertThrows<IllegalArgumentException> { selectRule(listOf(rule("25:00-06:00", "A")), LocalTime.NOON) }
    }
}
