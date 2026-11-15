package com.habittags.app.ui

import android.nfc.NfcAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.TapHandler
import kotlinx.coroutines.delay

/** Shown above both tabs: NFC warnings and every mode that is on, each with End now. */
@Composable
fun ActiveModeStrip(onEndNow: (String) -> Unit) {
    val context = LocalContext.current
    val state by context.app().state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val nfc = remember { NfcAdapter.getDefaultAdapter(context) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (nfc == null) Banner("This phone can't read NFC tags.")
        else if (!nfc.isEnabled) Banner("NFC is off. Turn it on in Settings to use your tags.")
        if (state.active.isEmpty()) return@Column
        Text("On now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        state.active.values.forEach { mode ->
            val label = state.tags[mode.tagId]?.label ?: mode.recipeId
            val strictLeft = mode.strictUntil?.takeIf { it > now }?.let { TapHandler.minutesLeft(it - now) }
            val detail = when {
                strictLeft != null -> " · strict, $strictLeft min left"
                mode.autoOffAt != null -> " · ends ${TapHandler.formatTime(mode.autoOffAt!!)}"
                else -> ""
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label + detail, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { onEndNow(mode.tagId) }) { Text("End now") }
                }
            }
        }
    }
}

@Composable
fun Banner(text: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(text, Modifier.padding(16.dp))
    }
}
