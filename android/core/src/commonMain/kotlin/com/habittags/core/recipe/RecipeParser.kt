package com.habittags.core.recipe

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
