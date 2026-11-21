package com.habittags.app.feedback

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.habittags.app.ui.components.QuietButton
import com.habittags.app.ui.components.ScreenPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val BubbleShape = RoundedCornerShape(20.dp)
private val ThumbShape = RoundedCornerShape(12.dp)

/** A chat with Mise: type, attach photos or hold to record a voice note. Nothing else to fill in. */
@Composable
fun FeedbackScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thread by FeedbackRepository.thread(context).collectAsState()
    var text by remember { mutableStateOf("") }
    var images by remember { mutableStateOf(listOf<Uri>()) }
    var sentThisSession by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(context) }
    var recordingId by remember { mutableStateOf<String?>(null) }
    var recordMs by remember { mutableLongStateOf(0L) }
    var micDenied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { recorder.release() } }
    LaunchedEffect(recordingId) {
        while (recordingId != null) {
            recordMs = recorder.elapsedMs()
            delay(200)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { picked ->
        images = (images + picked).distinct().take(MAX_IMAGES)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
    }

    fun send(voice: Attachment? = null, id: String = UUID.randomUUID().toString()) {
        val body = text
        val picked = images
        text = ""
        images = emptyList()
        val first = !sentThisSession
        sentThisSession = true
        scope.launch { FeedbackRepository.send(context, id, body, picked, voice, first) }
    }

    val messages = listOf(FeedbackMessage("greeting", 0, Sender.Mise, GREETING, sent = true)) + thread?.messages.orEmpty()
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { list.animateScrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Feedback", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            QuietButton("Close", onClose)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = ScreenPadding),
            state = list,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.id }) { Bubble(it) }
        }
        if (images.isNotEmpty()) {
            Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { uri ->
                    Box {
                        UriThumb(uri)
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .clickable { images = images - uri }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        when {
            recordingId != null -> Status("Recording ${clock(recordMs)} of 2:00. Let go to send.")
            micDenied -> Status("To send voice notes, allow the microphone for Mise in Settings.")
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComposerButton("Photo", enabled = images.size < MAX_IMAGES && recordingId == null) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            OutlinedTextField(
                text, { if (it.length <= MAX_TEXT) text = it },
                Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
            )
            if (text.isNotBlank() || images.isNotEmpty()) {
                ComposerButton("Send", filled = true) { send() }
            } else {
                // Hold to record, let go to send: the gesture messaging apps use.
                val holdLabel = if (recordingId != null) "Recording" else "Hold to talk"
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .semantics { contentDescription = "Hold to record a voice note" }
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }
                                val id = UUID.randomUUID().toString()
                                val out = File(FeedbackRepository.newMessageDir(context, id), "voice.m4a")
                                if (!recorder.start(out)) return@detectTapGestures
                                recordingId = id
                                tryAwaitRelease()
                                val note = recorder.stop("voice.m4a")
                                recordingId = null
                                if (note != null) send(note, id)
                            })
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(holdLabel, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Status(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = ScreenPadding))

@Composable
private fun ComposerButton(label: String, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.clip(RoundedCornerShape(24.dp)).background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
    ) { Text(label, color = fg, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun Bubble(message: FeedbackMessage) {
    val mine = message.sender == Sender.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                    BubbleShape,
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            val images = message.attachments.filter { it.type == "image" }
            if (images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { images.forEach { FileThumb(it.localPath) } }
            }
            message.attachments.firstOrNull { it.type == "audio" }?.let { VoicePlayer(it) }
            message.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
            if (mine && !message.sent) {
                Text("Sending…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VoicePlayer(note: Attachment) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(note.localPath) { onDispose { player?.release() } }
    val playing = player?.isPlaying == true
    Text(
        (if (playing) "Stop" else "Play") + " voice note · ${clock(note.durationMs)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
            val current = player
            if (current != null) {
                current.release()
                player = null
            } else {
                player = runCatching {
                    MediaPlayer().apply {
                        setDataSource(note.localPath)
                        setOnCompletionListener { it.release(); player = null }
                        prepare()
                        start()
                    }
                }.getOrNull()
            }
        }.padding(vertical = 4.dp),
    )
}

@Composable
private fun FileThumb(path: String) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { thumbnail { BitmapFactory.decodeFile(path, it) } }
    }
    Thumb(bitmap)
}

@Composable
private fun UriThumb(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            thumbnail { opts -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }
        }
    }
    Thumb(bitmap)
}

@Composable
private fun Thumb(bitmap: ImageBitmap?) {
    val m = Modifier.size(64.dp).clip(ThumbShape)
    if (bitmap != null) Image(bitmap, null, m, contentScale = ContentScale.Crop)
    else Box(m.background(MaterialTheme.colorScheme.surfaceVariant))
}

/** Decodes at a small size (twice, the first pass only for bounds) so thumbnails stay light. */
private fun thumbnail(decode: (BitmapFactory.Options) -> android.graphics.Bitmap?): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
    decode(BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

private fun clock(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
