package com.habittags.app

import android.app.Application
import com.habittags.app.catalog.BundledCatalogSource
import com.habittags.app.catalog.CatalogSource
import com.habittags.core.recipe.Catalog
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.isUsable
import com.habittags.core.store.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File

class HabitTagsApp : Application() {
    private lateinit var store: StateStore
    private lateinit var catalogSource: CatalogSource
    private val _state = MutableStateFlow(AppState())
    private val _catalog = MutableStateFlow<Catalog?>(null)

    val state: StateFlow<AppState> = _state.asStateFlow()
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        store = StateStore(File(filesDir, "state.json"))
        _state.value = store.load()
        catalogSource = BundledCatalogSource(this)
    }

    fun update(fn: (AppState) -> AppState): AppState = synchronized(this) {
        val next = fn(_state.value)
        store.save(next)
        _state.value = next
        next
    }

    /** Loads the catalog on first use; taps can arrive before any UI has started. */
    fun catalogNow(): Catalog = _catalog.value ?: runBlocking { catalogSource.load() }.also { _catalog.value = it }

    fun recipe(id: String): Recipe? = catalogNow().recipes.firstOrNull { it.id == id }?.takeIf(::isUsable)

    fun groups(): Map<String, Set<String>> {
        val overrides = _state.value.groupOverrides
        return catalogNow().appGroups.associate { it.id to (overrides[it.id] ?: it.androidPackages.toSet()) }
    }

    companion object {
        fun from(context: android.content.Context) = context.applicationContext as HabitTagsApp
    }
}
