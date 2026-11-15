package com.habittags.core.realtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Supabase Realtime (Phoenix protocol) messages for watching catalog changes. */
object Realtime {
    const val TOPIC = "realtime:catalog"
    const val HEARTBEAT_MS = 25_000L

    fun socketUrl(baseUrl: String, apiKey: String): String =
        baseUrl.replaceFirst("https://", "wss://").trimEnd('/') + "/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

    // No access_token: the publishable key isn't a JWT, and the anon role from the apikey is what we want.
    fun joinMessage(ref: Int): String = buildJsonObject {
        put("topic", TOPIC)
        put("event", "phx_join")
        put("ref", ref.toString())
        put("payload", buildJsonObject {
            put("config", buildJsonObject {
                put("postgres_changes", buildJsonArray {
                    for (table in listOf("recipes", "app_groups")) add(buildJsonObject {
                        put("event", "*")
                        put("schema", "public")
                        put("table", table)
                    })
                })
            })
        })
    }.toString()

    fun heartbeat(ref: Int): String = buildJsonObject {
        put("topic", "phoenix")
        put("event", "heartbeat")
        put("ref", ref.toString())
        put("payload", JsonObject(emptyMap()))
    }.toString()

    fun isCatalogChange(message: String): Boolean {
        val obj = runCatching { Json.parseToJsonElement(message) as? JsonObject }.getOrNull() ?: return false
        fun field(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull
        return field("event") == "postgres_changes" && field("topic") == TOPIC
    }
}
