package com.habittags.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.habittags.app.HabitTagsApp
import com.habittags.app.TapHandler
import com.habittags.core.recipe.bindInt
import com.habittags.core.store.manualTag
import com.habittags.app.ui.theme.MiseTheme

/**
 * Shown over whatever is on screen when a tag is tapped outside its recipe's hours: the user just
 * tapped, so they can choose to start it anyway right there instead of opening the app.
 */
class OutsideHoursActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipeId = intent.getStringExtra(EXTRA_RECIPE) ?: return finish()
        val space = intent.getStringExtra(EXTRA_SPACE).orEmpty()
        val windows = intent.getStringArrayListExtra(EXTRA_WINDOWS).orEmpty()
        setContent {
            MiseTheme {
                OutsideHoursDialog(
                    space, windows, napMinutes(this, recipeId),
                    onStart = {
                        TapHandler.startManually(this, recipeId, force = true)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_RECIPE = "recipe"
        private const val EXTRA_SPACE = "space"
        private const val EXTRA_WINDOWS = "windows"

        fun open(context: Context, recipeId: String, space: String, windows: List<String>) {
            context.startActivity(
                Intent(context, OutsideHoursActivity::class.java)
                    .putExtra(EXTRA_RECIPE, recipeId)
                    .putExtra(EXTRA_SPACE, space)
                    .putStringArrayListExtra(EXTRA_WINDOWS, ArrayList(windows))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * "Bed normally starts from 20:00. It's 15:10 now." with Start anyway / [dismissText]. For a recipe
 * whose out-of-hours start is a nap ([napMinutes] set), it offers "Start a nap" instead.
 */
@Composable
fun OutsideHoursDialog(
    space: String,
    windows: List<String>,
    napMinutes: Int?,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "Not now",
) {
    val usual = "$space normally ${TapHandler.startsText(windows)}. It's ${TapHandler.nowHhMm()} now."
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (napMinutes != null) "Take a nap?" else "Outside its usual hours") },
        text = { Text(if (napMinutes != null) "$usual A nap ends after ${durationWords(napMinutes)}." else usual) },
        confirmButton = { TextButton(onClick = onStart) { Text(if (napMinutes != null) "Start a nap" else "Start anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}

/** The nap length the user set for this recipe, or null when an out-of-hours start isn't a nap. */
fun napMinutes(context: Context, recipeId: String): Int? {
    val app = HabitTagsApp.from(context)
    val recipe = app.recipe(recipeId) ?: return null
    val nap = recipe.endPolicy?.nap ?: return null
    val params = app.state.value.manualTag(recipeId, recipe.title).params
    return runCatching { bindInt(nap, recipe.params, params) }.getOrNull()
}

private fun durationWords(minutes: Int): String = when {
    minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes minutes"
}
