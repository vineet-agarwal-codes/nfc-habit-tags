package com.habittags.core.mode

import com.habittags.core.fixtures.Fixtures.MIN
import com.habittags.core.fixtures.Fixtures.at
import com.habittags.core.fixtures.Fixtures.bed
import com.habittags.core.fixtures.Fixtures.desk
import com.habittags.core.fixtures.Fixtures.groups
import com.habittags.core.fixtures.Fixtures.gym
import com.habittags.core.fixtures.Fixtures.morning
import com.habittags.core.fixtures.Fixtures.recipes
import com.habittags.core.fixtures.Fixtures.tag
import com.habittags.core.recipe.Log
import com.habittags.core.recipe.OpenApp
import com.habittags.core.recipe.Timer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModeEngineTest {
    private val deskTag = tag("t1", desk)

    private fun start(tag: InstalledTag = deskTag, time: String = "10:00", active: Map<String, ActiveMode> = emptyMap()) =
        ModeEngine.onTap(active, tag, recipes[tag.recipeId], at(time)) as TapResult.Started

    @Test
    fun unknownTagAndMissingRecipe() {
        assertEquals(TapResult.UnknownTag, ModeEngine.onTap(emptyMap(), null, null, at("10:00")))
        assertEquals(TapResult.RecipeUnavailable, ModeEngine.onTap(emptyMap(), deskTag, null, at("10:00")))
    }

    @Test
    fun firstTapStartsWithDefaults() {
        val started = start()
        val mode = started.state.getValue("t1")
        val t0 = at("10:00").toInstant().toEpochMilli()
        assertEquals(t0 + 90 * MIN, mode.autoOffAt)
        assertEquals(null, mode.strictUntil)
        assertEquals(setOf("social", "video"), mode.blockedGroups)
        assertTrue(mode.dnd)
        assertEquals(emptyList<Any>(), started.effects)
    }

    @Test
    fun secondTapEnds() {
        val ended = ModeEngine.onTap(start().state, deskTag, desk, at("10:05")) as TapResult.Ended
        assertEquals(emptyMap<String, ActiveMode>(), ended.state)
    }

    @Test
    fun strictMinimumRefusesThenAllowsEnd() {
        val strictTag = tag("t1", desk, mapOf("strictMinutes" to 45))
        val state = start(strictTag).state
        assertEquals(TapResult.StrictRefused(15 * MIN), ModeEngine.onTap(state, strictTag, desk, at("10:30")))
        assertTrue(ModeEngine.onTap(state, strictTag, desk, at("10:46")) is TapResult.Ended)
        assertEquals(emptyMap<String, ActiveMode>(), ModeEngine.endViaGate(state, "t1"))
    }

    @Test
    fun expireRunsEndActionsAtAutoOff() {
        val state = start(tag("b1", bed)).state
        val t0 = at("10:00").toInstant().toEpochMilli()
        assertEquals(state to emptyList<Any>(), ModeEngine.expire(state, t0 + 89 * MIN, recipes::get))
        assertEquals(emptyMap<String, ActiveMode>() to listOf(morning), ModeEngine.expire(state, t0 + 90 * MIN, recipes::get))
    }

    @Test
    fun endingOneModeKeepsSharedGroupBlocked() {
        val both = start(tag("b1", bed), active = start().state).state
        assertEquals(setOf("insta", "fb", "yt"), ModeEngine.blockedPackages(both, groups))
        val bedOnly = (ModeEngine.onTap(both, deskTag, desk, at("10:10")) as TapResult.Ended).state
        assertEquals(setOf("insta", "fb"), ModeEngine.blockedPackages(bedOnly, groups))
        assertTrue(ModeEngine.dndWanted(both))
        assertFalse(ModeEngine.dndWanted(bedOnly))
    }

    @Test
    fun bedTapEndReturnsEndActions() {
        val bedTag = tag("b1", bed)
        val ended = ModeEngine.onTap(start(bedTag).state, bedTag, bed, at("10:10")) as TapResult.Ended
        assertEquals(listOf(morning), ended.effects)
    }

    @Test
    fun oneShotRunsEffectsOnlyAndIgnoresModeActions() {
        val result = ModeEngine.onTap(emptyMap(), tag("g1", gym), gym, at("10:00"))
        assertEquals(TapResult.Ran(listOf(OpenApp("com.fit"), Timer(kotlinx.serialization.json.JsonPrimitive(60)), Log("Workout"))), result)
    }

    @Test
    fun noMatchingRuleRunsNothing() {
        val nightOnly = desk.copy(rules = listOf(desk.rules.single().copy(`when` = com.habittags.core.recipe.When("22:00-06:00"))))
        assertEquals(TapResult.Ran(emptyList()), ModeEngine.onTap(emptyMap(), deskTag, nightOnly, at("10:00")))
    }
}

class ToggleWithoutModeActionsTest {
    @Test
    fun toggleRuleWithOnlyEffectsRunsWithoutStartingMode() {
        val logOnly = desk.copy(rules = listOf(com.habittags.core.recipe.Rule(actions = listOf(Log("day")))))
        assertEquals(TapResult.Ran(listOf(Log("day"))), ModeEngine.onTap(emptyMap(), tag("t1", desk), logOnly, at("10:00")))
    }
}
