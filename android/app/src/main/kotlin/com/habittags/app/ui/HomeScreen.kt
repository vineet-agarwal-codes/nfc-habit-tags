package com.habittags.app.ui

import android.nfc.NfcAdapter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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

@Composable
fun HomeScreen(onAddTag: () -> Unit, onOpenTag: (String) -> Unit, onEndNow: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val nfc = remember { NfcAdapter.getDefaultAdapter(context) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Habit Tags", style = MaterialTheme.typography.headlineMedium) }
        if (nfc == null) item { Banner("This phone can't read NFC tags.") }
        else if (!nfc.isEnabled) item { Banner("NFC is off. Turn it on in Settings to use your tags.") }

        if (state.active.isNotEmpty()) item { Text("On now", style = MaterialTheme.typography.titleMedium) }
        items(state.active.values.toList(), key = { "active-${it.tagId}" }) { mode ->
            val label = state.tags[mode.tagId]?.label ?: mode.recipeId
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        mode.autoOffAt?.let { Text("Ends at ${TapHandler.formatTime(it)}") }
                        mode.strictUntil?.takeIf { it > now }?.let { Text("Strict: ${TapHandler.minutesLeft(it - now)} min left") }
                    }
                    OutlinedButton(onClick = { onEndNow(mode.tagId) }) { Text("End now") }
                }
            }
        }

        item { Text("My tags", style = MaterialTheme.typography.titleMedium) }
        if (state.tags.isEmpty()) item { Text("No tags yet. Add one to get started.") }
        items(state.tags.values.toList(), key = { "tag-${it.tagId}" }) { tag ->
            Card(Modifier.fillMaxWidth().clickable { onOpenTag(tag.tagId) }) {
                Text(tag.label, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        item { Button(onClick = onAddTag, modifier = Modifier.fillMaxWidth()) { Text("Add a tag") } }
    }
}

@Composable
fun Banner(text: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(text, Modifier.padding(16.dp))
    }
}
