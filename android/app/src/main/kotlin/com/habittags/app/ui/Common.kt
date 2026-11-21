package com.habittags.app.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.habittags.app.TapHandler
import com.habittags.core.mode.ModeEngine
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.Dnd
import com.habittags.core.recipe.ParamSpec
import com.habittags.core.recipe.Recipe
import kotlin.math.roundToInt
import com.habittags.core.rules.formatMinutes as formatTimeOfDay

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

/** See [com.habittags.core.mode.hoursText]. */
fun hoursText(recipe: Recipe, userValues: Map<String, Int>): String? = com.habittags.core.mode.hoursText(recipe, userValues)

/** A time param (minutes since midnight) as a tappable row that opens a time picker; other params as sliders. */
@Composable
fun ParamControl(spec: ParamSpec, value: Int, onChange: (Int) -> Unit) {
    if (spec.type == "time") TimeParamRow(spec, value, onChange) else ParamSlider(spec, value, onChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeParamRow(spec: ParamSpec, value: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${spec.label}: ${formatTimeOfDay(value)}", Modifier.weight(1f))
            Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (picking) {
        val picker = rememberTimePickerState(value / 60, value % 60, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(spec.label) },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onChange((picker.hour * 60 + picker.minute).coerceIn(spec.min, spec.max))
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ParamSlider(spec: ParamSpec, value: Int, onChange: (Int) -> Unit) {
    val range = spec.max - spec.min
    val step = when {
        range > 120 -> 15
        range > 30 -> 5
        else -> 1
    }
    Column(Modifier.fillMaxWidth()) {
        Text("${spec.label}: ${if (value == 0 && spec.zeroLabel != null) spec.zeroLabel else formatMinutes(value)}")
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
            add(Needed("Usage access (to see which app is open)", Permissions.hasUsageAccess(context)) { Permissions.usageAccessIntent(it) })
            add(Needed("Display over other apps (to cover blocked apps)", Permissions.hasOverlay(context)) { Permissions.overlayIntent(it) })
        }
        if (actions.any { it is Dnd }) {
            add(Needed("Do Not Disturb access", Permissions.hasDndAccess(context)) { Permissions.dndAccessIntent(it) })
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

/** "Blocks social media and video apps, turns on Do Not Disturb, ends after 1 h 30 min." */
fun whatItDoes(recipe: Recipe, groupTitles: Map<String, String>, userValues: Map<String, Int> = emptyMap()): String {
    val actions = recipe.rules.flatMap { it.actions }
    val parts = buildList {
        val groups = actions.filterIsInstance<BlockGroups>().flatMap { it.groups }.distinct()
            .map { (groupTitles[it] ?: it).lowercase() }
        if (groups.isNotEmpty()) add("blocks ${groups.joinToString(" and ")} apps")
        if (actions.any { it is Dnd }) add("turns on Do Not Disturb")
        actions.filterIsInstance<com.habittags.core.recipe.Timer>().firstOrNull()?.let { t ->
            com.habittags.core.recipe.bindInt(t.minutes, recipe.params, userValues)?.let { add("starts a ${formatMinutes(it)} timer") }
        }
        if (actions.any { it is com.habittags.core.recipe.Checklist } || recipe.endActions.any { it is com.habittags.core.recipe.Checklist }) add("shows a checklist")
        runCatching { com.habittags.core.recipe.bindInt(recipe.autoOffMinutes, recipe.params, userValues) }.getOrNull()
            ?.takeIf { it > 0 && recipe.mode == Mode.toggle }?.let { add("ends after ${formatMinutes(it)}") }
    }
    if (parts.isEmpty()) return recipe.description
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
}
