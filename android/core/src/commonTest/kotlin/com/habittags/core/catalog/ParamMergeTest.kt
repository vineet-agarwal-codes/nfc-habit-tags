package com.habittags.core.catalog

import com.habittags.core.recipe.ParamSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ParamMergeTest {
    private fun spec(key: String, min: Int = 15, max: Int = 480) = ParamSpec(key, "duration", key, min, min, max)

    @Test
    fun merge() {
        val cases = listOf(
            Triple(mapOf("a" to 60, "b" to 10), listOf(spec("a")), mapOf("a" to 60)),
            Triple(mapOf("a" to 600), listOf(spec("a")), mapOf("a" to 480)),
            Triple(mapOf("a" to 5), listOf(spec("a")), mapOf("a" to 15)),
            Triple(emptyMap(), listOf(spec("a"), spec("c")), emptyMap()),
        )
        for ((user, specs, expected) in cases) assertEquals(expected, mergeParams(user, specs), "case $user")
    }

    @Test
    fun renamedParamKeepsTheOldValue() {
        val wake = ParamSpec("wakeAt", "time", "Wake up at", 300, 0, 1439, renamedFrom = "sleepEnd")
        val cases = listOf(
            // user values, expected
            mapOf("sleepEnd" to 420, "sleepStart" to 1260) to mapOf("wakeAt" to 420),
            mapOf("sleepEnd" to 420, "wakeAt" to 360) to mapOf("wakeAt" to 360), // a value under the new key wins
            emptyMap<String, Int>() to emptyMap(),
        )
        for ((user, expected) in cases) assertEquals(expected, mergeParams(user, listOf(wake)), "case $user")
    }
}
