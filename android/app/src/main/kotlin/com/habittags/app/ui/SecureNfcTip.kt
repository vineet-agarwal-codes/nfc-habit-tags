package com.habittags.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.ui.components.Panel
import com.habittags.app.ui.components.QuietButton
import com.habittags.app.ui.components.SecondaryButton
import com.habittags.core.tag.SecureNfcState
import com.habittags.core.tag.TipPlace
import com.habittags.core.tag.showSecureNfcTip

private const val PREFS = "tips"
private const val KEY_HOME_DISMISSED = "secureNfcHomeDismissed"

/** Secure NFC as the phone reports it (API 29+); older phones don't have the setting. */
class AndroidSecureNfc(context: Context) : SecureNfcState {
    private val adapter = NfcAdapter.getDefaultAdapter(context)
    override val supported: Boolean = Build.VERSION.SDK_INT >= 29 && adapter?.isSecureNfcSupported == true
    override val enabled: Boolean = supported && runCatching { adapter!!.isSecureNfcEnabled }.getOrDefault(false)
}

/**
 * "Tags without unlocking": while "Require device unlock for NFC" is on, a tag tap does nothing on a
 * locked phone. Re-checked each time the screen resumes, so it disappears once the setting is off.
 */
@Composable
fun SecureNfcTip(place: TipPlace, hasTag: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(KEY_HOME_DISMISSED, false)) }
    var cantOpen by remember { mutableStateOf(false) }
    val nfc = remember(tick) { AndroidSecureNfc(context) }
    if (!showSecureNfcTip(nfc, place, hasTag, dismissed)) return
    Panel(modifier) {
        Text("Tags without unlocking", style = MaterialTheme.typography.titleMedium)
        Text("Want tags to work without unlocking? Turn off 'Require device unlock for NFC'.", style = MaterialTheme.typography.bodyMedium)
        if (cantOpen) {
            Text(
                "Your phone doesn't let apps open this setting. Look for it under Settings > Connected devices > NFC.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "Open NFC settings", { cantOpen = !openNfcSettings(context) },
                container = MaterialTheme.colorScheme.surface,
            )
            if (place == TipPlace.Home) {
                QuietButton("Not now", {
                    prefs.edit().putBoolean(KEY_HOME_DISMISSED, true).apply()
                    dismissed = true
                })
            }
        }
    }
}

private fun openNfcSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) == null) return false
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
