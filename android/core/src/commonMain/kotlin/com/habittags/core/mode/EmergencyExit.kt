package com.habittags.core.mode

import com.habittags.core.store.AppState
import com.habittags.core.usage.UsageEvent
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One no-typing exit per rolling 7 days, shared by every space on the phone. It ends a mode like the
 * typing gate (no end actions) and ignores a strict minimum.
 */
object EmergencyExit {
    const val PERIOD_MS = 7L * 24 * 60 * 60 * 1000

    fun available(lastUsedMs: Long, nowMs: Long): Boolean = lastUsedMs <= 0 || nowMs - lastUsedMs >= PERIOD_MS

    /** When the next exit becomes available, or null when one is available now. */
    fun nextAt(lastUsedMs: Long, nowMs: Long): Long? = if (available(lastUsedMs, nowMs)) null else lastUsedMs + PERIOD_MS

    /** Ends [modeKey] and records the exit (and a count-only usage event); null when none is available. */
    fun use(state: AppState, modeKey: String, now: Instant, tz: TimeZone): AppState? {
        val nowMs = now.toEpochMilliseconds()
        val mode = state.active[modeKey] ?: return null
        if (!available(state.lastEmergencyExitMs, nowMs)) return null
        val event = UsageEvent(
            type = "emergency_exit", v = 1, day = now.toLocalDateTime(tz).date.toString(), tz = tz.id, at = now.toString(),
            payload = buildJsonObject {
                put("recipe", JsonPrimitive(mode.recipeId))
                put("count", JsonPrimitive(1))
            },
        )
        return state.copy(
            active = ModeEngine.endViaGate(state.active, modeKey),
            lastEmergencyExitMs = nowMs,
            pendingEvents = state.pendingEvents + event,
        )
    }
}
