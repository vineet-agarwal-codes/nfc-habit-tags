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

    fun parse(payload: ByteArray): String? = parseText(payload.decodeToString())

    fun parseText(text: String): String? = text.takeIf(VALID::matches)

    /** The https link written to tags by the iPhone app (iOS only opens apps from https tag links). */
    const val URL_PREFIX = "https://vineet-agarwal-codes.github.io/t/"

    fun urlFor(tagId: String): String = URL_PREFIX + tagId

    fun parseUrl(url: String): String? =
        url.takeIf { it.startsWith(URL_PREFIX) }?.removePrefix(URL_PREFIX)?.trimEnd('/')?.let(::parseText)
}
