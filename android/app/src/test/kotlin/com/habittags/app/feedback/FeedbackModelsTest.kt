package com.habittags.app.feedback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackModelsTest {
    private fun msg(id: String, text: String? = "hi", attachments: List<Attachment> = emptyList()) =
        FeedbackMessage(id, 1000, Sender.User, text, attachments)

    @Test
    fun firstMessageOfASessionGetsOneReply() {
        val cases = listOf(
            // first of session?, expected bubbles after adding
            true to listOf(Sender.User, Sender.Mise),
            false to listOf(Sender.User),
        )
        for ((first, expected) in cases) {
            val t = FeedbackThread().add(msg("a"), first, "r")
            assertEquals(expected, t.messages.map { it.sender })
        }
        val reply = FeedbackThread().add(msg("a"), true, "r").messages.last()
        assertEquals(REPLY, reply.text)
        assertTrue(reply.sent)
    }

    @Test
    fun pendingIsUnsentUserMessagesUntilMarkedSent() {
        val t = FeedbackThread().add(msg("a"), true, "r").add(msg("b"), false, "r2")
        assertEquals(listOf("a", "b"), t.pending().map { it.id })
        assertEquals(listOf("b"), t.markSent("a").pending().map { it.id })
    }

    @Test
    fun bodyCarriesTextAttachmentsUnderTheDeviceFolderAndMetadata() {
        val image = Attachment("/data/x/0.jpg", "0.jpg", "image", 1200)
        val voice = Attachment("/data/x/1.m4a", "1.m4a", "audio", 9000, durationMs = 4200)
        val body = Json.parseToJsonElement(feedbackBody("dev", msg("m", "x".repeat(5000), listOf(image, voice)), "1.0.0 (7)", "15", "Pixel 9", "home")).jsonObject
        assertEquals("m", body["p_id"]!!.jsonPrimitive.content)
        assertEquals(MAX_TEXT, body["p_text"]!!.jsonPrimitive.content.length)
        val atts = body["p_attachments"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("dev/m/0.jpg", "dev/m/1.m4a"), atts.map { it["path"]!!.jsonPrimitive.content })
        assertEquals("4200", atts[1]["duration_ms"]!!.jsonPrimitive.content)
        assertEquals("home", body["p_from_screen"]!!.jsonPrimitive.content)
    }

    @Test
    fun textOnlyMessageHasNullTextWhenEmptyAndNoAttachments() {
        val body = Json.parseToJsonElement(feedbackBody("dev", msg("m", null, listOf(Attachment("p", "0.jpg", "image", 1))), "v", "a", "d", "home")).jsonObject
        assertEquals("null", body["p_text"].toString())
    }
}
