package com.habittags.app.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.habittags.app.BuildConfig
import com.habittags.core.tag.TagPayload

sealed interface WriteResult {
    data class Written(val tagId: String) : WriteResult
    data object HasOtherData : WriteResult
    data object NotWritable : WriteResult
    data object TooSmall : WriteResult
    data class Failed(val message: String) : WriteResult
}

object TagWriter {
    private val externalType = "${TagPayload.EXTERNAL_DOMAIN}:${TagPayload.EXTERNAL_TYPE}".toByteArray(Charsets.US_ASCII)

    fun message(tagId: String) = NdefMessage(
        NdefRecord.createExternal(TagPayload.EXTERNAL_DOMAIN, TagPayload.EXTERNAL_TYPE, tagId.toByteArray(Charsets.US_ASCII)),
        // The record type above stays com.habittags.app:tag so tags written by earlier builds keep working;
        // the Android Application Record must name the installed package so a tap opens (or installs) Mise.
        NdefRecord.createApplicationRecord(BuildConfig.APPLICATION_ID),
    )

    /** Our external record (Android-written) or the https link record (iPhone-written). */
    fun tagIdOf(messages: List<NdefMessage>): String? {
        val records = messages.flatMap { it.records.asList() }
        return records.firstOrNull { it.tnf == NdefRecord.TNF_EXTERNAL_TYPE && it.type.contentEquals(externalType) }
            ?.let { TagPayload.parse(it.payload) }
            ?: records.firstNotNullOfOrNull { r -> r.toUri()?.toString()?.let(TagPayload::parseUrl) }
    }

    private fun isOurs(message: NdefMessage?) = message != null && tagIdOf(listOf(message)) != null

    private fun isEmpty(message: NdefMessage?) =
        message == null || message.records.all { it.tnf == NdefRecord.TNF_EMPTY }

    fun write(tag: Tag, tagId: String, overwrite: Boolean): WriteResult = try {
        val msg = message(tagId)
        val ndef = Ndef.get(tag)
        val formatable = NdefFormatable.get(tag)
        when {
            ndef != null -> ndef.use {
                it.connect()
                val existing = it.ndefMessage
                when {
                    !it.isWritable -> WriteResult.NotWritable
                    it.maxSize < msg.byteArrayLength -> WriteResult.TooSmall
                    !overwrite && !isEmpty(existing) && !isOurs(existing) -> WriteResult.HasOtherData
                    else -> { it.writeNdefMessage(msg); WriteResult.Written(tagId) }
                }
            }
            formatable != null -> formatable.use { it.connect(); it.format(msg); WriteResult.Written(tagId) }
            else -> WriteResult.NotWritable
        }
    } catch (e: Exception) {
        WriteResult.Failed(e.message ?: e.javaClass.simpleName)
    }
}
