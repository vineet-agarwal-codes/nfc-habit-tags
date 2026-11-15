package com.habittags.app.block

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import com.habittags.app.ui.MainActivity

/**
 * Modes request DND through the app's own automatic rule (API 29+), so Android merges it with the
 * user's own DND and schedules: when the rule deactivates, whatever DND state the user had is kept.
 */
object DndController {
    private const val PREFS = "dnd"
    private const val KEY_RULE_ID = "ruleId"
    private const val KEY_SET_BY_US = "setByUs"
    private val CONDITION_ID: Uri = Uri.parse("habittags://dnd")

    @Volatile private var lastApplied: Boolean? = null

    fun apply(context: Context, wanted: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) applyRule(context, nm, wanted) else applyLegacy(context, nm, wanted)
    }

    private fun applyRule(context: Context, nm: NotificationManager, wanted: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_RULE_ID, null)
        if (id == null || nm.getAutomaticZenRule(id) == null) {
            id = nm.addAutomaticZenRule(
                AutomaticZenRule(
                    "Habit Tags mode",
                    null,
                    ComponentName(context, MainActivity::class.java),
                    CONDITION_ID,
                    null,
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    true,
                ),
            )
            prefs.edit().putString(KEY_RULE_ID, id).apply()
            lastApplied = null
        }
        if (lastApplied == wanted) return
        val state = if (wanted) Condition.STATE_TRUE else Condition.STATE_FALSE
        nm.setAutomaticZenRuleState(id, Condition(CONDITION_ID, "Habit Tags mode", state))
        lastApplied = wanted
    }

    /** API 26–28: no per-app rule state, so only undo DND we turned on and the user hasn't changed since. */
    private fun applyLegacy(context: Context, nm: NotificationManager, wanted: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val setByUs = prefs.getBoolean(KEY_SET_BY_US, false)
        val current = nm.currentInterruptionFilter
        if (wanted && current == NotificationManager.INTERRUPTION_FILTER_ALL) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            prefs.edit().putBoolean(KEY_SET_BY_US, true).apply()
        } else if (!wanted && setByUs) {
            if (current == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            prefs.edit().putBoolean(KEY_SET_BY_US, false).apply()
        }
    }
}
