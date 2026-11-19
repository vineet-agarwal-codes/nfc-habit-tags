package com.habittags.app.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.habittags.app.block.BlockController
import com.habittags.app.ui.components.CardShape
import com.habittags.app.ui.components.Note
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.QuietButton
import com.habittags.app.ui.components.ScreenPadding
import com.habittags.app.ui.components.SecondaryButton
import com.habittags.app.ui.components.SectionTitle
import com.habittags.app.ui.theme.SpaceColour
import com.habittags.app.ui.theme.toColor
import com.habittags.core.mode.InstalledTag
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.recipe.isUsable
import com.habittags.core.space.Spaces
import com.habittags.core.tag.TagPayload

/** A space's settings: name, colour, recipe settings, apps, tags, and removing it. */
@Composable
fun SpaceSettingsScreen(recipeId: String, onOpenTag: (String) -> Unit, onAddFlow: () -> Unit, onRemoved: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val snapshot by app.snapshot.collectAsState()
    val state by app.state.collectAsState()
    val recipe = snapshot.catalog.recipes.firstOrNull { it.id == recipeId } ?: return
    val space = state.spaces[recipeId]
    val tags = state.tags.values.filter { it.recipeId == recipeId }.sortedBy { it.label }
    val running = state.active.values.any { it.recipeId == recipeId }
    var writingTagId by remember { mutableStateOf<String?>(null) }
    var name by remember(space?.name) { mutableStateOf(space?.name ?: Spaces.defaultName(recipe)) }
    val colour = SpaceColour.of(space?.colour)
    val uri = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(name.ifBlank { Spaces.defaultName(recipe) }, style = MaterialTheme.typography.displaySmall, color = colour.field.toColor())
        Text(recipe.title.substringAfter(": "), style = MaterialTheme.typography.titleMedium)
        if (snapshot.meta[recipeId]?.retired == true) Note("This recipe is no longer offered. It keeps working here.")
        if (running) Text("$name is on now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        else if (isUsable(recipe)) {
            val ready = PermissionCards(recipe)
            StartNowButton(recipe, enabled = ready)
        }

        SectionTitle("Name")
        OutlinedTextField(
            name, { name = it; app.update { s -> Spaces.rename(s, recipe, it) } },
            Modifier.fillMaxWidth(), singleLine = true,
        )
        SectionTitle("Colour")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpaceColour.entries.forEach { c ->
                val selected = c == colour
                Box(
                    Modifier.size(40.dp)
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                        .padding(5.dp)
                        .background(c.field.toColor(), CircleShape)
                        .clickable { app.update { s -> Spaces.recolour(s, recipeId, c.id) } },
                )
            }
        }

        val current = tags.firstOrNull()?.params ?: state.recipeParams[recipeId].orEmpty()
        if (recipe.params.isNotEmpty()) {
            SectionTitle("Settings")
            hoursText(recipe, current)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            recipe.params.forEach { spec ->
                ParamControl(spec, (current[spec.key] ?: spec.default).coerceIn(spec.min, spec.max)) { v ->
                    app.update { s ->
                        s.copy(
                            tags = s.tags.mapValues { (_, t) -> if (t.recipeId == recipeId) t.copy(params = t.params + (spec.key to v)) else t },
                            recipeParams = s.recipeParams + (recipeId to (s.recipeParams[recipeId].orEmpty() + (spec.key to v))),
                        )
                    }
                }
            }
            Note("Changes apply the next time it starts. Tags don't need rewriting.")
        }

        val groups = recipe.rules.flatMap { it.actions }.filterIsInstance<BlockGroups>().flatMap { it.groups }.distinct()
        if (groups.isNotEmpty()) {
            SectionTitle("Apps to block")
            groups.forEach { GroupEditor(it) }
        }

        SectionTitle("Tags")
        Note(tagCount(tags.size))
        tags.forEach { tag ->
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CardShape).clickable { onOpenTag(tag.tagId) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tag.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (tag.tagId in state.active) Text("On", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        SecondaryButton(
            if (tags.isEmpty()) "Write a tag" else "Write another tag",
            { if (tags.isEmpty()) onAddFlow() else writingTagId = TagPayload.newTagId() },
        )

        SectionTitle("About")
        Note("Device ID for data requests: ${state.deviceId.orEmpty()}")
        QuietButton("Privacy policy", { uri.openUri(PRIVACY_URL) })

        if (running) Note("End $name before removing it.")
        QuietButton("Remove $name", {
            if (running) return@QuietButton
            app.update { s ->
                s.copy(
                    installedRecipes = s.installedRecipes - recipeId,
                    tags = s.tags.filterValues { it.recipeId != recipeId },
                    recipeParams = s.recipeParams - recipeId,
                )
            }
            BlockController.sync(context)
            onRemoved()
        }, color = MaterialTheme.colorScheme.primary)
    }

    writingTagId?.let { id ->
        TagWriteDialog(id, onWritten = {
            val params = tags.firstOrNull()?.params ?: state.recipeParams[recipeId].orEmpty()
            app.update { s -> s.copy(tags = s.tags + (id to InstalledTag(id, recipeId, "$name ${tags.size + 1}", params))) }
            writingTagId = null
        }, onCancel = { writingTagId = null })
    }
}
