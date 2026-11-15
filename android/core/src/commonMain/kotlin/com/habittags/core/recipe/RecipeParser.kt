package com.habittags.core.recipe

import com.habittags.core.rules.resolveWindow
import com.habittags.core.rules.windowContains
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

val CatalogJson = Json { ignoreUnknownKeys = true }

private val PLACEHOLDER = Regex("""^\{\{(\w+)\}\}$""")

fun parseCatalog(json: String): Catalog = CatalogJson.decodeFromString(Catalog.serializer(), json)

fun isUsable(recipe: Recipe): Boolean =
    recipe.schemaVersion <= SUPPORTED_SCHEMA_VERSION &&
        (recipe.rules.flatMap { it.actions } + recipe.endActions).none { it is Unsupported }

fun bindInt(value: JsonElement?, spec: List<ParamSpec>, userValues: Map<String, Int>): Int? {
    if (value == null || value is JsonNull) return null
    require(value is JsonPrimitive) { "Expected a number or {{param}}, got $value" }
    value.intOrNull?.takeIf { !value.isString }?.let { return it }
    val key = PLACEHOLDER.matchEntire(value.content)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("Not a number or {{param}}: ${value.content}")
    val param = spec.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown param: $key")
    return (userValues[key] ?: param.default).coerceIn(param.min, param.max)
}

/**
 * The first problem that would make this recipe throw when tapped or edited, or null. Remote
 * recipes are authored by hand, so they're checked before the app ever stores or runs them.
 * Recipes for a newer schema aren't checked; the app never runs them.
 */
fun validate(recipe: Recipe): String? {
    if (recipe.schemaVersion > SUPPORTED_SCHEMA_VERSION) return null
    recipe.params.firstOrNull { it.min > it.max || it.default !in it.min..it.max }
        ?.let { return "Param ${it.key}: default ${it.default} not within ${it.min}..${it.max}" }
    recipe.params.firstOrNull { it.type == "time" && (it.min < 0 || it.max > 1439) }
        ?.let { return "Time param ${it.key} must stay within 00:00..23:59" }
    val bound = listOf(recipe.autoOffMinutes, recipe.strictMinutes) +
        (recipe.rules.flatMap { it.actions } + recipe.endActions).filterIsInstance<Timer>().map { it.minutes }
    for (value in bound) {
        runCatching { bindInt(value, recipe.params, emptyMap()) }.exceptionOrNull()?.let { return it.message }
    }
    for (window in recipe.rules.mapNotNull { it.`when`?.time }) {
        runCatching { windowContains(resolveWindow(window, recipe.params, emptyMap()), LocalTime(0, 0)) }
            .exceptionOrNull()?.let { return it.message }
    }
    return null
}
