package com.habittags.app.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.habittags.app.TapHandler
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.ScreenPadding
import com.habittags.app.ui.components.runningLines
import com.habittags.app.ui.components.rememberNow

/** A space that's on, full screen in its colour: name, a large countdown, what's paused, End early. */
@Composable
fun RunningScreen(modeKey: String, onEndEarly: () -> Unit, onOpenSettings: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    val mode = state.active[modeKey]
    val now = rememberNow()
    if (mode == null || mode.autoOffAt?.let { now >= it } == true) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }
        return
    }
    val colour = app.spaceColour(mode.recipeId)
    val field = androidx.compose.ui.graphics.Color(colour.field.toInt())
    val onField = androidx.compose.ui.graphics.Color(colour.onField.toInt())
    val activity = context as? Activity
    DisposableEffect(colour) {
        val window = activity?.window
        val before = window?.statusBarColor
        window?.statusBarColor = colour.field.toInt()
        onDispose { if (window != null && before != null) window.statusBarColor = before }
    }
    val recipe = app.recipe(mode.recipeId)
    val icons = remember(mode.blockedPackages) {
        mode.blockedPackages.mapNotNull { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().background(field).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val lines = app.runningLines(mode, now)
        Text(lines.title, style = MaterialTheme.typography.headlineMedium, color = onField)
        Text(lines.subtitle, style = MaterialTheme.typography.titleMedium, color = onField)
        Spacer(Modifier.height(24.dp))
        Text(lines.remaining ?: "On until you end it", style = MaterialTheme.typography.displaySmall, color = onField)
        lines.ends?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = onField) }
        lines.paused?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = onField) }
        mode.strictUntil?.takeIf { it > now }?.let {
            Text("Strict for ${TapHandler.minutesLeft(it - now)} more min: tapping the tag won't end it yet.", style = MaterialTheme.typography.bodyMedium, color = onField)
        }
        Spacer(Modifier.height(24.dp))
        if (icons.isNotEmpty()) {
            Text("Paused", style = MaterialTheme.typography.titleMedium, color = onField)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                icons.take(8).forEach { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
                if (icons.size > 8) Text("+${icons.size - 8}", style = MaterialTheme.typography.titleMedium, color = onField)
            }
        }
        lines.reaches?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = onField) }
        Spacer(Modifier.height(32.dp))
        Text(
            if (recipe?.tagCanEnd == false) "It ends on its own at that time. To stop sooner, end it early here."
            else "Tap the tag again to end it, or end it early here.",
            style = MaterialTheme.typography.bodyMedium, color = onField,
        )
        PrimaryButton("End early", onEndEarly, Modifier.fillMaxWidth(), container = onField, content = field)
        EmergencyExitAction(modeKey, onField, onEnded = onClose)
        com.habittags.app.ui.components.QuietButton("${app.spaceName(mode.recipeId)} settings", { onOpenSettings(mode.recipeId) }, color = onField)
    }
}
