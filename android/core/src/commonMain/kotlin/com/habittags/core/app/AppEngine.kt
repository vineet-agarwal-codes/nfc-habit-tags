package com.habittags.core.app

import com.habittags.core.catalog.CatalogSnapshot
import com.habittags.core.recipe.paramValue
import com.habittags.core.catalog.decodeRemoteCatalog
import com.habittags.core.catalog.discoverOrder
import com.habittags.core.catalog.mergeCatalog
import com.habittags.core.catalog.statLabel
import com.habittags.core.catalog.withoutInvalidRecipes
import com.habittags.core.gate.FrictionGate
import com.habittags.core.mode.InstalledTag
import com.habittags.core.mode.ModeEngine
import com.habittags.core.mode.hoursText
import com.habittags.core.mode.TapResult
import com.habittags.core.recipe.Action
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.CatalogJson
import com.habittags.core.recipe.Checklist
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.Log
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.OpenApp
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.Timer
import com.habittags.core.recipe.bindInt
import com.habittags.core.recipe.isUsable
import com.habittags.core.recipe.parseCatalog
import com.habittags.core.rules.formatMinutes
import com.habittags.core.store.AppState
import com.habittags.core.store.StateCodec
import com.habittags.core.store.TapLogEntry
import com.habittags.core.store.manualTag
import com.habittags.core.store.withDeviceId
import com.habittags.core.tag.TagPayload
import com.habittags.core.usage.UsageEvent
import com.habittags.core.usage.afterReportSent
import com.habittags.core.usage.buildReport
import com.habittags.core.usage.reportDue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random

/** Something the platform has to do after a tap: show a checklist, schedule a timer, open a link. */
data class EffectView(val kind: String, val title: String, val items: List<String>, val minutes: Int, val uri: String)

/**
 * The result of a tap or a manual start. [kind] is one of unknownTag, unavailable, alreadyOn, started,
 * ended, strictRefused, ran, outsideHours. [autoOffAtMs] is 0 when the mode has no auto-off.
 */
data class TapOutcome(
    val kind: String,
    val message: String,
    val title: String,
    val modeKey: String,
    val autoOffAtMs: Long,
    val windowsText: String,
    val effects: List<EffectView>,
)

data class ParamView(val key: String, val label: String, val type: String, val value: Int, val min: Int, val max: Int, val step: Int, val valueText: String)

data class RecipeCard(
    val id: String,
    val title: String,
    val summary: String,
    val placementHint: String,
    val stats: List<String>,
    val hours: String,
    val oneShot: Boolean,
    val usable: Boolean,
    val retired: Boolean,
    val blocksApps: Boolean,
    val usesDnd: Boolean,
    val tagCount: Int,
    val isOn: Boolean,
)

data class TagView(val tagId: String, val label: String, val isOn: Boolean)

data class ActiveView(val key: String, val title: String, val endsText: String, val strictText: String, val autoOffAtMs: Long)

/**
 * The whole app's behaviour behind plain values, for platforms that only draw screens and talk to
 * the OS (the iPhone app). Every call that changes [state] returns after updating it; the platform
 * persists [stateJson] after each change.
 */
class AppEngine(stateJson: String?, bundledCatalogJson: String, cachedSnapshotJson: String?, newDeviceId: String) {
    var state: AppState = (stateJson?.let(StateCodec::decodeOrNull) ?: AppState()).withDeviceId { newDeviceId }
        private set
    var snapshot: CatalogSnapshot = cachedSnapshotJson
        ?.let { runCatching { CatalogJson.decodeFromString(CatalogSnapshot.serializer(), it).withoutInvalidRecipes() }.getOrNull() }
        ?: CatalogSnapshot(parseCatalog(bundledCatalogJson)).withoutInvalidRecipes()
        private set
    private var inFlightReport: List<UsageEvent> = emptyList()

    fun stateJson(): String = StateCodec.encode(state)
    fun snapshotJson(): String = CatalogJson.encodeToString(CatalogSnapshot.serializer(), snapshot)
    fun deviceId(): String = state.deviceId.orEmpty()

    // --- Catalog -------------------------------------------------------------------------------

    /** False (catalog unchanged) when the responses can't be read; Kotlin exceptions must not reach Swift. */
    fun applyRemote(recipesJson: String, groupsJson: String, statsJson: String?, nowMs: Long): Boolean = runCatching {
        snapshot = mergeCatalog(snapshot, decodeRemoteCatalog(recipesJson, groupsJson, statsJson, nowMs), state.installedRecipes)
    }.isSuccess

