package com.habittags.core.catalog

import com.habittags.core.recipe.ParamSpec

/**
 * Keeps the user's values for params that still exist (clamped to new limits), carrying a value over
 * from a param's `renamedFrom` key when the new key has none; defaults apply to the rest.
 */
fun mergeParams(userValues: Map<String, Int>, specs: List<ParamSpec>): Map<String, Int> =
    specs.mapNotNull { spec ->
        val value = userValues[spec.key] ?: spec.renamedFrom?.let { userValues[it] } ?: return@mapNotNull null
        spec.key to value.coerceIn(spec.min, spec.max)
    }.toMap()
