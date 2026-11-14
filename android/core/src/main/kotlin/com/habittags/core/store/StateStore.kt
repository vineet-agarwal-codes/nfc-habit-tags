package com.habittags.core.store

import com.habittags.core.mode.ActiveMode
import com.habittags.core.mode.InstalledTag
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class TapLogEntry(val atMs: Long, val tagId: String, val label: String)

@Serializable
data class AppState(
    val tags: Map<String, InstalledTag> = emptyMap(),
    val active: Map<String, ActiveMode> = emptyMap(),
    val groupOverrides: Map<String, Set<String>> = emptyMap(),
    val tapLog: List<TapLogEntry> = emptyList(),
)

class StateStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppState =
        runCatching { json.decodeFromString(AppState.serializer(), file.readText()) }.getOrDefault(AppState())

    fun save(state: AppState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), state))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save state to $file" }
        }
    }
}