    fun discover(nowMs: Long, tzId: String): List<RecipeCard> =
        discoverOrder(snapshot, state.installedRecipes, Instant.fromEpochMilliseconds(nowMs)).map { card(it, tzId) }

    fun myRecipes(tzId: String): List<RecipeCard> =
        state.installedRecipes.mapNotNull(::anyRecipe).sortedBy { it.title }.map { card(it, tzId) }

    fun recipeCard(id: String, tzId: String): RecipeCard? = anyRecipe(id)?.let { card(it, tzId) }

    fun isInstalled(id: String): Boolean = id in state.installedRecipes

    fun params(recipeId: String): List<ParamView> {
        val recipe = anyRecipe(recipeId) ?: return emptyList()
        val current = currentParams(recipeId)
        return recipe.params.map { spec ->
            val value = paramValue(spec, current)
            val time = spec.type == "time"
            ParamView(
                spec.key, spec.label, spec.type, value, spec.min, spec.max,
                step = if (time) 15 else if (spec.max - spec.min > 120) 15 else 5,
                valueText = if (time) formatMinutes(value) else durationText(value),
            )
        }
    }

    fun tags(recipeId: String): List<TagView> =
        state.tags.values.filter { it.recipeId == recipeId }.sortedBy { it.label }.map { TagView(it.tagId, it.label, it.tagId in state.active) }

    fun isKnownTag(tagId: String): Boolean = tagId in state.tags

    fun activeModes(nowMs: Long, tzId: String): List<ActiveView> = state.active.entries.sortedBy { it.value.startedAt }.map { (key, mode) ->
        val title = state.tags[mode.tagId]?.label ?: anyRecipe(mode.recipeId)?.title ?: mode.recipeId
        val strictLeft = mode.strictUntil?.let { it - nowMs }?.takeIf { it > 0 }
        ActiveView(
            key, title,
            endsText = mode.autoOffAt?.let { "ends ${clock(it, tzId)}" }.orEmpty(),
            strictText = strictLeft?.let { "strict · ${minutesLeft(it)} min left" }.orEmpty(),
            autoOffAtMs = mode.autoOffAt ?: 0,
        )
    }

    // --- Installing and editing ----------------------------------------------------------------

    fun install(recipeId: String) {
        val recipe = recipe(recipeId) ?: return
        state = state.copy(
            installedRecipes = state.installedRecipes + recipeId,
            recipeParams = state.recipeParams + (recipeId to (state.recipeParams[recipeId] ?: recipe.params.associate { it.key to it.default })),
        )
    }

    /** Applies to every tag of the recipe and to starts from the app; takes effect on the next start. */
    fun setParam(recipeId: String, key: String, value: Int) {
        val spec = anyRecipe(recipeId)?.params?.firstOrNull { it.key == key } ?: return
        val v = value.coerceIn(spec.min, spec.max)
        state = state.copy(
            tags = state.tags.mapValues { (_, t) -> if (t.recipeId == recipeId) t.copy(params = t.params + (key to v)) else t },
            recipeParams = state.recipeParams + (recipeId to (state.recipeParams[recipeId].orEmpty() + (key to v))),
        )
    }

    /** Links a written (or scanned) tag to a recipe, installing it if needed. */
    fun linkTag(tagId: String, recipeId: String) {
        val recipe = anyRecipe(recipeId) ?: return
        install(recipeId)
        val count = state.tags.values.count { it.recipeId == recipeId }
        val label = if (count == 0) recipe.title else "${recipe.title} ${count + 1}"
        state = state.copy(tags = state.tags + (tagId to InstalledTag(tagId, recipeId, label, currentParams(recipeId))))
    }

    fun removeTag(tagId: String) {
        if (tagId in state.active) return
        state = state.copy(tags = state.tags - tagId)
    }

    fun isRunning(recipeId: String): Boolean = state.active.values.any { it.recipeId == recipeId }

    /** False while the recipe's mode is on. */
    fun uninstall(recipeId: String): Boolean {
        if (isRunning(recipeId)) return false
        state = state.copy(
            installedRecipes = state.installedRecipes - recipeId,
            tags = state.tags.filterValues { it.recipeId != recipeId },
            recipeParams = state.recipeParams - recipeId,
        )
        return true
    }

