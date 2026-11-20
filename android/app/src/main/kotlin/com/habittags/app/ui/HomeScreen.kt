package com.habittags.app.ui

import com.habittags.app.ui.components.runningLines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.habittags.core.tag.TipPlace
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.TapHandler
import com.habittags.app.ui.components.Note
import com.habittags.app.ui.components.Panel
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.ScanTarget
import com.habittags.app.ui.components.ScreenPadding
import com.habittags.app.ui.components.SecondaryButton
import com.habittags.app.ui.components.SpaceCard
import com.habittags.app.ui.components.rememberNow
import com.habittags.core.mode.TapResult
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.isUsable
import com.habittags.core.space.SpaceStatus
import com.habittags.core.space.spaceStatus
import kotlinx.datetime.TimeZone

/** Home: one card per space, running ones first, and the tag target at the bottom. */
@Composable
fun HomeScreen(onOpenSpace: (String) -> Unit, onOpenRunning: (String) -> Unit, onEndEarly: (String) -> Unit, onBrowse: () -> Unit) {
    val app = LocalContext.current.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val now = rememberNow()
    val tz = TimeZone.currentSystemDefault()
    val spaces = state.installedRecipes.mapNotNull { id -> snapshot.catalog.recipes.firstOrNull { it.id == id } }
        .map { it to spaceStatus(state, it, now, tz) }
        .sortedWith(compareBy({ it.second !is SpaceStatus.Running }, { app.spaceName(it.first.id) }))

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) {
                Text("Mise", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (spaces.isEmpty()) item {
            Panel {
                Text("Set up your first space", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "A space is a place in your home, like your desk or your bed, running a recipe. Tap its tag and your phone switches mode.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrimaryButton("Browse recipes", onBrowse)
            }
        }
        items(spaces, key = { it.first.id }) { (recipe, status) ->
            val running = status as? SpaceStatus.Running
            SpaceCard(
                spaceName = app.spaceName(recipe.id),
                recipeTitle = recipe.title.substringAfter(": "),
                colour = app.spaceColour(recipe.id),
                status = status,
                tagCount = state.tags.values.count { it.recipeId == recipe.id },
                onOpen = { onOpenSpace(recipe.id) },
                onOpenRunning = { running?.let { onOpenRunning(it.modeKey) } },
                onEndEarly = { running?.let { onEndEarly(it.modeKey) } },
                runningLines = running?.let { state.active[it.modeKey] }?.let { mode -> { now: Long -> app.runningLines(mode, now) } },
                startAction = {
                    if (!isUsable(recipe)) Note("Update the app to use this")
                    else StartNowButton(recipe, onNeedsPermissions = { onOpenSpace(recipe.id) })
                },
            )
        }
        item { SecureNfcTip(TipPlace.Home, hasTag = state.tags.isNotEmpty()) }
        if (spaces.isNotEmpty()) item {
            SecondaryButton("Add a space", onBrowse, Modifier.padding(top = 4.dp))
        }
        item { ScanTarget(Modifier.padding(top = 12.dp, bottom = 24.dp)) }
    }
}

/**
 * Starts (toggle) or runs (one-shot) the recipe without its tag, exactly like a tap. A recipe added
 * with "Skip" may lack its permissions, so those are asked for first ([onNeedsPermissions]).
 */
@Composable
fun StartNowButton(recipe: Recipe, enabled: Boolean = true, onNeedsPermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.app()
    var outside by remember { mutableStateOf<TapResult.OutsideHours?>(null) }
    val colour = app.spaceColour(recipe.id)
    PrimaryButton(
        if (recipe.mode == Mode.oneShot) "Run now" else "Start now",
        onClick = {
            if (!permissionsGranted(context, recipe)) onNeedsPermissions()
            else outside = TapHandler.startManually(context, recipe.id) as? TapResult.OutsideHours
        },
        enabled = enabled,
        container = androidx.compose.ui.graphics.Color(colour.field.toInt()),
        content = androidx.compose.ui.graphics.Color(colour.onField.toInt()),
    )
    outside?.let { result ->
        OutsideHoursDialog(
            app.spaceName(recipe.id), result.windows, napMinutes(context, recipe.id),
            onStart = {
                outside = null
                TapHandler.startManually(context, recipe.id, force = true)
            },
            onDismiss = { outside = null },
            dismissText = "Cancel",
        )
    }
}

fun tagCount(n: Int): String = com.habittags.app.ui.components.tagText(n)
