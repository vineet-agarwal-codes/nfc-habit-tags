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
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.Log
import com.habittags.core.recipe.ParamSpec
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
        assertEquals(TapResult.OutsideHours(listOf("22:00-06:00"), emptyList()), tap(emptyMap(), deskTag, nightOnly, "10:00"))
    }

    @Test
    fun daytimeTapOnNightRecipeSaysWhenItRuns() {
        val bedLike = desk.copy(
            rules = listOf(
                desk.rules.single().copy(`when` = When("21:00-05:00")),
                Rule(actions = listOf(Log("day"))),
            ),
        )
        assertEquals(TapResult.OutsideHours(listOf("21:00-05:00"), listOf(Log("day"))), tap(emptyMap(), deskTag, bedLike, "15:10"))
        assertIs<TapResult.Started>(tap(emptyMap(), deskTag, bedLike, "21:00"))
        assertEquals(
            TapResult.OutsideHours(listOf("21:00-05:00"), listOf(Log("day"))),
            ModeEngine.startManually(emptyMap(), deskTag, bedLike, at("15:10"), zone, groups),
        )
    }

    @Test
    fun toggleRuleWithOnlyEffectsRunsWithoutStartingMode() {
        val logOnly = desk.copy(rules = listOf(Rule(actions = listOf(Log("day")))))
        assertEquals(TapResult.Ran(listOf(Log("day"))), tap(emptyMap(), deskTag, logOnly, "10:00"))
    }

    @Test
    fun userTimeWindowsDecideWhenNightRecipesStart() {
        val night = nightBed()
        val cases = listOf(
            // user values, time, starts?
            Triple(emptyMap<String, Int>(), "21:00", true),
            Triple(emptyMap<String, Int>(), "15:10", false),
            Triple(mapOf("sleepStart" to 900), "15:10", true), // 15:00-05:00
            Triple(mapOf("sleepEnd" to 1080), "15:10", true), // 21:00-18:00 wraps, covers the afternoon
            Triple(mapOf("sleepStart" to 1380), "22:00", false), // 23:00-05:00
        )
        for ((values, time, starts) in cases) {
            val result = tap(emptyMap(), tag("b1", night, values), night, time)
            assertEquals(starts, result is TapResult.Started, "$values at $time: $result")
        }
        val outside = assertIs<TapResult.OutsideHours>(tap(emptyMap(), tag("b1", night, mapOf("sleepStart" to 1380, "sleepEnd" to 420)), night, "15:10"))
        assertEquals(listOf("23:00-07:00"), outside.windows)
    }

    @Test
    fun startAnywayForcesTheModeRuleOutsideHours() {
        val night = nightBed()
        val bedTag = tag("b1", night)
        val forced = assertIs<TapResult.Started>(ModeEngine.startManually(emptyMap(), bedTag, night, at("15:10"), zone, groups, force = true))
        val mode = forced.state.getValue("b1")
        assertEquals(setOf("social"), mode.blockedGroups)
        assertTrue(mode.dnd)
        assertEquals(ms("15:10") + 480 * MIN, mode.autoOffAt)
        assertEquals(emptyList(), forced.effects)
        // Without force the same start explains the hours instead.
        assertIs<TapResult.OutsideHours>(ModeEngine.startManually(emptyMap(), bedTag, night, at("15:10"), zone, groups))
        // Forcing inside hours is just a normal start.
        assertIs<TapResult.Started>(ModeEngine.startManually(emptyMap(), bedTag, night, at("22:00"), zone, groups, force = true))
        // A one-shot or windowless recipe is unaffected by force.
        assertEquals(tap(emptyMap(), tag("g", gym), gym, "15:10"), ModeEngine.startManually(emptyMap(), tag("g", gym), gym, at("15:10"), zone, groups, force = true))
    }

    private fun nightBed() = bed.copy(
        schemaVersion = 2,
        params = listOf(
            ParamSpec("autoOffMinutes", "duration", "Auto-off", 480, 240, 720),
            ParamSpec("sleepStart", "time", "Sleep from", 1260, 0, 1439),
            ParamSpec("sleepEnd", "time", "until", 300, 0, 1439),
        ),
        autoOffMinutes = JsonPrimitive("{{autoOffMinutes}}"),
        rules = listOf(
            Rule(When("{{sleepStart}}-{{sleepEnd}}"), listOf(BlockGroups(listOf("social")), Dnd)),
            Rule(actions = listOf(Log("day"))),
        ),
    )

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

    @Test
    fun manualStartOfTaglessRecipeUsesAppKey() {
        val manual = InstalledTag("app:desk", "desk", "Desk", mapOf("autoOffMinutes" to 30))
        val started = assertIs<TapResult.Started>(ModeEngine.startManually(emptyMap(), manual, desk, at("10:00"), zone, groups))
        val mode = started.state.getValue("app:desk")
        assertEquals(ms("10:00") + 30 * MIN, mode.autoOffAt)
        assertEquals(setOf("insta", "fb", "yt"), mode.blockedPackages)
    }

    @Test
    fun manualStartWhileOnIsRefused() {
        val state = start().state
        val manual = InstalledTag("app:desk", "desk", "Desk", emptyMap())
        assertEquals(TapResult.AlreadyOn, ModeEngine.startManually(state, manual, desk, at("10:05"), zone, groups))
        assertEquals(TapResult.AlreadyOn, ModeEngine.startManually(state, deskTag, desk, at("10:05"), zone, groups))
        // After auto-off the recipe is no longer on, so it can start again.
        assertIs<TapResult.Started>(ModeEngine.startManually(state, deskTag, desk, at("11:31"), zone, groups))
    }

    @Test
    fun manualOneShotRuns() {
        assertIs<TapResult.Ran>(ModeEngine.startManually(emptyMap(), tag("g1", gym), gym, at("10:00"), zone, groups))
    }

    @Test
    fun tapActsOnRecipesRunningMode() {
        val strict = InstalledTag("app:desk", "desk", "Desk", mapOf("strictMinutes" to 45))
        val appOn = assertIs<TapResult.Started>(ModeEngine.startManually(emptyMap(), strict, desk, at("10:00"), zone, groups)).state
        // cases: (active, tapped tag, time) -> expected key and result
        assertEquals("app:desk", ModeEngine.modeKey(appOn, deskTag, ms("10:10")))
        assertEquals(TapResult.StrictRefused(35 * MIN), tap(appOn, deskTag, desk, "10:10"))
        assertEquals(emptyMap(), assertIs<TapResult.Ended>(tap(appOn, deskTag, desk, "10:46")).state)

        val t1On = start().state
        val t2 = tag("t2", desk)
        assertEquals("t1", ModeEngine.modeKey(t1On, t2, ms("10:05")))
        assertEquals(emptyMap(), assertIs<TapResult.Ended>(tap(t1On, t2, desk, "10:05")).state)
        // An expired mode doesn't capture taps: t2 starts its own.
        assertEquals("t2", ModeEngine.modeKey(t1On, t2, ms("11:31")))
        assertIs<TapResult.Started>(tap(t1On, t2, desk, "11:31"))
    }

    @Test
    fun reachCountsEveryModeBlockingTheApp() {
        val both = start(tag("b1", bed), active = start().state).state
        val cases = listOf(
            "insta" to mapOf("t1" to 1, "b1" to 1), // social: in both
            "yt" to mapOf("t1" to 1, "b1" to 0),    // video: desk only
            "maps" to mapOf("t1" to 0, "b1" to 0),
        )
        for ((pkg, expected) in cases) {
            assertEquals(expected, ModeEngine.recordReach(both, pkg).mapValues { it.value.reaches }, pkg)
        }
    }
}
