package com.habittags.core.tag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagPayloadTest {
    @Test
    fun newIdsAreWellFormedAndDistinct() {
        val ids = List(1000) { TagPayload.newTagId() }
        val valid = Regex("^[A-Za-z0-9_-]{22}$")
        ids.forEach { assertTrue(valid.matches(it), it) }
        assertEquals(1000, ids.toSet().size)
    }

    @Test
    fun parse() {
        val id = TagPayload.newTagId()
        assertEquals(id, TagPayload.parse(id.encodeToByteArray()))
        for (bad in listOf("short", "x".repeat(22) + "!", "x".repeat(21) + "!")) {
            assertNull(TagPayload.parse(bad.encodeToByteArray()), bad)
        }
    }

    @Test
    fun urls() {
        val id = TagPayload.newTagId()
        assertEquals("https://vineet-agarwal-codes.github.io/t/$id", TagPayload.urlFor(id))
        val cases = mapOf(
            TagPayload.urlFor(id) to id,
            TagPayload.urlFor(id) + "/" to id,
            "https://example.com/t/$id" to null,
            "http://vineet-agarwal-codes.github.io/t/$id" to null,
            TagPayload.URL_PREFIX + "bad" to null,
            TagPayload.URL_PREFIX + id + "/x" to null,
        )
        for ((url, expected) in cases) assertEquals(expected, TagPayload.parseUrl(url), url)
    }
}
