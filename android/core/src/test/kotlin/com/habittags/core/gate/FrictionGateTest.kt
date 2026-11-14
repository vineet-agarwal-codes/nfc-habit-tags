package com.habittags.core.gate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.random.Random

class FrictionGateTest {
    @Test
    fun passageLengthAndDeterminism() {
        val p = FrictionGate.passage(Random(1))
        assertTrue(p.split(" ").size in 100..130, "words: ${p.split(" ").size}")
        assertEquals(p, FrictionGate.passage(Random(1)))
        assertNotEquals(p, FrictionGate.passage(Random(2)))
        assertFalse(p.contains("  "))
    }

    @Test
    fun passageHonoursWordOverride() {
        val p = FrictionGate.passage(Random(1), targetWords = 5)
        assertTrue(p.split(" ").size in 5..30)
    }

    @ParameterizedTest
    @CsvSource("ab,abc,true", "ab,abcde,true", "ab,abcdef,false", "abc,ab,true", "'',abcd,false")
    fun acceptEdit(before: String, after: String, expected: Boolean) {
        assertEquals(expected, FrictionGate.acceptEdit(before, after))
    }

    @Test
    fun completionAndProgressIgnoreCaseAndWhitespace() {
        assertTrue(FrictionGate.isComplete("The  cat\nsat ", "the cat sat"))
        assertFalse(FrictionGate.isComplete("the cat", "the cat sat"))
        assertEquals(6, FrictionGate.progress("the cax", "the cat sat"))
        assertEquals(0, FrictionGate.progress("", "the cat sat"))
    }
}
