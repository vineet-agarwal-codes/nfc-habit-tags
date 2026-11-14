package com.habittags.core.catalog

import com.habittags.core.recipe.ParamSpec

/** Keeps the user's values for params that still exist (clamped to new limits); defaults apply to the rest. */
fun mergeParams(userValues: Map<String, Int>, specs: List<ParamSpec>): Map<String, Int> {
    val byKey = specs.associateBy { it.key }
    return userValues.mapNotNull { (key, value) -> byKey[key]?.let { key to value.coerceIn(it.min, it.max) } }.toMap()
}
