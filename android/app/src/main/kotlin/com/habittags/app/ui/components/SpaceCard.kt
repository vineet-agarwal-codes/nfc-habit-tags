package com.habittags.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habittags.app.TapHandler
import com.habittags.app.ui.theme.SpaceColour
import com.habittags.app.ui.theme.tintColor
import com.habittags.app.ui.theme.toColor
import com.habittags.core.space.SpaceStatus
import kotlinx.coroutines.delay

/** "1:24:10" or "24:10" until [endMs]. */
fun countdown(remainingMs: Long): String {
    val total = (remainingMs.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** Ticks once a second while composed. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    return now
}

/**
 * One space on Home. Idle: its pale tint with name, recipe and status. Running: the whole card in the
 * space colour with the countdown large; tapping it opens the running screen.
 */
@Composable
fun SpaceCard(
    spaceName: String,
    recipeTitle: String,
    colour: SpaceColour,
    status: SpaceStatus,
    tagCount: Int,
    onOpen: () -> Unit,
    onOpenRunning: () -> Unit,
    onEndEarly: () -> Unit,
    startAction: @Composable () -> Unit,
    /** Plain-words lines for a running space, recomputed each second. */
    runningLines: ((Long) -> RunningLines)? = null,
) {
    val running = status as? SpaceStatus.Running
    val field = colour.field.toColor()
    val onField = colour.onField.toColor()
    if (running != null) {
        val now = rememberNow()
        Column(
            Modifier.fillMaxWidth().animateContentSize().background(field, CardShape).clickable(onClick = onOpenRunning).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val lines = runningLines?.invoke(now)
            Text(lines?.title ?: "$spaceName is on", style = MaterialTheme.typography.headlineSmall, color = onField)
            lines?.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = onField) }
            Text(
                lines?.remaining ?: "On until you end it",
                style = MaterialTheme.typography.headlineLarge,
                color = onField,
                modifier = Modifier.padding(top = 10.dp),
            )
            lines?.ends?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = onField) }
            lines?.paused?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = onField, modifier = Modifier.padding(top = 6.dp)) }
            running.strictUntilMs?.takeIf { it > now }?.let {
                Text("Strict for ${TapHandler.minutesLeft(it - now)} more min", style = MaterialTheme.typography.bodyMedium, color = onField)
            }
            lines?.reaches?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = onField) }
            Row(Modifier.padding(top = 8.dp)) {
                SecondaryButton("End early", onEndEarly, container = onField, content = field)
            }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().background(colour.tintColor(), CardShape).clickable(onClick = onOpen).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spaceName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(
                when (status) {
                    is SpaceStatus.StartsAt -> "From ${status.time}"
                    SpaceStatus.TapToRun -> "Tap to run"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(recipeTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            listOfNotNull(
                tagText(tagCount),
                (status as? SpaceStatus.Idle)?.lastUsedMs?.let { "last used ${lastUsedText(it)}" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(Modifier.padding(top = 10.dp)) { startAction() }
    }
}

fun tagText(n: Int): String = when (n) {
    0 -> "No tag yet"
    1 -> "1 tag"
    else -> "$n tags"
}

fun reachText(n: Int): String = if (n == 1) "You reached for a blocked app once" else "You reached for blocked apps $n times"

/** "today 09:40", "yesterday" or "12 Nov". */
fun lastUsedText(ms: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val then = java.time.Instant.ofEpochMilli(ms).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    return when (then.toLocalDate()) {
        today -> "today ${TapHandler.formatTime(ms)}"
        today.minusDays(1) -> "yesterday"
        else -> java.time.format.DateTimeFormatter.ofPattern("d MMM").format(then)
    }
}
