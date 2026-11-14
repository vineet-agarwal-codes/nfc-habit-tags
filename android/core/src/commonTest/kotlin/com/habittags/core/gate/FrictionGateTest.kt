package com.habittags.core.gate

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FrictionGateTest {
    @Test
    fun passageLengthAndDeterminism() {
        val p = FrictionGate.passage(Random(1))
        assertTrue(p.split(" ").size in 100..130, "words: ${p.split(" ").size}")
        assertEquals(p, FrictionGate.passage(Random(1)))
        assertNotEquals(p, FrictionGate.passage(Random(2)))
        assertFalse(p.contains("  "))
        assertFalse(p.any { it.isDigit() })
    }

    @Test
    fun passageHonoursWordOverride() {
        assertTrue(FrictionGate.passage(Random(1), targetWords = 5).split(" ").size in 5..30)
    }

    @Test
    fun acceptEdit() {
        val cases = listOf(
            Triple("ab", "abc", true), Triple("ab", "abcde", true), Triple("ab", "abcdef", false),
            Triple("abc", "ab", true), Triple("", "abcd", false),
        )
        for ((before, after, expected) in cases) assertEquals(expected, FrictionGate.acceptEdit(before, after), "$before -> $after")
    }

    @Test
    fun completionAndProgressIgnoreCaseAndWhitespace() {
        assertTrue(FrictionGate.isComplete("The  cat\nsat ", "the cat sat"))
        assertFalse(FrictionGate.isComplete("the cat", "the cat sat"))
        assertEquals(6, FrictionGate.progress("the cax", "the cat sat"))
        assertEquals(0, FrictionGate.progress("", "the cat sat"))
    }

    @Test
    fun enoughSentences() {
        assertTrue(GATE_SENTENCES.size >= 80)
    }
}
