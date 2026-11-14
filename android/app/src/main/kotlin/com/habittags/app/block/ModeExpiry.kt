package com.habittags.app.block

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habittags.app.HabitTagsApp
import com.habittags.app.effects.EffectRunner
import com.habittags.core.mode.ModeEngine

object ModeExpiry {
    /** Ends modes whose auto-off time has passed and runs their end actions. */
    fun expireNow(context: Context) {
        val app = HabitTagsApp.from(context)
        val active = app.state.value.active
        val (remaining, _) = ModeEngine.expire(active, System.currentTimeMillis(), app::recipe)
        if (remaining == active) return
        app.update { it.copy(active = remaining) }
        for (mode in active.values - remaining.values.toSet()) {
            val recipe = app.recipe(mode.recipeId)
            EffectRunner.run(context, recipe?.endActions.orEmpty(), recipe, app.state.value.tags[mode.tagId]?.params.orEmpty())
        }
    }

    /** The service loop stalls in Doze, so the earliest auto-off also gets an idle-allowed alarm. */
    fun scheduleNext(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, AutoOffReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = HabitTagsApp.from(context).state.value.active.values.mapNotNull { it.autoOffAt }.minOrNull()
        if (next == null) alarms.cancel(pending) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
    }
}

class AutoOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ModeExpiry.expireNow(context)
        BlockController.sync(context)
    }
}
