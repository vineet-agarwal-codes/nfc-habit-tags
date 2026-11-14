package com.habittags.core.tag

import java.security.SecureRandom
import java.util.Base64

object TagPayload {
    const val EXTERNAL_DOMAIN = "com.habittags.app"
    const val EXTERNAL_TYPE = "tag"
    private val VALID = Regex("^[A-Za-z0-9_-]{22}$")

    fun newTagId(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun parse(payload: ByteArray): String? = String(payload, Charsets.US_ASCII).takeIf(VALID::matches)
}
