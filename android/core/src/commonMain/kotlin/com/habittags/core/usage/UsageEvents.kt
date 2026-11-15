package com.habittags.core.usage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One anonymous usage event; `day` is a local date in zone `tz` and never travels without it. */
@Serializable
data class UsageEvent(
    val type: String,
    val v: Int,
    val day: String?,
    val tz: String?,
    val at: String? = null,
    val payload: JsonObject,
)
