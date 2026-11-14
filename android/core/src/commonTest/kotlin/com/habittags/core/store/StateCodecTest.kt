package com.habittags.core.store

import com.habittags.core.fixtures.Fixtures.MIN
import com.habittags.core.fixtures.Fixtures.at
import com.habittags.core.fixtures.Fixtures.bed
import com.habittags.core.fixtures.Fixtures.ms
import com.habittags.core.fixtures.Fixtures.recipes
import com.habittags.core.fixtures.Fixtures.tag
import com.habittags.core.fixtures.Fixtures.zone
import com.habittags.core.mode.ModeEngine
import com.habittags.core.mode.TapResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StateCodecTest {
    private val sample = AppState(
        tags = mapOf("b1" to tag("b1", bed, mapOf("autoOffMinutes" to 60))),
        groupOverrides = mapOf("social" to setOf("com.android.settings")),
        tapLog = listOf(TapLogEntry(1L, "b1", "Bed")),
    )

    @Test
    fun roundTrip() {
        assertEquals(sample, StateCodec.decode(StateCodec.encode(sample)))
    }

    @Test
    fun missingOrCorruptDataGivesEmptyState() {
        for (text in listOf(null, "", "garbage", "{\"tags\": 5}")) assertEquals(AppState(), StateCodec.decode(text), "$text")
    }

    @Test
    fun expireAfterRestart() {
        val started = assertIs<TapResult.Started>(ModeEngine.onTap(emptyMap(), sample.tags.getValue("b1"), bed, at("10:00"), zone))
        val reloaded = StateCodec.decode(StateCodec.encode(sample.copy(active = started.state)))
        assertEquals(started.state, reloaded.active)
        assertEquals(emptyMap(), ModeEngine.expire(reloaded.active, ms("10:00") + 90 * MIN, recipes::get).first)
    }
}
