package com.habittags.app.ui

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.habittags.app.nfc.TagIoFactory
import com.habittags.app.nfc.WriteResult

/** Dialog that waits for a tag, writes [tagId], asks before overwriting other data, and reports the result. */
@Composable
fun TagWriteDialog(tagId: String, onWritten: () -> Unit, onCancel: () -> Unit) {
    val activity = LocalContext.current as Activity
    val io = remember { TagIoFactory.create(activity) }
    var overwrite by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<WriteResult?>(null) }

    LaunchedEffect(attempt, overwrite) {
        result = null
        result = io?.awaitWrite(tagId, overwrite) ?: WriteResult.Failed("This phone can't write NFC tags.")
        if (result is WriteResult.Written) onWritten()
    }

    val message = when (val r = result) {
        null -> "Hold your phone to a blank tag."
        is WriteResult.Written -> "Tag ready. Tap it to start."
        WriteResult.HasOtherData -> "This tag already has something else on it. Overwrite it?"
        WriteResult.NotWritable -> "This tag is locked or not supported. Try another tag."
        WriteResult.TooSmall -> "This tag is too small. NTAG213, 215 or 216 tags work best."
        is WriteResult.Failed -> "Couldn't write the tag: ${r.message}. Try again."
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Write tag") },
        text = { Text(message) },
        confirmButton = {
            when (result) {
                WriteResult.HasOtherData -> TextButton(onClick = { overwrite = true; attempt++ }) { Text("Overwrite") }
                null, is WriteResult.Written -> Unit
                else -> TextButton(onClick = { attempt++ }) { Text("Try again") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
