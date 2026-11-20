package com.habittags.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.habittags.app.block.BlockController
import com.habittags.core.mode.EmergencyExit
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * An "Emergency exit" card: ends [modeKey] without typing, once every 7 days for the whole phone.
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
    // A clear card, not a quiet link: it's the honest way out when there's no time to type.
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Emergency exit", style = MaterialTheme.typography.titleMedium, color = color)
            if (next != null) {
                val day = DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()))
                Text("Used this week. Your next one is on $day.", style = MaterialTheme.typography.bodyMedium, color = color)
            } else {
                Text("Skip the typing, once every 7 days. 1 available.", style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
        if (next == null) {
            OutlinedButton(
                onClick = { confirming = true },
                border = BorderStroke(1.5.dp, color),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            ) { Text("Use it") }
        }
    }
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
