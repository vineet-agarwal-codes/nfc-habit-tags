package com.habittags.core.mode

import com.habittags.core.fixtures.Fixtures.at
import com.habittags.core.fixtures.Fixtures.desk
import com.habittags.core.fixtures.Fixtures.groups
import com.habittags.core.fixtures.Fixtures.sleep
import com.habittags.core.fixtures.Fixtures.tag
import com.habittags.core.fixtures.Fixtures.zone
import com.habittags.core.store.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunningAndEmergencyTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun emergencyExitOncePerSevenDays() {
        val now = 100 * day
        // lastUsed, available
        val cases = listOf(0L to true, now - 7 * day to true, now - 7 * day + 1 to false, now - day to false)
        for ((last, expected) in cases) {
            assertEquals(expected, EmergencyExit.available(last, now), "last=$last")
            assertEquals(if (expected) null else last + 7 * day, EmergencyExit.nextAt(last, now), "last=$last")
        }
    }

    @Test
    fun emergencyExitEndsTheModeIgnoringStrictAndRecordsIt() {
        val t = tag("t1", desk, mapOf("strictMinutes" to 60))
        val started = assertIs<TapResult.Started>(ModeEngine.onTap(emptyMap(), t, desk, at("10:00"), zone, groups))
        val state = AppState(active = started.state)
        val used = EmergencyExit.use(state, "t1", at("10:05"), zone)!!
        assertTrue(used.active.isEmpty())
        assertEquals(at("10:05").toEpochMilliseconds(), used.lastEmergencyExitMs)
        assertEquals("emergency_exit", used.pendingEvents.single().type)
        // A second one the same week is refused and changes nothing.
        val again = assertIs<TapResult.Started>(ModeEngine.onTap(emptyMap(), t, desk, at("11:00"), zone, groups))
        assertNull(EmergencyExit.use(used.copy(active = again.state), "t1", at("11:05"), zone))
        // Unknown mode: nothing to end.
        assertNull(EmergencyExit.use(state, "nope", at("10:05"), zone))
    }

    @Test
    fun napIsFlagged() {
        val nap = assertIs<TapResult.Started>(ModeEngine.startManually(emptyMap(), tag("s1", sleep), sleep, at("15:10"), zone, groups, force = true))
        assertTrue(nap.state.getValue("s1").nap)
        val night = assertIs<TapResult.Started>(ModeEngine.onTap(emptyMap(), tag("s1", sleep), sleep, at("23:00"), zone, groups))
        assertFalse(night.state.getValue("s1").nap)
        assertEquals("Nap is on", RunningText.title(sleep, nap.state.getValue("s1"), "Bed"))
        assertEquals("Sleep is on", RunningText.title(sleep, night.state.getValue("s1"), "Bed"))
    }

    @Test
    fun runningLines() {
        val min = 60_000L
        val remaining = listOf(
            (12 * 60 + 41) * min + 46_000 to "12 h 42 min left",
            12 * 60 * min to "12 h left",
            41 * min to "41 min left",
            35_000L to "35 s left",
            0L to "0 s left",
        )
        for ((ms, text) in remaining) assertEquals(text, RunningText.remaining(ms), "$ms")
        val now = at("16:18").toEpochMilliseconds()
        assertEquals("Ends at 17:18", RunningText.ends(now + 60 * min, now, zone))
        assertEquals("Ends tomorrow at 05:00", RunningText.ends(at("05:00").toEpochMilliseconds() + day, now, zone))
        assertEquals("Blocking social media, video and streaming · Do Not Disturb on", RunningText.paused(listOf("Social media", "Video and streaming"), true))
        val locks = listOf(252_000L to "Apps lock in 4:12", 59_000L to "Apps lock in 0:59", 600_000L to "Apps lock in 10:00", 0L to "Apps lock in 0:00")
        for ((ms, text) in locks) assertEquals(text, RunningText.locksIn(ms), "$ms")
        assertEquals("Do Not Disturb on", RunningText.paused(emptyList(), true))
        assertNull(RunningText.paused(emptyList(), false))
    }
}
