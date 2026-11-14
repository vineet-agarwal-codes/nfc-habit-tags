package com.habittags.core.mode

import com.habittags.core.fixtures.Fixtures.MIN
import com.habittags.core.fixtures.Fixtures.at
import com.habittags.core.fixtures.Fixtures.bed
import com.habittags.core.fixtures.Fixtures.desk
import com.habittags.core.fixtures.Fixtures.groups
import com.habittags.core.fixtures.Fixtures.gym
import com.habittags.core.fixtures.Fixtures.morning
import com.habittags.core.fixtures.Fixtures.ms
import com.habittags.core.fixtures.Fixtures.recipes
import com.habittags.core.fixtures.Fixtures.tag
import com.habittags.core.fixtures.Fixtures.zone
import com.habittags.core.recipe.Log
import com.habittags.core.recipe.OpenApp
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.Rule
import com.habittags.core.recipe.Timer
import com.habittags.core.recipe.When
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModeEngineTest {
    private val deskTag = tag("t1", desk)

    private fun tap(active: Map<String, ActiveMode>, tag: InstalledTag?, recipe: Recipe?, time: String) =
        ModeEngine.onTap(active, tag, recipe, at(time), zone, groups)

    private fun start(tag: InstalledTag = deskTag, time: String = "10:00", active: Map<String, ActiveMode> = emptyMap()) =
        assertIs<TapResult.Started>(tap(active, tag, recipes[tag.recipeId], time))

    @Test
    fun unknownTagAndMissingRecipe() {
        assertEquals(TapResult.UnknownTag, tap(emptyMap(), null, null, "10:00"))
        assertEquals(TapResult.RecipeUnavailable, tap(emptyMap(), deskTag, null, "10:00"))
    }

    @Test
    fun firstTapStartsWithDefaults() {
        val started = start()
        val mode = started.state.getValue("t1")
        assertEquals(ms("10:00") + 90 * MIN, mode.autoOffAt)
        assertEquals(null, mode.strictUntil)
        assertEquals(setOf("social", "video"), mode.blockedGroups)
        assertTrue(mode.dnd)
        assertEquals(emptyList(), started.effects)
    }

    @Test
    fun secondTapEnds() {
        val ended = assertIs<TapResult.Ended>(tap(start().state, deskTag, desk, "10:05"))
        assertEquals(emptyMap(), ended.state)
    }

    @Test
    fun strictMinimumRefusesThenAllowsEnd() {
        val strictTag = tag("t1", desk, mapOf("strictMinutes" to 45))
        val state = start(strictTag).state
        assertEquals(TapResult.StrictRefused(15 * MIN), tap(state, strictTag, desk, "10:30"))
        assertIs<TapResult.Ended>(tap(state, strictTag, desk, "10:46"))
        assertEquals(emptyMap(), ModeEngine.endViaGate(state, "t1"))
    }

    @Test
    fun expireRunsEndActionsAtAutoOff() {
        val state = start(tag("b1", bed)).state
        assertEquals(state to emptyList(), ModeEngine.expire(state, ms("10:00") + 89 * MIN, recipes::get))
        assertEquals(emptyMap<String, ActiveMode>() to listOf(morning), ModeEngine.expire(state, ms("10:00") + 90 * MIN, recipes::get))
    }

    @Test
    fun endingOneModeKeepsSharedGroupBlocked() {
        val both = start(tag("b1", bed), active = start().state).state
        assertEquals(setOf("insta", "fb", "yt"), ModeEngine.blockedPackages(both))
        val bedOnly = assertIs<TapResult.Ended>(tap(both, deskTag, desk, "10:10")).state
        assertEquals(setOf("insta", "fb"), ModeEngine.blockedPackages(bedOnly))
        assertTrue(ModeEngine.dndWanted(both))
        assertFalse(ModeEngine.dndWanted(bedOnly))
    }

    @Test
    fun bedTapEndReturnsEndActions() {
        val bedTag = tag("b1", bed)
        val ended = assertIs<TapResult.Ended>(tap(start(bedTag).state, bedTag, bed, "10:10"))
        assertEquals(listOf(morning), ended.effects)
    }

    @Test
    fun oneShotRunsEffectsOnlyAndIgnoresModeActions() {
        assertEquals(
            TapResult.Ran(listOf(OpenApp("com.fit"), Timer(JsonPrimitive(60)), Log("Workout"))),
            tap(emptyMap(), tag("g1", gym), gym, "10:00"),
        )
    }

    @Test
    fun noMatchingRuleRunsNothing() {
        val nightOnly = desk.copy(rules = listOf(desk.rules.single().copy(`when` = When("22:00-06:00"))))
        assertEquals(TapResult.Ran(emptyList()), tap(emptyMap(), deskTag, nightOnly, "10:00"))
    }

    @Test
    fun toggleRuleWithOnlyEffectsRunsWithoutStartingMode() {
        val logOnly = desk.copy(rules = listOf(Rule(actions = listOf(Log("day")))))
        assertEquals(TapResult.Ran(listOf(Log("day"))), tap(emptyMap(), deskTag, logOnly, "10:00"))
    }

    @Test
    fun runningModeKeepsAppsResolvedAtStart() {
        val started = start()
        assertEquals(setOf("insta", "fb", "yt"), started.state.getValue("t1").blockedPackages)
        // Editing groups later (e.g. removing "insta") must not weaken a running mode.
        assertEquals(setOf("insta", "fb", "yt"), ModeEngine.blockedPackages(started.state))
    }

    @Test
    fun tapAfterAutoOffStartsFreshInsteadOfEnding() {
        val state = start().state
        val restarted = assertIs<TapResult.Started>(tap(state, deskTag, desk, "11:31"))
        assertEquals(ms("11:31") + 90 * MIN, restarted.state.getValue("t1").autoOffAt)
    }
}
