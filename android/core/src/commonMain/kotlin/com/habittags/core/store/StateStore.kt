package com.habittags.core.store

import com.habittags.core.mode.ActiveMode
import com.habittags.core.mode.InstalledTag
import com.habittags.core.usage.UsageEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@Serializable
data class TapLogEntry(
    val atMs: Long,
    val tagId: String,
    val label: String,
    val recipeId: String? = null,
    /** IANA zone at tap time, so the tap's local day never moves if the phone changes zone. */
    val zone: String? = null,
    /** "tag" or "app" (started from the app). Kept on the phone only. */
    val source: String = "tag",
)

@Serializable
data class AppState(
    val tags: Map<String, InstalledTag> = emptyMap(),
    val active: Map<String, ActiveMode> = emptyMap(),
    val groupOverrides: Map<String, Set<String>> = emptyMap(),
    val tapLog: List<TapLogEntry> = emptyList(),
    val stateVersion: Int = STATE_VERSION,
    val installedRecipes: Set<String> = emptySet(),
    val deviceId: String? = null,
    val lastReportMs: Long = 0,
    val pendingEvents: List<UsageEvent> = emptyList(),
    /** Settings per installed recipe, used when it's started from the app without a tag. */
    val recipeParams: Map<String, Map<String, Int>> = emptyMap(),
)

const val STATE_VERSION = 2

/** What a manual start taps: the recipe's first tag, or a virtual "app:<recipeId>" tag when it has none. */
fun AppState.manualTag(recipeId: String, title: String): InstalledTag =
    tags.values.firstOrNull { it.recipeId == recipeId }
        ?: InstalledTag("$APP_KEY_PREFIX$recipeId", recipeId, title, recipeParams[recipeId].orEmpty())

fun AppState.paramsFor(key: String, recipeId: String): Map<String, Int> = tags[key]?.params ?: recipeParams[recipeId].orEmpty()

const val APP_KEY_PREFIX = "app:"

fun AppState.withDeviceId(newId: () -> String): AppState = if (deviceId != null) this else copy(deviceId = newId())

/** Platform-neutral persistence format; each app only reads and writes the resulting text. */
object StateCodec {
    // encodeDefaults keeps stateVersion in the file; without it a v2 state would read back as v1.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)

    /** Missing or corrupt data yields an empty state rather than a crash. */
    fun decode(text: String?): AppState = text?.let(::decodeOrNull) ?: AppState()

    /** Null when [text] isn't a readable state, so callers can preserve it instead of overwriting it. */
    fun decodeOrNull(text: String): AppState? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        // State saved before versioning (v1) has no stateVersion; decoding it as-is would take the v2 default.
        val versioned = if ("stateVersion" in obj) obj else JsonObject(obj + ("stateVersion" to JsonPrimitive(1)))
        val state = runCatching { json.decodeFromJsonElement(AppState.serializer(), versioned) }.getOrNull() ?: return null
        return migrate(state)
    }

    private fun migrate(state: AppState): AppState =
        if (state.stateVersion >= STATE_VERSION) state
        else state.copy(installedRecipes = state.tags.values.map { it.recipeId }.toSet(), stateVersion = STATE_VERSION)
}
