package com.habittags.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.habittags.core.catalog.mergeParams
import com.habittags.core.recipe.isUsable

@Composable
fun TagDetailScreen(tagId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    val catalog by app.catalog.collectAsState()
    val tag = state.tags[tagId] ?: return
    val recipe = app.recipe(tag.recipeId)
    var rewriting by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }

    fun edit(fn: (com.habittags.core.mode.InstalledTag) -> com.habittags.core.mode.InstalledTag) =
        app.update { s -> s.tags[tagId]?.let { s.copy(tags = s.tags + (tagId to fn(it))) } ?: s }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(tag.label, { v -> edit { it.copy(label = v) } }, Modifier.fillMaxWidth(), label = { Text("Tag name") }, singleLine = true)
        Text(recipe?.title ?: "Recipe not available on this phone", style = MaterialTheme.typography.titleMedium)
        recipe?.params?.forEach { spec ->
            ParamSlider(spec, (tag.params[spec.key] ?: spec.default).coerceIn(spec.min, spec.max)) { v -> edit { it.copy(params = it.params + (spec.key to v)) } }
        }
        Text("Changes apply on your next tap. The tag itself doesn't need rewriting.", style = MaterialTheme.typography.bodySmall)

        val running = state.active.values.any { it.recipeId == tag.recipeId }
        if (running) Text("This mode is on. End it before switching recipe or removing the tag.", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { choosing = true }, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text("Switch recipe") }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            catalog?.recipes.orEmpty().filter(::isUsable).forEach { r ->
                DropdownMenuItem(text = { Text(r.title) }, onClick = {
                    edit { it.copy(recipeId = r.id, params = mergeParams(it.params, r.params)) }
                    app.update { it.copy(installedRecipes = it.installedRecipes + r.id) }
                    choosing = false
                })
            }
        }
        OutlinedButton(onClick = { rewriting = true }, modifier = Modifier.fillMaxWidth()) { Text("Rewrite this tag") }
        TextButton(enabled = !running, onClick = {
            app.update { it.copy(tags = it.tags - tagId) }
            BlockController.sync(context)
            onDone()
        }) { Text("Remove tag") }
    }

    if (rewriting) TagWriteDialog(tagId, onWritten = { rewriting = false }, onCancel = { rewriting = false })
}
