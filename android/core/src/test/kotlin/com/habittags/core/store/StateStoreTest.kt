package com.habittags.core.store

import com.habittags.core.fixtures.Fixtures.MIN
import com.habittags.core.fixtures.Fixtures.at
import com.habittags.core.fixtures.Fixtures.bed
import com.habittags.core.fixtures.Fixtures.recipes
import com.habittags.core.fixtures.Fixtures.tag
import com.habittags.core.mode.ModeEngine
import com.habittags.core.mode.TapResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StateStoreTest {
    @TempDir lateinit var dir: File

    private val sample = AppState(
        tags = mapOf("b1" to tag("b1", bed, mapOf("autoOffMinutes" to 60))),
        groupOverrides = mapOf("social" to setOf("com.android.settings")),
        tapLog = listOf(TapLogEntry(1L, "b1", "Bed")),
    )

    @Test
    fun roundTrip() {
        val store = StateStore(File(dir, "state.json"))
        store.save(sample)
        assertEquals(sample, store.load())
    }

    @Test
    fun missingOrCorruptFileGivesEmptyState() {
        assertEquals(AppState(), StateStore(File(dir, "none.json")).load())
        val bad = File(dir, "bad.json").apply { writeText("garbage") }
        assertEquals(AppState(), StateStore(bad).load())
    }

    @Test
    fun expireAfterRestart() {
        val bedTag = sample.tags.getValue("b1")
        val started = ModeEngine.onTap(emptyMap(), bedTag, bed, at("10:00")) as TapResult.Started
        StateStore(File(dir, "state.json")).save(sample.copy(active = started.state))

        val reloaded = StateStore(File(dir, "state.json")).load()
        val t0 = at("10:00").toInstant().toEpochMilli()
        assertEquals(started.state, reloaded.active)
        assertEquals(emptyMap<String, Any>(), ModeEngine.expire(reloaded.active, t0 + 90 * MIN, recipes::get).first)
    }
}
