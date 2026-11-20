package com.habittags.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.habittags.app.block.BlockController
import com.habittags.app.ui.components.QuietButton
import com.habittags.core.mode.EmergencyExit
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A quiet "Emergency exit": ends [modeKey] without typing, once every 7 days for the whole phone.
 * When it's been used, shows when the next one becomes available instead.
 */
@Composable
fun EmergencyExitAction(modeKey: String, color: Color, onEnded: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    val mode = state.active[modeKey] ?: return
    var confirming by remember { mutableStateOf(false) }
    val next = EmergencyExit.nextAt(state.lastEmergencyExitMs, System.currentTimeMillis())
    if (next != null) {
        val day = DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()))
        Text("Next emergency exit on $day", style = MaterialTheme.typography.bodySmall, color = color)
        return
    }
    QuietButton("Emergency exit", { confirming = true }, color = color)
    if (confirming) {
        val space = app.spaceName(mode.recipeId)
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Use your emergency exit?") },
            text = { Text("It ends $space now without typing. You get one every 7 days.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    val now = Clock.System.now()
                    app.update { EmergencyExit.use(it, modeKey, now, TimeZone.currentSystemDefault()) ?: it }
                    BlockController.sync(context)
                    onEnded()
                }) { Text("Use it") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}
