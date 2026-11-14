package com.habittags.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.habittags.app.DebugSettings
import com.habittags.app.block.BlockController
import com.habittags.core.gate.FrictionGate
import com.habittags.core.mode.ModeEngine
import kotlin.random.Random

@Composable
fun GateScreen(tagId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    val passage = remember { FrictionGate.passage(Random.Default, DebugSettings.gateWords(context) ?: FrictionGate.TARGET_WORDS) }
    var matched by remember { mutableIntStateOf(0) }
    var rejected by remember { mutableStateOf(false) }
    val label = state.tags[tagId]?.label ?: "This mode"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tagId !in state.active) {
            Text("$label has already ended.")
            Button(onClick = onDone) { Text("Back") }
            return@Column
        }
        Text("End $label early?", style = MaterialTheme.typography.headlineSmall)
        Text("Tapping the tag again ends it for free. To end it without the tag, type the passage below exactly, letter by letter.")
        Text(passage, style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(progress = { matched.toFloat() / passage.length }, modifier = Modifier.fillMaxWidth())
        if (rejected) Text("Type it letter by letter — voice and paste don't count.", color = MaterialTheme.colorScheme.error)
        AndroidView(
            factory = { ctx ->
                GateEditText(ctx, passage).apply {
                    onProgress = { typed, wasRejected ->
                        rejected = wasRejected
                        matched = FrictionGate.progress(typed, passage)
                        if (FrictionGate.isComplete(typed, passage)) {
                            app.update { it.copy(active = ModeEngine.endViaGate(it.active, tagId)) }
                            BlockController.sync(ctx)
                            onDone()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        )
    }
}
