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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.core.recipe.isUsable

@Composable
fun CatalogScreen(setupTagId: String?, onPick: (String) -> Unit) {
    val catalog by LocalContext.current.app().catalog.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(if (setupTagId != null) "Set up this tag" else "Choose a recipe", style = MaterialTheme.typography.headlineSmall)
            if (setupTagId != null) Text("This tag isn't set up on this phone yet. Pick what it should do.")
        }
        items(catalog?.recipes.orEmpty(), key = { it.id }) { recipe ->
            val usable = isUsable(recipe)
            Card(
                Modifier.fillMaxWidth().alpha(if (usable) 1f else 0.5f).clickable(enabled = usable) { onPick(recipe.id) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                    Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
                    if (!usable) Text("Update the app to use this", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
