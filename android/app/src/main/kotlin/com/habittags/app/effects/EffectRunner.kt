package com.habittags.app.effects

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log as AndroidLog
import androidx.core.app.NotificationCompat
import com.habittags.app.HabitTagsApp
import com.habittags.app.R
import com.habittags.app.ui.MainActivity
import com.habittags.core.recipe.Action
import com.habittags.core.recipe.Checklist
import com.habittags.core.recipe.Log
import com.habittags.core.recipe.OpenApp
import com.habittags.core.recipe.Recipe
import com.habittags.core.recipe.Timer
import com.habittags.core.recipe.bindInt
import com.habittags.core.store.TapLogEntry

object EffectRunner {
    fun run(context: Context, effects: List<Action>, recipe: Recipe?, params: Map<String, Int>) {
        for (effect in effects) {
            runCatching { runOne(context, effect, recipe, params) }
                .onFailure { AndroidLog.w("EffectRunner", "Effect $effect failed", it) }
        }
    }

    private fun runOne(context: Context, effect: Action, recipe: Recipe?, params: Map<String, Int>) {
        when (effect) {
            is Timer -> {
                val minutes = bindInt(effect.minutes, recipe?.params.orEmpty(), params) ?: return
                scheduleTimer(context, minutes, recipe?.title ?: "Timer")
            }
            is Checklist -> context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_CHECKLIST_TITLE, effect.title)
                    .putExtra(MainActivity.EXTRA_CHECKLIST_ITEMS, effect.items.toTypedArray())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            is OpenApp -> {
                val intent = effect.androidPackage?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    ?: effect.uri?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
                    ?: return
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            is Log -> HabitTagsApp.from(context).update {
                it.copy(tapLog = (it.tapLog + TapLogEntry(System.currentTimeMillis(), "", effect.label)).takeLast(500))
            }
            else -> Unit
        }
    }

    /** Inexact by design: a habit timer within a minute is fine and avoids the exact-alarm permission. */
    private fun scheduleTimer(context: Context, minutes: Int, title: String) {
        val intent = Intent(context, TimerReceiver::class.java).putExtra(TimerReceiver.EXTRA_TITLE, title)
        val pending = PendingIntent.getBroadcast(context, title.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val at = System.currentTimeMillis() + minutes * 60_000L
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pending)
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Timers", NotificationManager.IMPORTANCE_HIGH))
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Timer"
        nm.notify(
            title.hashCode(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tag)
                .setContentTitle("Timer done")
                .setContentText(title)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val EXTRA_TITLE = "title"
        private const val CHANNEL = "timers"
    }
}
