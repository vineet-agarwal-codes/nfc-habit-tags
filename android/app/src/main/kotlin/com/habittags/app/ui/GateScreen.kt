package com.habittags.app.ui

import android.graphics.drawable.GradientDrawable
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.habittags.app.DebugSettings
import com.habittags.app.block.BlockController
import com.habittags.core.gate.FrictionGate
import com.habittags.core.mode.ModeEngine
import kotlin.random.Random

/**
 * Typing gate: the passage sits in a card with what's been typed shown in colour and the next letter
 * highlighted; the input box is pinned above the keyboard and focused on open.
 */
@Composable
fun GateScreen(tagId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    val passage = remember { FrictionGate.passage(Random.Default, DebugSettings.gateWords(context) ?: FrictionGate.TARGET_WORDS) }
    val totalWords = remember(passage) { FrictionGate.wordCount(passage) }
    var typed by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    val label = state.active[tagId]?.let(app::modeLabel) ?: "This mode"

    if (tagId !in state.active) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$label has already ended.")
            Button(onClick = onDone) { Text("Back") }
        }
        return
    }

    val matched = FrictionGate.matchedLength(typed, passage)
    val typo = FrictionGate.hasTypo(typed, passage)
    val colors = MaterialTheme.colorScheme
    val doneColor = Color(0xFF2E7D32)

    Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("End $label early?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Copy the text below into the box at the bottom, letter by letter. Or tap your tag to end it instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${FrictionGate.wordsTyped(typed, passage)} of $totalWords words", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { matched.toFloat() / passage.length }, modifier = Modifier.weight(1f))
        }

        val scroll = rememberScrollState()
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val lineSlack = with(LocalDensity.current) { 32.dp.toPx() }
        LaunchedEffect(matched, layout) {
            val l = layout ?: return@LaunchedEffect
            val top = l.getLineTop(l.getLineForOffset(matched.coerceAtMost(passage.length)))
            scroll.animateScrollTo((top - lineSlack).toInt().coerceAtLeast(0))
        }
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = doneColor)) { append(passage.take(matched)) }
                    if (matched < passage.length) {
                        val next = if (typo) SpanStyle(background = colors.errorContainer, color = colors.onErrorContainer, fontWeight = FontWeight.Bold)
                        else SpanStyle(background = colors.primaryContainer, color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        withStyle(next) { append(passage[matched]) }
                        withStyle(SpanStyle(color = colors.onSurface)) { append(passage.substring(matched + 1)) }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                onTextLayout = { layout = it },
                modifier = Modifier.verticalScroll(scroll).padding(16.dp),
            )
        }

        when {
            rejected -> Text("Type it letter by letter — voice, swipe and paste don't count.", color = colors.error)
            typo -> Text("Typo — press backspace and fix the highlighted letter.", color = colors.error)
        }

        val borderColor = (if (typo) colors.error else colors.primary).toArgb()
        val hintColor = colors.onSurfaceVariant.toArgb()
        val textColor = colors.onSurface.toArgb()
        AndroidView(
            factory = { ctx ->
                GateEditText(ctx, passage).apply {
                    hint = "Start typing here…"
                    setHintTextColor(hintColor)
                    setTextColor(textColor)
                    textSize = 18f
                    minLines = 2
                    maxLines = 3
                    val pad = (14 * ctx.resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    onProgress = { now, wasRejected ->
                        rejected = wasRejected
                        typed = now
                        if (FrictionGate.isComplete(now, passage)) {
                            app.update { it.copy(active = ModeEngine.endViaGate(it.active, tagId)) }
                            BlockController.sync(ctx)
                            onDone()
                        }
                    }
                    post {
                        requestFocus()
                        ctx.getSystemService(InputMethodManager::class.java).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            },
            update = { view ->
                val density = view.resources.displayMetrics.density
                view.background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setStroke((2 * density).toInt(), borderColor)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
