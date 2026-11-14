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
}
