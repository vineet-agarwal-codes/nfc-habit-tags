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
}
