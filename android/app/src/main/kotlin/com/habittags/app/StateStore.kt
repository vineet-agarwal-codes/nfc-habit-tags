package com.habittags.app

import com.habittags.core.store.AppState
import com.habittags.core.store.StateCodec
import java.io.File

/** Android file I/O around the shared [StateCodec]; writes via a temp file so a crash can't leave half a file. */
class StateStore(private val file: File) {
    fun load(): AppState = StateCodec.decode(file.takeIf { it.exists() }?.readText())

    fun save(state: AppState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(StateCodec.encode(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save state to $file" }
        }
    }
}
