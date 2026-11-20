package com.habittags.core.fixtures

import com.habittags.core.mode.InstalledTag
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Checklist
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.EndPolicy
import com.habittags.core.recipe.Log
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.OpenApp
import com.habittags.core.recipe.ParamSpec
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.Rule
import com.habittags.core.recipe.Timer
import com.habittags.core.recipe.When
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

object Fixtures {
    val zone: TimeZone = TimeZone.of("Asia/Kolkata")
    fun at(hhmm: String): Instant = LocalDateTime(2026, 11, 14, hhmm.take(2).toInt(), hhmm.takeLast(2).toInt()).toInstant(zone)
    fun ms(hhmm: String): Long = at(hhmm).toEpochMilliseconds()
    const val MIN = 60_000L

    val desk = Recipe(
        id = "desk", schemaVersion = 1, version = 1, title = "Desk", description = "", placementHint = "",
        mode = Mode.toggle,
        params = listOf(
            ParamSpec("autoOffMinutes", "duration", "Auto-off", 90, 15, 480),
            ParamSpec("strictMinutes", "duration", "Strict", 0, 0, 240),
        ),
        autoOffMinutes = JsonPrimitive("{{autoOffMinutes}}"),
        strictMinutes = JsonPrimitive("{{strictMinutes}}"),
        rules = listOf(Rule(actions = listOf(BlockGroups(listOf("social", "video")), Dnd))),
    )

    val morning = Checklist("Good morning", listOf("Water"))

    val bed = Recipe(
        id = "bed", schemaVersion = 1, version = 1, title = "Bed", description = "", placementHint = "",
        mode = Mode.toggle,
        autoOffMinutes = JsonPrimitive(90),
        rules = listOf(Rule(actions = listOf(BlockGroups(listOf("social"))))),
        endActions = listOf(morning),
    )

    /** Bed with the wake-up end policy: allowed from usual bedtime − 3 h until wake-up; the tag can't end it. */
    val sleep = Recipe(
        id = "sleep", schemaVersion = 3, version = 1, title = "Sleep", description = "", placementHint = "",
        mode = Mode.toggle,
        params = listOf(
            ParamSpec("usualBedtime", "time", "Usual bedtime", 1380, 0, 1439),
            ParamSpec("wakeAt", "time", "Wake up at", 300, 0, 1439, renamedFrom = "sleepEnd"),
            ParamSpec("latestWake", "time", "Latest wake-up", 480, 0, 1439),
            ParamSpec("napMinutes", "duration", "Nap length", 60, 15, 240),
        ),
        endPolicy = EndPolicy(
            "wake", JsonPrimitive("{{wakeAt}}"), JsonPrimitive("{{usualBedtime}}"), JsonPrimitive("{{latestWake}}"),
            nap = JsonPrimitive("{{napMinutes}}"),
        ),
        tagCanEnd = false,
        rules = listOf(
            Rule(When("{{usualBedtime-180}}-{{wakeAt}}"), listOf(BlockGroups(listOf("social")), Dnd)),
            Rule(actions = listOf(Log("Bed tap outside night hours"))),
        ),
        endActions = listOf(morning),
    )

    val gym = Recipe(
        id = "gym", schemaVersion = 1, version = 1, title = "Gym", description = "", placementHint = "",
        mode = Mode.oneShot,
        rules = listOf(Rule(actions = listOf(OpenApp("com.fit"), Timer(JsonPrimitive(60)), Log("Workout"), BlockGroups(listOf("social")), Dnd))),
    )

    val recipes = listOf(desk, bed, sleep, gym).associateBy { it.id }

    fun tag(id: String, recipe: Recipe, params: Map<String, Int> = emptyMap()) = InstalledTag(id, recipe.id, recipe.title, params)

    val groups = mapOf("social" to setOf("insta", "fb"), "video" to setOf("yt"))
}
