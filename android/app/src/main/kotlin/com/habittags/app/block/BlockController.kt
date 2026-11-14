package com.habittags.app.block

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.habittags.app.HabitTagsApp

object BlockController {
    /** Starts the blocking service while any mode is active; otherwise stops it and releases DND. */
    fun sync(context: Context) {
        ModeExpiry.scheduleNext(context)
        val intent = Intent(context, BlockService::class.java)
        if (HabitTagsApp.from(context).state.value.active.isEmpty()) {
            context.stopService(intent)
            DndController.apply(context, false)
            return
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            Log.w("BlockController", "Could not start blocking service", e)
        }
    }
}
