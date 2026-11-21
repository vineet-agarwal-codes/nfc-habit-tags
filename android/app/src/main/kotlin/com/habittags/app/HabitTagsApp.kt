package com.habittags.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.habittags.app.backend.Backend
import com.habittags.app.backend.BackendFactory
import com.habittags.app.backend.CatalogRepository
import com.habittags.app.backend.RealtimeWatcher
import com.habittags.app.backend.UsageReporting
import com.habittags.app.feedback.FeedbackRepository
import com.habittags.app.feedback.pending
import com.habittags.core.catalog.CatalogSnapshot
import com.habittags.core.mode.ActiveMode
import com.habittags.core.realtime.Realtime
import com.habittags.core.recipe.Catalog
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.isUsable
import com.habittags.app.ui.theme.SpaceColour
import com.habittags.core.space.Spaces
import com.habittags.core.store.AppState
import com.habittags.core.store.withDeviceId
import com.habittags.core.usage.reportDue
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class HabitTagsApp : Application() {
    private lateinit var store: StateStore
    private val _state = MutableStateFlow(AppState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = OkHttpClient()

    lateinit var backend: Backend
        private set
    lateinit var catalogRepository: CatalogRepository
        private set
    private lateinit var realtime: RealtimeWatcher

    val state: StateFlow<AppState> = _state.asStateFlow()
    val snapshot: StateFlow<CatalogSnapshot> get() = catalogRepository.snapshot
    lateinit var catalog: StateFlow<Catalog?>
        private set

    override fun onCreate() {
        super.onCreate()
        store = StateStore(File(filesDir, "state.json"))
        _state.value = store.load()
        update { it.withDeviceId { UUID.randomUUID().toString() } }
        backend = BackendFactory.create(this, http)
        catalogRepository = CatalogRepository(this, backend, { _state.value.installedRecipes }, scope)
        catalog = catalogRepository.snapshot.map<CatalogSnapshot, Catalog?> { it.catalog }
            .stateIn(scope, SharingStarted.Eagerly, catalogRepository.snapshot.value.catalog)
        // Spaces for recipes installed before the catalog was known get their names once it loads.
        scope.launch { catalogRepository.snapshot.collect { update { it } } }
        realtime = RealtimeWatcher(http, Realtime.socketUrl(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY), scope) {
            catalogRepository.refreshSoon()
        }
        UsageReporting.schedulePeriodic(this)
        // Feedback written offline (or interrupted) goes out once there's a connection.
        if (FeedbackRepository.thread(this).value?.pending().orEmpty().isNotEmpty()) FeedbackRepository.enqueue(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch { catalogRepository.refresh() }
                if (backend.supportsRealtime) realtime.start()
                if (reportDue(_state.value, System.currentTimeMillis())) UsageReporting.enqueueNow(this@HabitTagsApp)
            }

            override fun onStop(owner: LifecycleOwner) = realtime.stop()
        })
    }

    fun update(fn: (AppState) -> AppState): AppState = synchronized(this) {
        // Every installed recipe always has a space (name + colour), whichever screen changed the state.
        val next = fn(_state.value).let { s ->
            if (::catalogRepository.isInitialized) Spaces.ensure(s) { id -> catalogNow().recipes.firstOrNull { it.id == id } } else s
        }
        if (next != _state.value) {
            store.save(next)
            _state.value = next
        }
        next
    }

    fun catalogNow(): Catalog = catalogRepository.snapshot.value.catalog

    fun recipe(id: String): Recipe? = catalogNow().recipes.firstOrNull { it.id == id }?.takeIf(::isUsable)

    /** A running mode's name: its space ("Desk"), else the recipe title. */
    fun modeLabel(mode: ActiveMode): String = spaceName(mode.recipeId)

    fun spaceName(recipeId: String): String =
        _state.value.spaces[recipeId]?.name ?: catalogNow().recipes.firstOrNull { it.id == recipeId }?.let(Spaces::defaultName) ?: recipeId

    fun spaceColour(recipeId: String): SpaceColour = SpaceColour.of(_state.value.spaces[recipeId]?.colour)

    fun groups(): Map<String, Set<String>> {
        val overrides = _state.value.groupOverrides
        return catalogNow().appGroups.associate { it.id to (overrides[it.id] ?: it.androidPackages.toSet()) }
    }

    companion object {
        fun from(context: android.content.Context) = context.applicationContext as HabitTagsApp
    }
}
