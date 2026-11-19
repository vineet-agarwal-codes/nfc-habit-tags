package com.habittags.app.zone

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.habittags.app.DebugSettings
import com.habittags.app.ui.HabitTheme
import com.habittags.core.zone.UNKNOWN_SPOT
import com.habittags.core.zone.MODE_POCKET
import com.habittags.core.zone.MODE_RESTING
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Debug-only "Zone lab": teach spots, check guesses, and run lab tracking. Everything uploads automatically. */
class ZoneLabActivity : ComponentActivity() {
    /** Set when opened from a notification's "No": the sample whose spot the user should pick. */
    private val askFor = mutableStateOf<Pair<String, String?>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZoneLabStore.load(this)
        readAsk(intent)
        // Surface paints the theme's background; without it a dark-mode phone got light text on the light window.
        setContent { HabitTheme { Surface(Modifier.fillMaxSize()) { ZoneLabScreen(askFor) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readAsk(intent)
    }

    private fun readAsk(intent: Intent?) {
        val id = intent?.getStringExtra(ZoneTrackingService.EXTRA_ID) ?: return
        getSystemService(NotificationManager::class.java).cancel(ZoneTrackingService.ASK_NOTIFICATION_ID)
        askFor.value = id to intent.getStringExtra(ZoneTrackingService.EXTRA_GUESS)
    }
}

private val LAB_PERMISSIONS = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun missingPermissions(context: Context) =
    LAB_PERMISSIONS.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

private fun locationOn(context: Context) =
    LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneLabScreen(askFor: androidx.compose.runtime.MutableState<Pair<String, String?>?>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordings by ZoneLabStore.recordings.collectAsState()
    val pending by ZoneLabStore.pending.collectAsState()
    val fake = DebugSettings.zoneFakeSpot(context) != null

    var permissionTick by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick++ }
    val missing = remember(permissionTick) { if (fake) emptyList() else missingPermissions(context) }
    val locationOff = remember(permissionTick) { !fake && !locationOn(context) }
    val ready = missing.isEmpty() && !locationOff

    var spot by remember { mutableStateOf("Desk") }
    var custom by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(MODE_RESTING) }
    var busy by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    var recordStatus by remember { mutableStateOf<String?>(null) }
    var observation by remember { mutableStateOf<ZoneLab.Observation?>(null) }
    var answered by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(ZoneTrackingService.running) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Zone lab", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Teach the app your spots, then use the phone normally. Everything is uploaded automatically for analysis.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!ready) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (missing.isNotEmpty()) {
                        Text("The lab needs location (for Wi-Fi scans), physical activity (step detection) and notifications.")
                        Button(onClick = { launcher.launch(LAB_PERMISSIONS) }) { Text("Grant permissions") }
                    }
                    if (locationOff) {
                        Text("Turn on Location: Android only returns Wi-Fi scans while it's on.")
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }) { Text("Open location settings") }
                        TextButton(onClick = { permissionTick++ }) { Text("I've turned it on") }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("1. Teach a spot", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneLabStore.spots().forEach { s ->
                FilterChip(selected = spot == s && custom.isBlank(), onClick = { spot = s; custom = "" }, label = { Text(s) })
            }
        }
        OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Or a new spot name") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == MODE_RESTING, onClick = { mode = MODE_RESTING }, label = { Text("Phone resting here") })
            FilterChip(selected = mode == MODE_POCKET, onClick = { mode = MODE_POCKET }, label = { Text("In my pocket here") })
        }
        val target = custom.trim().ifBlank { spot }
        val modeLabel = if (mode == MODE_RESTING) "resting" else "in pocket"
        Button(enabled = ready && !busy, onClick = {
            busy = true
            scope.launch {
                val rec = ZoneLab.record(context, target, mode) { p ->
                    recordStatus = when (p) {
                        is ZoneLab.RecordProgress.Scanning -> "Recording $target ($modeLabel): ${p.done} of ${ZoneLab.SCANS_PER_RECORDING} scans"
                        is ZoneLab.RecordProgress.Throttled ->
                            "Waiting: Android allows 4 Wi-Fi scans per 2 minutes (${p.done} of ${ZoneLab.SCANS_PER_RECORDING} done). Keep the phone where it is."
                    }
                }
                recordStatus = if (rec != null) "Saved: $target ($modeLabel)" else "Couldn't get ${ZoneLab.SCANS_PER_RECORDING} fresh scans in 4 minutes. Try again."
                if (rec != null) custom = ""
                busy = false
            }
        }) { Text("Record $target") }
        recordStatus?.let { Text(it) }
        val counts = recordings.groupBy { it.spot }.mapValues { (_, recs) ->
            "resting ${recs.count { it.mode == MODE_RESTING }}, pocket ${recs.count { it.mode == MODE_POCKET }}"
        }
        if (counts.isNotEmpty()) {
            Text("Recorded so far:", style = MaterialTheme.typography.labelLarge)
            counts.forEach { (s, c) -> Text("$s — $c") }
        }

        HorizontalDivider()
        Text("2. Where am I?", style = MaterialTheme.typography.titleMedium)
        Button(enabled = ready && !busy && recordings.isNotEmpty(), onClick = {
            busy = true
            answered = false
            observation = null
            scope.launch {
                waiting = false
                observation = ZoneLab.observe(context, "live", waitForFresh = true, onWaiting = { waiting = true })
                waiting = false
                busy = false
            }
        }) { Text("Where am I?") }
        if (recordings.isEmpty()) Text("Record at least one spot first.", style = MaterialTheme.typography.bodySmall)
        if (busy && !waiting) Text("Checking where you are…", style = MaterialTheme.typography.bodyMedium)
        if (waiting) Text("Waiting for a fresh Wi-Fi scan — Android allows only a few per minute. Up to 2 minutes.", style = MaterialTheme.typography.bodySmall)
        if (!busy) observation?.let { obs ->
            val g = obs.guess
            if (g == null) {
                Text("Couldn't get a fresh Wi-Fi scan, so no guess. Try again in a minute.")
            } else {
                if (!answered) {
                    Text("Where are you actually? Tap it:", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(g.scores.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${(it.value * 100).roundToInt()}%" })
                }
                if (!obs.sample.scanFresh) Text("(Used a Wi-Fi scan from the last minute.)", style = MaterialTheme.typography.bodySmall)
                if (!answered) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        g.scores.entries.sortedByDescending { it.value }.forEachIndexed { i, (spot, score) ->
                            val label = "$spot ${(score * 100).roundToInt()}%"
                            val pick = {
                                answered = true
                                scope.launch { ZoneLab.answer(context, obs.id, g.spot, spot, "live") }
                                Unit
                            }
                            if (i == 0) Button(onClick = pick) { Text(label) } else OutlinedButton(onClick = pick) { Text(label) }
                        }
                        TextButton(onClick = { askFor.value = obs.id to g.spot }) { Text("Somewhere else") }
                    }
                } else {
                    Text("Thanks — saved.")
                }
            }
        }

        HorizontalDivider()
        Text("3. Lab tracking", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = tracking, enabled = ready && recordings.isNotEmpty(), onCheckedChange = { on ->
                tracking = on
                if (on) ZoneTrackingService.start(context) else ZoneTrackingService.stop(context)
            })
            Text(if (tracking) "On" else "Off")
        }
        Text(
            "Checks your spot each time you stop moving and asks \"Are you at …?\" about once an hour.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        val uploadFailed by ZoneLabStore.uploadFailed.collectAsState()
        if (uploadFailed && pending > 0) {
            Text("Couldn't upload $pending lab events (no internet?). They're kept and retried automatically.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { scope.launch { ZoneLabStore.upload(context) } }) { Text("Retry now") }
        }
    }

    askFor.value?.let { (id, guessed) ->
        SpotPicker(
            title = if (guessed != null) "Not $guessed — where are you?" else "Where are you?",
            onPick = { actual ->
                askFor.value = null
                if (observation?.id == id) answered = true
                scope.launch { ZoneLab.answer(context, id, guessed, actual, if (observation?.id == id) "live" else "notification") }
            },
            onDismiss = { askFor.value = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotPicker(title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (ZoneLabStore.spots() + UNKNOWN_SPOT).forEach { s -> OutlinedButton(onClick = { onPick(s) }) { Text(s) } }
                }
                OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Somewhere else") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = custom.isNotBlank(), onClick = { onPick(custom.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
