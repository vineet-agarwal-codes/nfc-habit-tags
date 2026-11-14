package com.habittags.core.tag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TagPayloadTest {
    @Test
    fun newIdsAreWellFormedAndDistinct() {
        val ids = List(1000) { TagPayload.newTagId() }
        ids.forEach { assertEquals(22, it.length); assert(Regex("^[A-Za-z0-9_-]{22}$").matches(it)) }
        assertEquals(1000, ids.toSet().size)
    }

    @Test
    fun parse() {
        val id = TagPayload.newTagId()
        assertEquals(id, TagPayload.parse(id.toByteArray(Charsets.US_ASCII)))
        assertNull(TagPayload.parse("short".toByteArray()))
        assertNull(TagPayload.parse(("x".repeat(22) + "!").toByteArray()))
        assertNull(TagPayload.parse(("x".repeat(21) + "!").toByteArray()))
    }
}
