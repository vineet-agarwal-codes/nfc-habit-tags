package com.habittags.app.ui

import com.habittags.core.store.manualTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.core.catalog.statLabel

@Composable
fun RecipeScreen(recipeId: String, onInstall: () -> Unit, onEdit: () -> Unit) {
    val app = LocalContext.current.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val recipe = snapshot.catalog.recipes.firstOrNull { it.id == recipeId } ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
        Text(recipe.description)
        Text("Where to put it: ${recipe.placementHint}", style = MaterialTheme.typography.bodyMedium)
        hoursText(recipe, state.manualTag(recipe.id, recipe.title).params)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        statLabel(snapshot.stats[recipe.id]).forEach { Text(it, style = MaterialTheme.typography.labelLarge) }
        if (recipeId in state.installedRecipes) {
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit") }
        } else {
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Install") }
        }
    }
}