    // --- Taps, starts, ends --------------------------------------------------------------------

    fun tap(tagId: String, nowMs: Long, tzId: String): TapOutcome {
        expireInternal(nowMs)
        val tag = state.tags[tagId]
        val recipe = tag?.let { recipe(it.recipeId) }
        val now = Instant.fromEpochMilliseconds(nowMs)
        val result = ModeEngine.onTap(state.active, tag, recipe, now, zone(tzId))
        if (tag == null) return TapOutcome("unknownTag", "", "", tagId, 0, "", emptyList())
        return apply(tag, recipe, ModeEngine.modeKey(state.active, tag, nowMs), result, "tag", nowMs, tzId)
    }

    fun startManually(recipeId: String, force: Boolean, nowMs: Long, tzId: String): TapOutcome {
        expireInternal(nowMs)
        val recipe = recipe(recipeId)
        val tag = state.manualTag(recipeId, recipe?.title ?: recipeId)
        val result = ModeEngine.startManually(state.active, tag, recipe, Instant.fromEpochMilliseconds(nowMs), zone(tzId), force = force)
        return apply(tag, recipe, tag.tagId, result, "app", nowMs, tzId)
    }

    /** Ends modes whose auto-off has passed; returns their end actions for the platform to run. */
    fun expire(nowMs: Long): List<EffectView> = expireInternal(nowMs)

    fun endViaGate(modeKey: String) {
        state = state.copy(active = ModeEngine.endViaGate(state.active, modeKey))
    }

    // --- Typing gate ---------------------------------------------------------------------------

    fun gatePassage(words: Int): String = FrictionGate.passage(Random.Default, words)

    // --- Tags ----------------------------------------------------------------------------------

    fun createTagId(): String = TagPayload.newTagId()
    fun tagUrl(tagId: String): String = TagPayload.urlFor(tagId)
    fun tagIdFromUrl(url: String): String? = TagPayload.parseUrl(url)
    fun tagIdFromText(text: String): String? = TagPayload.parseText(text)

    // --- Usage reports -------------------------------------------------------------------------

    fun reportDue(nowMs: Long): Boolean = reportDue(state, nowMs)

    /** The JSON body for the report_events RPC; call [reportSent] once the server accepted it. */
    fun buildReportBody(nowMs: Long, tzId: String): String {
        inFlightReport = buildReport(state, Instant.fromEpochMilliseconds(nowMs), zone(tzId))
        return buildJsonObject {
            put("device_id", JsonPrimitive(deviceId()))
            put("events", Json.encodeToJsonElement(ListSerializer(UsageEvent.serializer()), inFlightReport))
        }.toString()
    }

    fun reportSent(nowMs: Long) {
        state = state.afterReportSent(nowMs, inFlightReport)
        inFlightReport = emptyList()
    }

    // --- Internals -----------------------------------------------------------------------------

    private fun apply(tag: InstalledTag, recipe: Recipe?, key: String, result: TapResult, source: String, nowMs: Long, tzId: String): TapOutcome {
        if (result != TapResult.RecipeUnavailable && result != TapResult.AlreadyOn) {
            log(TapLogEntry(nowMs, tag.tagId, tag.label, tag.recipeId, tzId, source))
        }
        val title = tag.label
        fun outcome(kind: String, message: String, effects: List<Action> = emptyList(), autoOff: Long = 0, windows: String = "") =
            TapOutcome(kind, message, title, key, autoOff, windows, effectViews(effects, recipe, tag.params, nowMs))
        return when (result) {
            TapResult.UnknownTag -> outcome("unknownTag", "")
            TapResult.RecipeUnavailable -> outcome("unavailable", "This recipe isn't available on this phone")
            TapResult.AlreadyOn -> outcome("alreadyOn", "$title is already on")
            is TapResult.Started -> {
                val mode = result.state.getValue(tag.tagId)
                state = state.copy(active = state.active + (key to mode))
                val off = mode.autoOffAt
                outcome("started", if (off == null) "$title on" else "$title on · off at ${clock(off, tzId)}", result.effects, off ?: 0)
            }
            is TapResult.Ended -> {
                state = state.copy(active = state.active - key)
                outcome("ended", "$title off", result.effects)
            }
            is TapResult.StrictRefused -> outcome("strictRefused", "Strict mode: ${minutesLeft(result.remainingMs)} min left")
            is TapResult.TagCantEnd -> outcome("tagCantEnd", tagCantEndMessage(title, result.endsAt?.let { clock(it, tzId) }))
            is TapResult.Ran -> outcome("ran", title, result.effects)
            is TapResult.OutsideHours -> {
                val windows = describeWindows(result.windows)
                outcome(
                    "outsideHours",
                    "${recipe?.title ?: title} only turns on $windows. It's ${clock(nowMs, tzId)} now, so nothing was started.",
                    result.effects,
                    windows = windows,
                )
            }
        }
    }

