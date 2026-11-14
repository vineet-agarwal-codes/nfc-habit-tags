package com.habittags.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.habittags.app.block.BlockController
import com.habittags.app.effects.EffectRunner
import com.habittags.app.ui.MainActivity
import com.habittags.core.mode.ModeEngine
import com.habittags.core.mode.TapResult
import com.habittags.core.store.TapLogEntry
import java.time.Instant
import java.time.ZoneId
import kotlinx.datetime.Clock
import java.time.format.DateTimeFormatter

private const val MAX_LOG = 500

object TapHandler {
    fun handle(context: Context, tagId: String): TapResult {
        val app = HabitTagsApp.from(context)
        val state = app.state.value
        val tag = state.tags[tagId]
        val recipe = tag?.let { app.recipe(it.recipeId) }
        val result = ModeEngine.onTap(state.active, tag, recipe, Clock.System.now())

        if (tag != null && result != TapResult.RecipeUnavailable) {
            val entry = TapLogEntry(System.currentTimeMillis(), tagId, tag.label)
            app.update { it.copy(tapLog = (it.tapLog + entry).takeLast(MAX_LOG)) }
        }
        val title = tag?.label.orEmpty()
        val message = when (result) {
            TapResult.UnknownTag -> {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SETUP_TAG_ID, tagId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                null
            }
            TapResult.RecipeUnavailable -> "This tag's recipe isn't available on this phone"
            is TapResult.Started -> {
                app.update { it.copy(active = result.state) }
                EffectRunner.run(context, result.effects, recipe, tag?.params.orEmpty())
                val off = result.state.getValue(tagId).autoOffAt
                if (off == null) "$title on" else "$title on · off at ${formatTime(off)}"
            }
            is TapResult.Ended -> {
                app.update { it.copy(active = result.state) }
                EffectRunner.run(context, result.effects, recipe, tag?.params.orEmpty())
                "$title off"
            }
            is TapResult.StrictRefused -> "Strict mode: ${minutesLeft(result.remainingMs)} min left"
            is TapResult.Ran -> {
                EffectRunner.run(context, result.effects, recipe, tag?.params.orEmpty())
                title
            }
        }
        BlockController.sync(context)
        message?.let { Toast.makeText(context.applicationContext, it, Toast.LENGTH_LONG).show() }
        return result
    }

    fun minutesLeft(ms: Long): Long = (ms + 59_999) / 60_000

    fun formatTime(epochMs: Long): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}
