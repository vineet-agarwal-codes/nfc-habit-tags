package com.habittags.core.recipe

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 2: params of type "time" (minutes since midnight) and `{{param}}` sides in rule time windows.
 * 3: `endPolicy`, `tagCanEnd`, `renamedFrom` on params and `{{param±minutes}}` window sides.
 */
const val SUPPORTED_SCHEMA_VERSION = 3

@Serializable
data class Catalog(val recipes: List<Recipe>, val appGroups: List<AppGroup>)

@Serializable
data class AppGroup(val id: String, val title: String, val androidPackages: List<String>)

@Suppress("EnumEntryName")
@Serializable
enum class Mode { toggle, oneShot }

@Serializable
data class ParamSpec(
    val key: String,
    val type: String,
    val label: String,
    val default: Int,
    val min: Int,
    val max: Int,
    /** An earlier key for this param; installed values under it carry over. */
    val renamedFrom: String? = null,
)

/**
 * How a running mode ends instead of a fixed `autoOffMinutes`. Type "wake": at the next [wakeAt], or
 * after the usual sleep ([usual] → [wakeAt]) when started late, but never after [latest].
 */
@Serializable
data class EndPolicy(val type: String, val wakeAt: JsonElement, val usual: JsonElement, val latest: JsonElement)

const val END_POLICY_WAKE = "wake"

@Serializable
data class When(val time: String)

@Serializable
data class Rule(val `when`: When? = null, val actions: List<Action>)

@Serializable
data class Recipe(
    val id: String,
    val schemaVersion: Int,
    val version: Int,
    val title: String,
    val description: String,
    val placementHint: String,
    val mode: Mode,
    val params: List<ParamSpec> = emptyList(),
    val autoOffMinutes: JsonElement? = null,
    val endPolicy: EndPolicy? = null,
    /** False: tapping the tag while the mode is on doesn't end it (only its end time or End early do). */
    val tagCanEnd: Boolean = true,
    val strictMinutes: JsonElement? = null,
    val rules: List<Rule>,
    val endActions: List<Action> = emptyList(),
    val price: Int? = null,
    /** The space this recipe usually belongs to ("Desk") and its preferred colour id. */
    val space: SpaceHint? = null,
    /** Discover grouping: desk, bed, leaving, workout; anything else falls under "More recipes". */
    val place: String? = null,
)

@Serializable
data class SpaceHint(val name: String, val colour: String? = null)

@Serializable(with = ActionSerializer::class)
sealed interface Action

@Serializable @SerialName("blockGroups")
data class BlockGroups(val groups: List<String>) : Action

@Serializable @SerialName("dnd")
data object Dnd : Action

@Serializable @SerialName("timer")
data class Timer(val minutes: JsonElement) : Action

@Serializable @SerialName("checklist")
data class Checklist(val title: String, val items: List<String>) : Action

@Serializable @SerialName("openApp")
data class OpenApp(val androidPackage: String? = null, val uri: String? = null) : Action

@Serializable @SerialName("log")
data class Log(val label: String) : Action

data class Unsupported(val type: String) : Action

object ActionSerializer : KSerializer<Action> {
    private val known: Map<String, KSerializer<out Action>> = mapOf(
        "blockGroups" to BlockGroups.serializer(),
        "dnd" to Dnd.serializer(),
        "timer" to Timer.serializer(),
        "checklist" to Checklist.serializer(),
        "openApp" to OpenApp.serializer(),
        "log" to Log.serializer(),
    )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Action")

    override fun deserialize(decoder: Decoder): Action {
        val input = decoder as JsonDecoder
        val obj = input.decodeJsonElement().jsonObject
        val type = obj["type"]?.jsonPrimitive?.content ?: return Unsupported("")
        val serializer = known[type] ?: return Unsupported(type)
        return input.json.decodeFromJsonElement(serializer, JsonObject(obj - "type"))
    }

    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: Action) {
        val output = encoder as JsonEncoder
        if (value is Unsupported) {
            output.encodeJsonElement(JsonObject(mapOf("type" to JsonPrimitive(value.type))))
            return
        }
        val (type, serializer) = known.entries.first { it.value.descriptor.serialName == serialNameOf(value) }
        val body = output.json.encodeToJsonElement(serializer as KSerializer<Action>, value).jsonObject
        output.encodeJsonElement(JsonObject(mapOf("type" to JsonPrimitive(type)) + body))
    }

    private fun serialNameOf(value: Action): String = when (value) {
        is BlockGroups -> "blockGroups"
        Dnd -> "dnd"
        is Timer -> "timer"
        is Checklist -> "checklist"
        is OpenApp -> "openApp"
        is Log -> "log"
        is Unsupported -> value.type
    }
}
