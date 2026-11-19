package com.habittags.core.space

import com.habittags.core.fixtures.Fixtures.bed
import com.habittags.core.fixtures.Fixtures.ms
import com.habittags.core.fixtures.Fixtures.zone
import com.habittags.core.fixtures.Fixtures.desk
import com.habittags.core.fixtures.Fixtures.gym
import com.habittags.core.recipe.SpaceHint
import com.habittags.core.store.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SpacesTest {
    @Test
    fun defaultNames() {
        val cases = listOf(
            desk.copy(title = "Desk: Deep Work") to "Desk",
            desk.copy(title = "Deep Work", space = SpaceHint("Bed")) to "Bed",
            desk.copy(title = "Morning") to "Morning",
            desk.copy(title = "Morning", space = SpaceHint("  ")) to "Morning",
        )
        for ((recipe, expected) in cases) assertEquals(expected, Spaces.defaultName(recipe), recipe.title)
    }

    @Test
    fun nextColour() {
        val all = Spaces.COLOURS
        val cases = listOf(
            Triple(emptyList<String>(), null, "tomato"),
            Triple(listOf("tomato"), "tomato", "deep_blue"),
            Triple(listOf("tomato"), "green", "green"),
            Triple(emptyList(), "not-a-colour", "tomato"),
            Triple(all, "tomato", "tomato"), // seventh wraps around
            Triple(all + "tomato", null, "deep_blue"),
        )
        for ((used, hint, expected) in cases) assertEquals(expected, Spaces.nextColour(used, hint), "$used $hint")
    }

    @Test
    fun ensureGivesEveryInstalledRecipeADistinctSpace() {
        val recipes = mapOf(
            "desk" to desk.copy(title = "Desk: Deep Work", space = SpaceHint("Desk", "tomato")),
            "bed" to bed.copy(title = "Bed: Sleep", space = SpaceHint("Bed", "tomato")),
        )
        val s = Spaces.ensure(AppState(installedRecipes = setOf("desk", "bed")), recipes::get)
        assertEquals(Space("bed", "Bed", "tomato"), s.spaces["bed"]) // sorted ids: bed claims its hint first
        assertEquals(Space("desk", "Desk", "deep_blue"), s.spaces["desk"])
        // Uninstalling drops its space; unknown recipes wait for the catalog.
        val after = Spaces.ensure(s.copy(installedRecipes = setOf("desk", "later")), recipes::get)
        assertEquals(setOf("desk"), after.spaces.keys)
        // Already complete: the same state comes back.
        assertSame(after, Spaces.ensure(after, recipes::get))
    }

    @Test
    fun renameAndRecolour() {
        val s = Spaces.ensure(AppState(installedRecipes = setOf("gym")), mapOf("gym" to gym)::get)
        assertEquals("Gym bag", Spaces.rename(s, gym, "  Gym bag ").spaces.getValue("gym").name)
        assertEquals("Gym", Spaces.rename(s, gym, " ").spaces.getValue("gym").name)
        assertEquals("plum", Spaces.recolour(s, "gym", "plum").spaces.getValue("gym").colour)
        assertSame(s, Spaces.recolour(s, "gym", "purple"))
    }

    @Test
    fun statusPerCard() {
        val night = bed.copy(rules = listOf(com.habittags.core.recipe.Rule(com.habittags.core.recipe.When("21:00-05:00"), bed.rules.single().actions)))
        val running = com.habittags.core.mode.ActiveMode("t1", "desk", ms("10:00"), ms("11:30"), null, emptySet(), false, reaches = 4)
        val log = listOf(com.habittags.core.store.TapLogEntry(ms("09:00"), "t1", "Desk", "desk", "Asia/Kolkata"))
        val s = AppState(active = mapOf("t1" to running), tapLog = log)
        val cases = listOf(
            Triple(desk, "10:30", SpaceStatus.Running("t1", ms("11:30"), null, 4)),
            Triple(desk, "11:31", SpaceStatus.Idle(ms("09:00"))), // auto-off passed
            Triple(night, "18:00", SpaceStatus.StartsAt("21:00")),
            Triple(night, "22:00", SpaceStatus.Idle(null)),
            Triple(gym, "18:00", SpaceStatus.TapToRun),
        )
        for ((recipe, time, expected) in cases) assertEquals(expected, spaceStatus(s, recipe, ms(time), zone), "${recipe.id} $time")
    }
}
