package com.habittags.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.block.BlockController
import com.habittags.core.mode.InstalledTag
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.isUsable
import com.habittags.core.tag.TagPayload

/** Settings for an installed recipe; slider changes apply to every tag of this recipe on the next tap. */
@Composable
fun RecipeEditScreen(recipeId: String, onOpenTag: (String) -> Unit, onInstallFlow: () -> Unit, onUninstalled: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val recipe = snapshot.catalog.recipes.firstOrNull { it.id == recipeId } ?: return
    val tags = state.tags.values.filter { it.recipeId == recipeId }.sortedBy { it.label }
    val running = state.active.values.any { it.recipeId == recipeId }
    var writingTagId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
        if (snapshot.meta[recipeId]?.retired == true) Text("No longer offered", style = MaterialTheme.typography.labelLarge)
        if (running) Text("On now", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        else if (isUsable(recipe)) StartNowButton(recipe, enabled = PermissionCards(recipe))
        val current = tags.firstOrNull()?.params ?: state.recipeParams[recipeId].orEmpty()
        recipe.params.forEach { spec ->
            ParamSlider(spec, (current[spec.key] ?: spec.default).coerceIn(spec.min, spec.max)) { v ->
                app.update { s ->
                    s.copy(
                        tags = s.tags.mapValues { (_, t) -> if (t.recipeId == recipeId) t.copy(params = t.params + (spec.key to v)) else t },
                        recipeParams = s.recipeParams + (recipeId to (s.recipeParams[recipeId].orEmpty() + (spec.key to v))),
                    )
                }
            }
        }
        if (recipe.params.isNotEmpty()) Text("Changes apply the next time it starts. Tags don't need rewriting.", style = MaterialTheme.typography.bodySmall)

        val groups = recipe.rules.flatMap { it.actions }.filterIsInstance<BlockGroups>().flatMap { it.groups }.distinct()
        if (groups.isNotEmpty()) {
            Text("Apps to block", style = MaterialTheme.typography.titleMedium)
            groups.forEach { GroupEditor(it) }
        }

        Text("Tags", style = MaterialTheme.typography.titleMedium)
        Text(tagCount(tags.size), style = MaterialTheme.typography.bodyMedium)
        tags.forEach { tag ->
            Card(Modifier.fillMaxWidth().clickable { onOpenTag(tag.tagId) }) {
                Text(tag.label + if (tag.tagId in state.active) " · on" else "", Modifier.padding(16.dp))
            }
        }
        Button(
            onClick = { if (tags.isEmpty()) onInstallFlow() else writingTagId = TagPayload.newTagId() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (tags.isEmpty()) "Write to a tag" else "Write another tag") }

        if (running) Text("Turn this mode off first", color = MaterialTheme.colorScheme.error)
        OutlinedButton(enabled = !running, modifier = Modifier.fillMaxWidth(), onClick = {
            app.update { s ->
                s.copy(
                    installedRecipes = s.installedRecipes - recipeId,
                    tags = s.tags.filterValues { it.recipeId != recipeId },
                    recipeParams = s.recipeParams - recipeId,
                )
            }
            BlockController.sync(context)
            onUninstalled()
        }) { Text("Uninstall") }
    }

    writingTagId?.let { id ->
        TagWriteDialog(id, onWritten = {
            val params = tags.firstOrNull()?.params ?: state.recipeParams[recipeId].orEmpty()
            app.update { s -> s.copy(tags = s.tags + (id to InstalledTag(id, recipeId, "${recipe.title} ${tags.size + 1}", params))) }
            writingTagId = null
        }, onCancel = { writingTagId = null })
    }
}
