package com.habittags.app.backend

import android.content.Context
import android.util.Log
import com.habittags.core.catalog.CatalogSnapshot
import com.habittags.core.catalog.mergeCatalog
import com.habittags.core.catalog.withoutInvalidRecipes
import com.habittags.core.recipe.CatalogJson
import com.habittags.core.recipe.parseCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** The catalog the app runs on: last fetched copy (cached on disk), else the bundled one. */
class CatalogRepository(
    private val context: Context,
    private val backend: Backend,
    private val installed: () -> Set<String>,
    private val scope: CoroutineScope,
) {
    private val cache = File(context.filesDir, "catalog_cache.json")
    private val _snapshot = MutableStateFlow(loadLocal())
    val snapshot: StateFlow<CatalogSnapshot> = _snapshot.asStateFlow()
    private val refreshLock = Mutex()
    private var pending: Job? = null

    private fun loadLocal(): CatalogSnapshot =
        runCatching { CatalogJson.decodeFromString(CatalogSnapshot.serializer(), cache.readText()).withoutInvalidRecipes() }.getOrNull()
            ?: CatalogSnapshot(parseCatalog(context.assets.open("catalog.json").bufferedReader().use { it.readText() }))

    suspend fun refresh() = refreshLock.withLock {
        try {
            val merged = mergeCatalog(_snapshot.value, backend.fetchCatalog(), installed())
            val tmp = File(cache.path + ".tmp")
            tmp.writeText(CatalogJson.encodeToString(CatalogSnapshot.serializer(), merged))
            tmp.renameTo(cache)
            _snapshot.value = merged
        } catch (e: Exception) {
            Log.w(TAG, "catalog refresh failed; keeping current copy", e)
        }
    }

    /** Coalesces bursts of Realtime changes into one fetch. */
    fun refreshSoon() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            refresh()
        }
    }

    private companion object {
        const val TAG = "CatalogRepository"
        const val DEBOUNCE_MS = 2_000L
    }
}
