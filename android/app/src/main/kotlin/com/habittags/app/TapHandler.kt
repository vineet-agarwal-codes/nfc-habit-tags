package com.habittags.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.habittags.app.block.BlockController
import com.habittags.app.block.ModeExpiry
import com.habittags.app.effects.EffectRunner
import com.habittags.app.ui.MainActivity
import com.habittags.core.mode.InstalledTag
import com.habittags.core.mode.ModeEngine
import com.habittags.core.mode.TapResult
import com.habittags.core.recipe.Recipe
import com.habittags.core.store.TapLogEntry
import com.habittags.core.store.manualTag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

private const val MAX_LOG = 500

object TapHandler {
    fun handle(context: Context, tagId: String): TapResult {
        val app = HabitTagsApp.from(context)
        ModeExpiry.expireNow(context)
        val state = app.state.value
        val tag = state.tags[tagId]
        val recipe = tag?.let { app.recipe(it.recipeId) }
        val now = Clock.System.now()
        val result = ModeEngine.onTap(state.active, tag, recipe, now, groups = app.groups())
        if (tag == null) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SETUP_TAG_ID, tagId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            return result
        }
        apply(context, tag, recipe, ModeEngine.modeKey(state.active, tag, now.toEpochMilliseconds()), result, "tag")
        return result
    }

    /** Starts (or runs) an installed recipe without its tag, exactly as a tap on its first tag would. */
    fun startManually(context: Context, recipeId: String): TapResult {
        val app = HabitTagsApp.from(context)
        ModeExpiry.expireNow(context)
        val state = app.state.value
        val recipe = app.recipe(recipeId)
        val tag = state.manualTag(recipeId, recipe?.title ?: recipeId)
        val result = ModeEngine.startManually(state.active, tag, recipe, Clock.System.now(), groups = app.groups())
        apply(context, tag, recipe, tag.tagId, result, "app")
        return result
    }

    private fun apply(context: Context, tag: InstalledTag, recipe: Recipe?, key: String, result: TapResult, source: String) {
        val app = HabitTagsApp.from(context)
        if (result != TapResult.RecipeUnavailable && result != TapResult.AlreadyOn) {
            val entry = TapLogEntry(System.currentTimeMillis(), tag.tagId, tag.label, tag.recipeId, TimeZone.currentSystemDefault().id, source)
            app.update { it.copy(tapLog = (it.tapLog + entry).takeLast(MAX_LOG)) }
        }
        val title = tag.label
        val message = when (result) {
            TapResult.UnknownTag -> null
            TapResult.RecipeUnavailable -> "This recipe isn't available on this phone"
            TapResult.AlreadyOn -> "$title is already on"
            is TapResult.Started -> {
                val mode = result.state.getValue(key)
                app.update { it.copy(active = it.active + (key to mode)) }
                EffectRunner.run(context, result.effects, recipe, tag.params)
                val off = mode.autoOffAt
                if (off == null) "$title on" else "$title on · off at ${formatTime(off)}"
            }
            is TapResult.Ended -> {
                app.update { it.copy(active = it.active - key) }
                EffectRunner.run(context, result.effects, recipe, tag.params)
                "$title off"
            }
            is TapResult.StrictRefused -> "Strict mode: ${minutesLeft(result.remainingMs)} min left"
            is TapResult.Ran -> {
                EffectRunner.run(context, result.effects, recipe, tag.params)
                title
            }
            is TapResult.OutsideHours -> {
                EffectRunner.run(context, result.effects, recipe, tag.params)
                // Start now shows this in a dialog instead (see StartNowButton).
                if (source == "app") null else outsideHoursMessage(recipe?.title ?: title, result.windows)
            }
        }
        BlockController.sync(context)
        message?.let { Toast.makeText(context.applicationContext, it, Toast.LENGTH_LONG).show() }
    }

    fun outsideHoursMessage(title: String, windows: List<String>): String {
        val ranges = windows.joinToString(" or ") { w -> w.split("-").let { "between ${it[0]} and ${it[1]}" } }
        val now = DateTimeFormatter.ofPattern("HH:mm").format(java.time.LocalTime.now())
        return "$title only turns on $ranges. It's $now now, so nothing was started."
    }

    fun minutesLeft(ms: Long): Long = (ms + 59_999) / 60_000

    fun formatTime(epochMs: Long): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}
