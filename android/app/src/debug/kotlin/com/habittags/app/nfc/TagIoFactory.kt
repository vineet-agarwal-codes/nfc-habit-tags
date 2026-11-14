package com.habittags.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import kotlinx.coroutines.delay

/** Debug builds on phones without NFC (the CI emulator) simulate a successful write. */
object TagIoFactory {
    fun create(activity: Activity): TagIo? =
        NfcAdapter.getDefaultAdapter(activity)?.let { NfcTagIo(activity, it) } ?: FakeTagIo
}

object FakeTagIo : TagIo {
    override suspend fun awaitWrite(tagId: String, overwrite: Boolean): WriteResult {
        delay(1_000)
        return WriteResult.Written(tagId)
    }
}
