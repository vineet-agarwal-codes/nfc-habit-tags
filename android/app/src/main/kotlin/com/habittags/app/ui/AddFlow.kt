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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.habittags.core.tag.TipPlace
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.ui.components.Note
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.QuietButton
import com.habittags.app.ui.components.ScreenPadding
import com.habittags.app.ui.theme.SpaceColour
import com.habittags.app.ui.theme.toColor
import com.habittags.core.mode.InstalledTag
import com.habittags.core.recipe.BlockGroups
import com.habittags.core.space.Spaces
import com.habittags.core.tag.TagPayload

/**
 * Adding a recipe to a space in three steps: settings (defaults already set), the permissions this
 * recipe needs (skipped when granted), then the tag. The space's colour is picked automatically.
 * With [setupTagId] (a tag the phone didn't know) the last step links that tag instead of writing one.
 */
@Composable
fun AddFlow(recipeId: String, setupTagId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val recipe = remember(recipeId) { app.recipe(recipeId) } ?: return
    val params = remember { mutableStateMapOf<String, Int>().apply { recipe.params.forEach { put(it.key, it.default) } } }
    var name by remember { mutableStateOf(Spaces.defaultName(recipe)) }
    var step by remember { mutableIntStateOf(0) }
    var writingTagId by remember { mutableStateOf<String?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    val colour = SpaceColour.of(recipe.space?.colour)

    fun finish(tagId: String?) {
        app.update {
            it.copy(
                tags = if (tagId == null) it.tags else it.tags + (tagId to InstalledTag(tagId, recipe.id, name.ifBlank { recipe.title }, params.toMap())),
                installedRecipes = it.installedRecipes + recipe.id,
                recipeParams = it.recipeParams + (recipe.id to params.toMap()),
            )
        }
        app.update { Spaces.rename(it, recipe, name) }
        onDone()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LinearProgressIndicator(
            progress = { (step + 1) / 3f },
            modifier = Modifier.fillMaxWidth(),
            color = colour.field.toColor(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        when (step) {
            0 -> {
                Text("Set up $name", style = MaterialTheme.typography.displaySmall)
                Text(recipe.title.substringAfter(": "), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Space name") }, singleLine = true)
                recipe.params.forEach { spec -> ParamControl(spec, params[spec.key] ?: spec.default) { params[spec.key] = it } }
                hoursText(recipe, params.toMap())?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val groups = recipe.rules.flatMap { it.actions }.filterIsInstance<BlockGroups>().flatMap { it.groups }.distinct()
                if (groups.isNotEmpty()) {
                    Text("Apps to block", style = MaterialTheme.typography.titleLarge)
                    groups.forEach { GroupEditor(it) }
                }
                PrimaryButton("Next", { step = if (permissionsGranted(context, recipe)) 2 else 1 }, Modifier.fillMaxWidth())
            }
            1 -> {
                Text("Allow what $name needs", style = MaterialTheme.typography.displaySmall)
                Text("Mise only asks for what this recipe uses. Each opens a system screen; come back here after.", style = MaterialTheme.typography.bodyLarge)
                val ready = PermissionCards(recipe)
                PrimaryButton("Next", { step = 2 }, Modifier.fillMaxWidth(), enabled = ready)
                QuietButton("Back", { step = 0 })
            }
            else -> {
                if (setupTagId != null) {
                    Text("Use this tag for $name", style = MaterialTheme.typography.displaySmall)
                    Text("The tag you just tapped will switch $name on and off.", style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton("Use this tag", { finish(setupTagId) }, Modifier.fillMaxWidth())
                } else {
                    Text("Write the tag", style = MaterialTheme.typography.displaySmall)
                    Text("Any NFC sticker works: NTAG213, 215 or 216 are best. ${recipe.placementHint}", style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton("Write to a tag", { writingTagId = TagPayload.newTagId() }, Modifier.fillMaxWidth())
                    QuietButton("Skip, start it from the app", { finish(null) }, Modifier.align(Alignment.CenterHorizontally))
                    Note("You can write a tag later from the space.")
                    SecureNfcTip(TipPlace.AddFlow)
                }
            }
        }
    }

    writingTagId?.let { id -> TagWriteDialog(id, onWritten = { finish(id) }, onCancel = { writingTagId = null }) }
}
