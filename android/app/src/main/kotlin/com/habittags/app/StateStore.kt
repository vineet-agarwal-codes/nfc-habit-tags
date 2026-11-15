package com.habittags.app

import com.habittags.core.store.AppState
import com.habittags.core.store.StateCodec
import android.util.Log
import java.io.File

/** Android file I/O around the shared [StateCodec]; writes via a temp file so a crash can't leave half a file. */
class StateStore(private val file: File) {
    /**
     * An unreadable file is moved aside rather than overwritten by the next save, so a bad read can't
     * permanently wipe the user's tags and active modes.
     */
    fun load(): AppState {
        val text = file.takeIf { it.exists() }?.readText() ?: return AppState()
        StateCodec.decodeOrNull(text)?.let { return it }
        val kept = File(file.path + ".bad-" + System.currentTimeMillis())
        Log.e("StateStore", "Unreadable state file; kept as ${kept.name}")
        file.renameTo(kept)
        return AppState()
    }

    fun save(state: AppState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(StateCodec.encode(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save state to $file" }
        }
    }
}
