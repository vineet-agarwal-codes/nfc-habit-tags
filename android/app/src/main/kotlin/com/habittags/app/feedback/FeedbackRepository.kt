package com.habittags.app.feedback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.habittags.app.BuildConfig
import com.habittags.app.HabitTagsApp
import com.habittags.core.store.withDeviceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * The feedback conversation on this phone: kept in a file so past messages stay visible, and sent
 * to Supabase in the background (WorkManager) so it survives being offline or the app closing.
 */
object FeedbackRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _thread = MutableStateFlow<FeedbackThread?>(null)
    private val sending = Mutex()

    private fun dir(context: Context) = File(context.filesDir, "feedback").apply { mkdirs() }
    private fun file(context: Context) = File(dir(context), "thread.json")

    fun thread(context: Context): StateFlow<FeedbackThread?> {
        if (_thread.value == null) {
            _thread.value = runCatching { json.decodeFromString(FeedbackThread.serializer(), file(context).readText()) }.getOrNull()
                ?: FeedbackThread()
        }
        return _thread.asStateFlow()
    }

    @Synchronized
    private fun update(context: Context, fn: (FeedbackThread) -> FeedbackThread) {
        val next = fn(thread(context).value ?: FeedbackThread())
        val tmp = File(dir(context), "thread.json.tmp")
        tmp.writeText(json.encodeToString(FeedbackThread.serializer(), next))
        tmp.renameTo(file(context))
        _thread.value = next
    }

    /** A folder for a new message's files; the voice recorder writes straight into it. */
    fun newMessageDir(context: Context, id: String) = File(dir(context), "files/$id").apply { mkdirs() }

    /** Adds the user's message (images are shrunk and copied in first) and schedules sending. */
    suspend fun send(
        context: Context,
        id: String,
        text: String?,
        images: List<Uri>,
        voice: Attachment?,
        firstOfSession: Boolean,
    ) {
        val attachments = withContext(Dispatchers.IO) {
            images.take(MAX_IMAGES).mapIndexedNotNull { i, uri -> saveImage(context, uri, newMessageDir(context, id), "image-$i.jpg") }
        } + listOfNotNull(voice)
        val body = text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
        if (body == null && attachments.isEmpty()) return
        val message = FeedbackMessage(id, System.currentTimeMillis(), Sender.User, body, attachments)
        update(context) { it.add(message, firstOfSession, UUID.randomUUID().toString()) }
        enqueue(context)
    }

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<FeedbackWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("feedback-send", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Uploads each pending message's files, then submits it; true when nothing is left pending. */
    suspend fun sendPending(context: Context): Boolean = sending.withLock {
        val app = HabitTagsApp.from(context)
        val deviceId = app.update { it.withDeviceId { UUID.randomUUID().toString() } }.deviceId!!
        for (message in thread(context).value?.pending().orEmpty()) {
            val ok = runCatching {
                for (a in message.attachments) {
                    val type = if (a.type == "audio") "audio/mp4" else "image/jpeg"
                    app.backend.uploadFeedbackFile(remotePath(deviceId, message.id, a), File(a.localPath).readBytes(), type)
                }
                app.backend.submitFeedback(
                    feedbackBody(
                        deviceId, message,
                        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        androidVersion = Build.VERSION.RELEASE,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        fromScreen = "home",
                    ),
                )
            }.onFailure { Log.w("Feedback", "send failed; will retry", it) }.isSuccess
            if (!ok) return@withLock false
            update(context) { it.markSent(message.id) }
        }
        true
    }

    /** Decodes (honouring rotation on Android 9+), shrinks to at most 1600px and saves a JPEG under 5 MB. */
    private fun saveImage(context: Context, uri: Uri, folder: File, name: String): Attachment? = runCatching {
        val bitmap = decode(context, uri, 1600) ?: return null
        var quality = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 15
        } while (bytes.size > 5_000_000 && quality > 30)
        val out = File(folder, name).apply { writeBytes(bytes) }
        Attachment(out.absolutePath, name, "image", bytes.size.toLong())
    }.onFailure { Log.w("Feedback", "couldn't read image", it) }.getOrNull()

    private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
        return if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
    }
}

class FeedbackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (FeedbackRepository.sendPending(applicationContext)) Result.success() else Result.retry()
}
