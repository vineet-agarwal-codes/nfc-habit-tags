package com.habittags.core.store

import com.habittags.core.mode.ActiveMode
import com.habittags.core.mode.InstalledTag
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TapLogEntry(val atMs: Long, val tagId: String, val label: String)

@Serializable
data class AppState(
    val tags: Map<String, InstalledTag> = emptyMap(),
    val active: Map<String, ActiveMode> = emptyMap(),
    val groupOverrides: Map<String, Set<String>> = emptyMap(),
    val tapLog: List<TapLogEntry> = emptyList(),
)

/** Platform-neutral persistence format; each app only reads and writes the resulting text. */
object StateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)

    /** Missing or corrupt data yields an empty state rather than a crash. */
    fun decode(text: String?): AppState =
        text?.let { runCatching { json.decodeFromString(AppState.serializer(), it) }.getOrNull() } ?: AppState()
}
