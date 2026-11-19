package com.habittags.app.ui

import androidx.compose.foundation.background
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
import com.habittags.core.recipe.isUsable
import kotlinx.datetime.Clock

/** A tapped tag this phone doesn't know: pick what it should do (installed recipes first). */
@Composable
fun SetupTagScreen(onPick: (String) -> Unit) {
    val app = LocalContext.current.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val installed = snapshot.catalog.recipes.filter { it.id in state.installedRecipes && isUsable(it) }.sortedBy { it.title }
    val choices = installed + discoverOrder(snapshot, state.installedRecipes, Clock.System.now())

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = com.habittags.app.ui.components.ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            com.habittags.app.ui.components.ScreenTitle("Set up this tag")
            Text("This tag isn't set up on this phone yet. Pick the space it belongs to.", style = MaterialTheme.typography.bodyLarge)
        }
        items(choices, key = { it.id }) { recipe ->
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, com.habittags.app.ui.components.CardShape)
                    .clickable { onPick(recipe.id) }.padding(20.dp),
            ) {
                Text(
                    if (recipe.id in state.installedRecipes) app.spaceName(recipe.id) else recipe.title.substringAfter(": "),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(recipe.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        }
    }
}
