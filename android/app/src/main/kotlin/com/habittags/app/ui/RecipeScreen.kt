package com.habittags.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.ui.components.Panel
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.ScreenPadding
import com.habittags.app.ui.theme.SpaceColour
import com.habittags.app.ui.theme.toColor
import com.habittags.core.catalog.statLabel
import com.habittags.core.space.Spaces
import com.habittags.core.store.manualTag

@Composable
fun RecipeScreen(recipeId: String, onAdd: () -> Unit, onOpenSpace: () -> Unit) {
    val app = LocalContext.current.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val recipe = snapshot.catalog.recipes.firstOrNull { it.id == recipeId } ?: return
    val space = Spaces.defaultName(recipe)
    val colour = SpaceColour.of(recipe.space?.colour)
    val groupTitles = snapshot.catalog.appGroups.associate { it.id to it.title }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("For your ${space.lowercase()}", style = MaterialTheme.typography.titleMedium, color = colour.field.toColor())
        Text(recipe.title.substringAfter(": "), style = MaterialTheme.typography.displaySmall)
        Text(recipe.description, style = MaterialTheme.typography.bodyLarge)
        Panel {
            Text("What it does", style = MaterialTheme.typography.titleMedium)
            Text(whatItDoes(recipe, groupTitles), style = MaterialTheme.typography.bodyMedium)
            hoursText(recipe, state.manualTag(recipe.id, recipe.title).params)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Panel {
            Text("Where the tag goes", style = MaterialTheme.typography.titleMedium)
            Text(recipe.placementHint, style = MaterialTheme.typography.bodyMedium)
        }
        statLabel(snapshot.stats[recipe.id]).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (recipeId in state.installedRecipes) {
            PrimaryButton("Open ${app.spaceName(recipeId)}", onOpenSpace, Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Set up your ${space.lowercase()}", onAdd, Modifier.fillMaxWidth())
        }
    }
}
