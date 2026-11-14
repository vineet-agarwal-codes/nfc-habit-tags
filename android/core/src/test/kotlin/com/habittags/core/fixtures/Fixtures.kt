package com.habittags.core.fixtures

import com.habittags.core.mode.InstalledTag
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Checklist
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.Log
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.OpenApp
import com.habittags.core.recipe.ParamSpec
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.Rule
import com.habittags.core.recipe.Timer
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneId
import java.time.ZonedDateTime

object Fixtures {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    fun at(hhmm: String): ZonedDateTime = ZonedDateTime.of(2026, 11, 14, hhmm.take(2).toInt(), hhmm.takeLast(2).toInt(), 0, 0, zone)
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

    val gym = Recipe(
        id = "gym", schemaVersion = 1, version = 1, title = "Gym", description = "", placementHint = "",
        mode = Mode.oneShot,
        rules = listOf(Rule(actions = listOf(OpenApp("com.fit"), Timer(JsonPrimitive(60)), Log("Workout"), BlockGroups(listOf("social")), Dnd))),
    )

    val recipes = listOf(desk, bed, gym).associateBy { it.id }

    fun tag(id: String, recipe: Recipe, params: Map<String, Int> = emptyMap()) = InstalledTag(id, recipe.id, recipe.title, params)

    val groups = mapOf("social" to setOf("insta", "fb"), "video" to setOf("yt"))
}
