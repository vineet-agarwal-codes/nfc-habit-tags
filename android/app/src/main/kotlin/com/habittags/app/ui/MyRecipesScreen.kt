package com.habittags.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.TapHandler
import com.habittags.core.recipe.Mode
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.isUsable

@Composable
fun MyRecipesScreen(onOpen: (String) -> Unit, onBrowse: () -> Unit) {
    val app = LocalContext.current.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val installed = state.installedRecipes.mapNotNull { id -> snapshot.catalog.recipes.firstOrNull { it.id == id } }.sortedBy { it.title }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("My recipes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
        if (installed.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nothing installed yet")
                Button(onClick = onBrowse) { Text("Browse recipes") }
            }
        }
        items(installed, key = { it.id }) { recipe ->
            val tags = state.tags.values.filter { it.recipeId == recipe.id }
            val on = state.active.values.any { it.recipeId == recipe.id }
            Card(Modifier.fillMaxWidth().clickable { onOpen(recipe.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(recipe.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (on) Text("On", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(tagCount(tags.size), style = MaterialTheme.typography.bodyMedium)
                    if (!isUsable(recipe)) Text("Update the app to use this", style = MaterialTheme.typography.labelLarge)
                    if (snapshot.meta[recipe.id]?.retired == true) Text("No longer offered", style = MaterialTheme.typography.labelLarge)
                    if (!on && isUsable(recipe)) StartNowButton(recipe)
                }
            }
        }
    }
}

fun tagCount(n: Int): String = when (n) {
    0 -> "No tag yet"
    1 -> "1 tag"
    else -> "$n tags"
}

/** Starts (toggle) or runs (one-shot) the recipe without its tag, exactly like a tap. */
@Composable
fun StartNowButton(recipe: Recipe) {
    val context = LocalContext.current
    OutlinedButton(onClick = { TapHandler.startManually(context, recipe.id) }) {
        Text(if (recipe.mode == Mode.oneShot) "Run now" else "Start now")
    }
}
