package com.habittags.core.usage

import com.habittags.core.store.AppState
import com.habittags.core.store.TapLogEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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

const val REPORT_INTERVAL_MS = 6 * 60 * 60 * 1000L
const val MAX_REPORT_DAYS = 14

/**
 * The events to send now: anything queued, a snapshot of installed recipes, and per-day tap and
 * blocked-app reach counts (counts only; never which app).
 * Each tap is bucketed into its local day in the zone recorded when it happened (or [tz] for
 * entries saved before zones were recorded). Days already partly reported are re-sent in full, so
 * re-sending is idempotent on the server.
 */
fun buildReport(state: AppState, now: Instant, tz: TimeZone): List<UsageEvent> {
    val snapshot = UsageEvent(
        type = "installed_snapshot", v = 1, day = now.toLocalDateTime(tz).date.toString(), tz = tz.id,
        payload = buildJsonObject { put("recipes", JsonArray(state.installedRecipes.sorted().map(::JsonPrimitive))) },
    )
    val lastReport = state.lastReportMs.takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds)
    val taps = dailyCounts("tap_count", state.tapLog, lastReport, now, tz)
    val reaches = dailyCounts("block_reach", state.reachLog, lastReport, now, tz)
    return state.pendingEvents + snapshot + taps + reaches
}

/**
 * One [type] event per (recipe, local day): the server keys usage on that pair, so entries on the same
 * date in two zones are summed, and the event carries the zone of the day's latest entry.
 */
private fun dailyCounts(type: String, log: List<TapLogEntry>, lastReport: Instant?, now: Instant, tz: TimeZone): List<UsageEvent> {
    data class DayCount(val count: Int, val latestMs: Long, val zone: String)
    val counts = mutableMapOf<Pair<String, LocalDate>, DayCount>()
    for (entry in log) {
        val recipe = entry.recipeId ?: continue
        val zone = entry.zone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: tz
        val day = Instant.fromEpochMilliseconds(entry.atMs).toLocalDateTime(zone).date
        val oldest = now.toLocalDateTime(zone).date.minus(MAX_REPORT_DAYS - 1, DateTimeUnit.DAY)
        val cutoff = lastReport?.toLocalDateTime(zone)?.date?.let { maxOf(it, oldest) } ?: oldest
        if (day < cutoff) continue
        val prev = counts[recipe to day]
        counts[recipe to day] = DayCount(
            count = (prev?.count ?: 0) + 1,
            latestMs = maxOf(prev?.latestMs ?: Long.MIN_VALUE, entry.atMs),
            zone = if (prev == null || entry.atMs >= prev.latestMs) zone.id else prev.zone,
        )
    }
    return counts.entries
        .sortedWith(compareBy({ it.key.second }, { it.key.first }))
        .map { (key, value) ->
            UsageEvent(
                type = type, v = 1, day = key.second.toString(), tz = value.zone,
                payload = buildJsonObject {
                    put("recipe", JsonPrimitive(key.first))
                    put("count", JsonPrimitive(value.count))
                },
            )
        }
}

fun reportDue(state: AppState, nowMs: Long): Boolean = nowMs - state.lastReportMs >= REPORT_INTERVAL_MS

/** Clears only the queued events that were in the sent report; anything queued since stays. */
fun AppState.afterReportSent(nowMs: Long, sent: List<UsageEvent>): AppState {
    val remaining = pendingEvents.toMutableList()
    sent.forEach { remaining.remove(it) }
    return copy(lastReportMs = nowMs, pendingEvents = remaining)
}
