package com.habittags.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.core.mode.InstalledTag
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.tag.TagPayload

@Composable
fun InstallScreen(recipeId: String, setupTagId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val recipe = remember(recipeId) { app.recipe(recipeId) } ?: return
    val params = remember { mutableStateMapOf<String, Int>().apply { recipe.params.forEach { put(it.key, it.default) } } }
    var label by remember { mutableStateOf(recipe.title) }
    var writingTagId by remember { mutableStateOf<String?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }

    fun save(tagId: String) {
        app.update {
            it.copy(
                tags = it.tags + (tagId to InstalledTag(tagId, recipe.id, label.ifBlank { recipe.title }, params.toMap())),
                installedRecipes = it.installedRecipes + recipe.id,
            )
        }
        onDone()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
        Text(recipe.description)
        Text("Where to put it: ${recipe.placementHint}", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("Tag name") }, singleLine = true)
        recipe.params.forEach { spec -> ParamSlider(spec, params[spec.key] ?: spec.default) { params[spec.key] = it } }
        val groups = recipe.rules.flatMap { it.actions }.filterIsInstance<BlockGroups>().flatMap { it.groups }.distinct()
        if (groups.isNotEmpty()) {
            Text("Apps to block", style = MaterialTheme.typography.titleMedium)
            groups.forEach { GroupEditor(it) }
        }
        val ready = PermissionCards(recipe)
        if (setupTagId != null) {
            Button(onClick = { save(setupTagId) }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Use this tag") }
        } else {
            Text("NTAG213, 215 or 216 tags work best.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { writingTagId = TagPayload.newTagId() }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Write to a tag") }
            TextButton(onClick = {
                app.update { it.copy(installedRecipes = it.installedRecipes + recipe.id) }
                onDone()
            }, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        }
    }

    writingTagId?.let { id -> TagWriteDialog(id, onWritten = { save(id) }, onCancel = { writingTagId = null }) }
}
