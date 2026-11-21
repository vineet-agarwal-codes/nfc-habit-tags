package com.habittags.app.feedback

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

enum class Sender { User, Mise }

/** A file attached to a message, stored on the phone at [localPath] and uploaded to [remoteName]. */
@Serializable
data class Attachment(val localPath: String, val remoteName: String, val type: String, val bytes: Long, val durationMs: Long = 0)

/** One chat bubble. User messages are sent to Mise; [sent] turns true once the server accepted them. */
@Serializable
data class FeedbackMessage(
    val id: String,
    val atMs: Long,
    val sender: Sender,
    val text: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val sent: Boolean = false,
)

@Serializable
data class FeedbackThread(val messages: List<FeedbackMessage> = emptyList())

const val MAX_IMAGES = 5
const val MAX_TEXT = 4000
const val MAX_VOICE_MS = 120_000L

const val GREETING = "Tell us anything: what's working, what isn't, what you wish Mise did. We read every message and review feedback every week."
const val REPLY = "Thanks, got it. We'll look at this in our weekly review."

fun FeedbackThread.pending(): List<FeedbackMessage> = messages.filter { it.sender == Sender.User && !it.sent }

fun FeedbackThread.markSent(id: String) = copy(messages = messages.map { if (it.id == id) it.copy(sent = true) else it })

/**
 * Adds the user's message; the first message of a session also gets Mise's reply after it, so every
 * conversation is acknowledged once without repeating the same bubble under each message.
 */
fun FeedbackThread.add(message: FeedbackMessage, firstOfSession: Boolean, replyId: String): FeedbackThread {
    val reply = FeedbackMessage(replyId, message.atMs + 1, Sender.Mise, REPLY, sent = true)
    return copy(messages = messages + message + if (firstOfSession) listOf(reply) else emptyList())
}

/** Path in the feedback bucket: <device>/<message>/<file>; the server only accepts the device's own folder. */
fun remotePath(deviceId: String, messageId: String, attachment: Attachment) = "$deviceId/$messageId/${attachment.remoteName}"

/** JSON for the submit_feedback RPC. */
fun feedbackBody(
    deviceId: String,
    message: FeedbackMessage,
    appVersion: String,
    androidVersion: String,
    deviceModel: String,
    fromScreen: String,
): String = buildJsonObject {
    put("p_id", JsonPrimitive(message.id))
    put("p_device_id", JsonPrimitive(deviceId))
    put("p_text", JsonPrimitive(message.text?.take(MAX_TEXT)))
    put("p_attachments", buildJsonArray {
        message.attachments.forEach { a ->
            add(buildJsonObject {
                put("path", JsonPrimitive(remotePath(deviceId, message.id, a)))
                put("type", JsonPrimitive(a.type))
                put("bytes", JsonPrimitive(a.bytes))
                if (a.durationMs > 0) put("duration_ms", JsonPrimitive(a.durationMs))
            })
        }
    })
    put("p_app_version", JsonPrimitive(appVersion))
    put("p_android_version", JsonPrimitive(androidVersion))
    put("p_device_model", JsonPrimitive(deviceModel))
    put("p_from_screen", JsonPrimitive(fromScreen))
}.toString()
