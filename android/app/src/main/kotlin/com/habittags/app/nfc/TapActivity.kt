package com.habittags.app.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import com.habittags.app.TapHandler

/** Invisible entry point for tag taps; hands the tagId to [TapHandler] and closes. */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>().orEmpty()
        TagWriter.tagIdOf(messages)?.let { TapHandler.handle(this, it) }
        finish()
    }
}
