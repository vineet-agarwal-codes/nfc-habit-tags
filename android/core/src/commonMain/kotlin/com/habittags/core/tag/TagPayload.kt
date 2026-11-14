package com.habittags.core.tag

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Cryptographically secure random bytes from the platform. */
expect fun secureRandomBytes(size: Int): ByteArray

object TagPayload {
    const val EXTERNAL_DOMAIN = "com.habittags.app"
    const val EXTERNAL_TYPE = "tag"
    private val VALID = Regex("^[A-Za-z0-9_-]{22}$")

    @OptIn(ExperimentalEncodingApi::class)
    fun newTagId(): String = Base64.UrlSafe.encode(secureRandomBytes(16)).trimEnd('=')

    fun parse(payload: ByteArray): String? = payload.decodeToString().takeIf(VALID::matches)
}
