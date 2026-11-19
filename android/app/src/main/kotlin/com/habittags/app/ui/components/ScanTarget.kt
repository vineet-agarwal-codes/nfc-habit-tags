package com.habittags.app.ui.components

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habittags.app.TapHandler
import com.habittags.app.nfc.TagWriter
import com.habittags.app.ui.theme.reducedMotion

/**
 * The tag target at the bottom of Home. While Home is on screen the app reads tags itself (reader mode),
 * handing each to the same [TapHandler] a tag tapped from outside the app uses, so nothing runs twice.
 */
@Composable
fun ScanTarget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner, adapter) {
        if (activity == null || adapter == null) return@DisposableEffect onDispose { }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        fun enable() = runCatching {
            adapter.enableReaderMode(activity, { tag ->
                val message = Ndef.get(tag)?.cachedNdefMessage
                val tagId = message?.let { TagWriter.tagIdOf(listOf(it)) }
                if (tagId != null) activity.runOnUiThread { TapHandler.handle(activity, tagId) }
            }, flags, null)
        }
        fun disable() = runCatching { adapter.disableReaderMode(activity) }
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) enable()
            if (e == Lifecycle.Event.ON_PAUSE) disable()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enable()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            disable()
        }
    }

    when {
        adapter == null -> Notice("This phone can't read tags. Start your spaces from the app.", modifier)
        !adapter.isEnabled -> Notice("NFC is off, so your tags won't work.", modifier) {
            SecondaryButton("Turn on NFC", { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        }
        else -> {
            val still = reducedMotion()
            val pulse = if (still) 1f else rememberInfiniteTransition(label = "scan").animateFloat(
                initialValue = 0.85f, targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse",
            ).value
            Row(
                modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CardShape).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(14.dp).scale(pulse).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
                Text("Hold your phone to a tag", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
