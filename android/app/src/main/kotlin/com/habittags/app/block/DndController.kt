package com.habittags.app.block

import android.app.NotificationManager
import android.content.Context

object DndController {
    private const val PREFS = "dnd"
    private const val KEY_SET_BY_US = "setByUs"

    /** Turns DND on for active modes; only turns it off again if we were the ones who turned it on. */
    fun apply(context: Context, wanted: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val setByUs = prefs.getBoolean(KEY_SET_BY_US, false)
        if (wanted && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            prefs.edit().putBoolean(KEY_SET_BY_US, true).apply()
        } else if (!wanted && setByUs) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            prefs.edit().putBoolean(KEY_SET_BY_US, false).apply()
        }
    }
}
