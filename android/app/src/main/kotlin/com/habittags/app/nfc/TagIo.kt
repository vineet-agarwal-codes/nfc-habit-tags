package com.habittags.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface TagIo {
    /** Waits for the user to hold a tag to the phone, then writes [tagId] to it. */
    suspend fun awaitWrite(tagId: String, overwrite: Boolean): WriteResult
}

class NfcTagIo(private val activity: Activity, private val adapter: NfcAdapter) : TagIo {
    override suspend fun awaitWrite(tagId: String, overwrite: Boolean): WriteResult =
        suspendCancellableCoroutine { cont ->
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            adapter.enableReaderMode(activity, { tag ->
                val result = TagWriter.write(tag, tagId, overwrite)
                activity.runOnUiThread { adapter.disableReaderMode(activity) }
                if (cont.isActive) cont.resume(result)
            }, flags, null)
            cont.invokeOnCancellation { activity.runOnUiThread { adapter.disableReaderMode(activity) } }
        }
}
