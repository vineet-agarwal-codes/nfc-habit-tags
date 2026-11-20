package com.habittags.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.habittags.app.ui.components.CardShape
import com.habittags.app.ui.components.Note
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.QuietButton
import com.habittags.app.ui.components.ScreenPadding
import com.habittags.app.ui.components.ScreenTitle
import com.habittags.app.ui.components.SectionTitle
import com.habittags.app.ui.theme.SpaceColour
import com.habittags.app.ui.theme.toColor
import com.habittags.core.catalog.discoverSections
import com.habittags.core.catalog.statLabel
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.bindInt
import com.habittags.core.space.Spaces
import kotlinx.datetime.Clock

const val PRIVACY_URL = "https://vineet-agarwal-codes.github.io/privacy.html"

@Composable
fun DiscoverScreen(onOpen: (String) -> Unit, onAdd: (String) -> Unit) {
    val app = LocalContext.current.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val sections = discoverSections(snapshot, state.installedRecipes, Clock.System.now())
    val uri = LocalUriHandler.current

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("Recipes") }
        if (snapshot.stats.isEmpty() && snapshot.fetchedAtMs == 0L) item { Note("Showing saved recipes") }
        if (sections.isEmpty()) item {
            Text("You've added every recipe. More are coming soon.", style = MaterialTheme.typography.bodyLarge)
        }
        sections.forEach { (title, recipes) ->
            item(key = "section-$title") { SectionTitle(title) }
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(recipe, statLabel(snapshot.stats[recipe.id]), onOpen = { onOpen(recipe.id) }, onAdd = { onAdd(recipe.id) })
            }
        }
        item {
            QuietButton("Privacy policy", { uri.openUri(PRIVACY_URL) }, Modifier.padding(top = 16.dp, bottom = 24.dp))
        }
    }
}

/** What a recipe does in plain words, from its actions and default settings. */
fun durationText(recipe: Recipe): String? =
    runCatching { bindInt(recipe.autoOffMinutes, recipe.params, emptyMap()) }.getOrNull()?.takeIf { it > 0 }?.let(::formatMinutes)

@Composable
private fun RecipeCard(recipe: Recipe, stats: List<String>, onOpen: () -> Unit, onAdd: () -> Unit) {
    val colour = SpaceColour.of(recipe.space?.colour)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CardShape).clickable(onClick = onOpen).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(12.dp).background(colour.field.toColor(), CircleShape))
            Text(recipe.title.substringAfter(": "), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(recipe.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
        Text(
            (listOfNotNull(durationText(recipe)) + stats).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 8.dp)) { PrimaryButton("Set up your ${Spaces.defaultName(recipe).lowercase()}", onAdd) }
    }
}
