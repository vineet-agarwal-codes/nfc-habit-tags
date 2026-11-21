package com.habittags.core.mode

import com.habittags.core.recipe.Recipe
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Plain-words lines for a running mode, shared by the Home card and the running screen. */
object RunningText {
    /** "Nap is on" for a nap, else the recipe's own name: "Sleep mode is on", "Deep work is on". */
    fun title(recipe: Recipe?, mode: ActiveMode, spaceName: String): String {
        if (mode.nap) return "Nap is on"
        val name = recipe?.title?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: spaceName
        return sentenceCase(name) + " is on"
    }

    /** "12 h 41 min left", "41 min left", "35 s left". */
    fun remaining(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        if (s < 60) return "$s s left"
        val totalMin = (s + 59) / 60
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m min left"
            m == 0L -> "$h h left"
            else -> "$h h $m min left"
        }
    }

    /** "Apps lock in 4:12" while a mode waits out its lock delay. */
    fun locksIn(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return "Apps lock in ${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** "Ends at 05:00" today, "Ends tomorrow at 05:00" the next day, else "Ends Thu at 05:00". */
    fun ends(endMs: Long, nowMs: Long, zone: TimeZone): String {
        val end = Instant.fromEpochMilliseconds(endMs).toLocalDateTime(zone)
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date
        val time = "${end.hour.toString().padStart(2, '0')}:${end.minute.toString().padStart(2, '0')}"
        return when (end.date.toEpochDays() - today.toEpochDays()) {
            0 -> "Ends at $time"
            1 -> "Ends tomorrow at $time"
            else -> "Ends ${end.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} at $time"
        }
    }

    /** "Blocking social media, video and streaming · Do Not Disturb on"; null when nothing is paused. */
    fun paused(groupTitles: List<String>, dnd: Boolean): String? {
        val parts = buildList {
            if (groupTitles.isNotEmpty()) add("Blocking " + groupTitles.joinToString(", ") { it.lowercase() })
            if (dnd) add("Do Not Disturb on")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun sentenceCase(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }
}
