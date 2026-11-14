package com.habittags.core.catalog

import com.habittags.core.recipe.ParamSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ParamMergeTest {
    @ParameterizedTest
    @MethodSource("cases")
    fun merge(user: Map<String, Int>, specs: List<ParamSpec>, expected: Map<String, Int>) {
        assertEquals(expected, mergeParams(user, specs))
    }

    companion object {
        private fun spec(key: String, min: Int = 15, max: Int = 480) = ParamSpec(key, "duration", key, min, min, max)

        @JvmStatic
        fun cases() = listOf(
            Arguments.of(mapOf("a" to 60, "b" to 10), listOf(spec("a")), mapOf("a" to 60)),
            Arguments.of(mapOf("a" to 600), listOf(spec("a")), mapOf("a" to 480)),
            Arguments.of(mapOf("a" to 5), listOf(spec("a")), mapOf("a" to 15)),
            Arguments.of(emptyMap<String, Int>(), listOf(spec("a"), spec("c")), emptyMap<String, Int>()),
        )
    }
}
