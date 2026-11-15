package com.habittags.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habittags.app.HabitTagsApp
import com.habittags.app.Permissions
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.ParamSpec
import com.habittags.core.recipe.Recipe
import kotlin.math.roundToInt

fun Context.app() = HabitTagsApp.from(this)

fun formatMinutes(minutes: Int): String = when {
    minutes == 0 -> "Off"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Increments every time the screen resumes, so permission checks re-run after visiting Settings. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
fun ParamSlider(spec: ParamSpec, value: Int, onChange: (Int) -> Unit) {
    val step = if (spec.max - spec.min > 120) 15 else 5
    Column(Modifier.fillMaxWidth()) {
        Text("${spec.label}: ${formatMinutes(value)}")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(((it / step).roundToInt() * step).coerceIn(spec.min, spec.max)) },
            valueRange = spec.min.toFloat()..spec.max.toFloat(),
        )
    }
}

private data class Needed(val label: String, val granted: Boolean, val intent: (Context) -> Intent)

private fun neededPermissions(context: Context, recipe: Recipe): List<Needed> {
    val actions = recipe.rules.flatMap { it.actions }
    return buildList {
        if (actions.any { it is BlockGroups }) {
            add(Needed("Usage access (to see which app is open)", Permissions.hasUsageAccess(context)) { Permissions.usageAccessIntent() })
            add(Needed("Display over other apps (to cover blocked apps)", Permissions.hasOverlay(context)) { Permissions.overlayIntent(it) })
        }
        if (actions.any { it is Dnd }) {
            add(Needed("Do Not Disturb access", Permissions.hasDndAccess(context)) { Permissions.dndAccessIntent() })
        }
    }
}

fun permissionsGranted(context: Context, recipe: Recipe): Boolean = neededPermissions(context, recipe).all { it.granted }

/** Just-in-time permissions: only what this recipe's actions need. Returns true when all are granted. */
@Composable
fun PermissionCards(recipe: Recipe): Boolean {
    val context = LocalContext.current
    rememberResumeTick()
    val needed = neededPermissions(context, recipe)
    needed.filterNot { it.granted }.forEach { need ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(need.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { context.startActivity(need.intent(context)) }) { Text("Allow") }
            }
        }
    }
    return needed.all { it.granted }
}
