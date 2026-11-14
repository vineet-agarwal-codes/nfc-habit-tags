package com.habittags.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter

object TagIoFactory {
    fun create(activity: Activity): TagIo? = NfcAdapter.getDefaultAdapter(activity)?.let { NfcTagIo(activity, it) }
}
