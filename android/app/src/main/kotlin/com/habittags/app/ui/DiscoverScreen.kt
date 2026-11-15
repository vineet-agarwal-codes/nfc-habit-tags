package com.habittags.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.core.catalog.discoverOrder
import com.habittags.core.catalog.statLabel
import kotlinx.datetime.Clock

@Composable
fun DiscoverScreen(onOpen: (String) -> Unit) {
    val app = LocalContext.current.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val recipes = discoverOrder(snapshot, state.installedRecipes, Clock.System.now())

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Discover", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
        if (recipes.isEmpty()) item { Text("You've installed every recipe — more coming soon.") }
        items(recipes, key = { it.id }) { recipe ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(recipe.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                    Text(recipe.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    statLabel(snapshot.stats[recipe.id]).forEach { Text(it, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}