    private fun expireInternal(nowMs: Long): List<EffectView> {
        val (remaining, effects) = ModeEngine.expire(state.active, nowMs, ::anyRecipe)
        if (remaining.size == state.active.size) return emptyList()
        val ended = state.active.values.filter { it.tagId !in remaining.keys }
        state = state.copy(active = remaining)
        val recipe = ended.firstOrNull()?.let { anyRecipe(it.recipeId) }
        return effectViews(effects, recipe, ended.firstOrNull()?.let { currentParams(it.recipeId) }.orEmpty(), nowMs)
    }

    /** Log actions are handled here; the rest go to the platform. */
    private fun effectViews(effects: List<Action>, recipe: Recipe?, params: Map<String, Int>, nowMs: Long): List<EffectView> = effects.mapNotNull { e ->
        when (e) {
            is Checklist -> EffectView("checklist", e.title, e.items, 0, "")
            is Timer -> bindInt(e.minutes, recipe?.params.orEmpty(), params)?.let { EffectView("timer", recipe?.title ?: "Timer", emptyList(), it, "") }
            is OpenApp -> e.uri?.let { EffectView("openUri", "", emptyList(), 0, it) }
            is Log -> { log(TapLogEntry(nowMs, "", e.label)); null }
            else -> null
        }
    }

    private fun log(entry: TapLogEntry) {
        state = state.copy(tapLog = (state.tapLog + entry).takeLast(MAX_LOG))
    }

    private fun card(r: Recipe, tzId: String): RecipeCard {
        val actions = r.rules.flatMap { it.actions } + r.endActions
        return RecipeCard(
            id = r.id, title = r.title, summary = r.description, placementHint = r.placementHint,
            stats = statLabel(snapshot.stats[r.id]),
            hours = hoursText(r, currentParams(r.id)).orEmpty(),
            oneShot = r.mode == Mode.oneShot,
            usable = isUsable(r),
            retired = snapshot.meta[r.id]?.retired == true,
            blocksApps = actions.any { it is BlockGroups },
            usesDnd = actions.any { it is Dnd },
            tagCount = state.tags.values.count { it.recipeId == r.id },
            isOn = isRunning(r.id),
        )
    }

    private fun anyRecipe(id: String): Recipe? = snapshot.catalog.recipes.firstOrNull { it.id == id }
    private fun recipe(id: String): Recipe? = anyRecipe(id)?.takeIf(::isUsable)
    private fun currentParams(recipeId: String): Map<String, Int> =
        state.tags.values.firstOrNull { it.recipeId == recipeId }?.params ?: state.recipeParams[recipeId].orEmpty()

    private fun zone(tzId: String): TimeZone = runCatching { TimeZone.of(tzId) }.getOrDefault(TimeZone.UTC)

    private fun clock(epochMs: Long, tzId: String): String {
        val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone(tzId)).time
        return formatMinutes(t.hour * 60 + t.minute)
    }

    companion object {
        private const val MAX_LOG = 500

        /** False for a state file that exists but can't be read; the platform moves it aside instead of overwriting it. */
        fun isReadableState(text: String): Boolean = StateCodec.decodeOrNull(text) != null

        /** "Desk is on until 11:30. To stop early, use End early in the app." */
        fun tagCantEndMessage(title: String, endsAt: String?): String =
            (if (endsAt != null) "$title is on until $endsAt." else "$title is on.") + " To stop early, use End early in the app."

        fun describeWindows(windows: List<String>): String =
            windows.joinToString(" or ") { w -> w.split("-").let { "between ${it[0]} and ${it[1]}" } }

        fun minutesLeft(ms: Long): Long = (ms + 59_999) / 60_000

        fun durationText(minutes: Int): String = when {
            minutes == 0 -> "Off"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }
}
