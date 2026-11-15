package com.habittags.app.zone

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Yes" on an "Are you at X?" notification. */
class ZoneFeedbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ZoneTrackingService.EXTRA_ID) ?: return
        val spot = intent.getStringExtra(ZoneTrackingService.EXTRA_GUESS) ?: return
        context.getSystemService(NotificationManager::class.java).cancel(ZoneTrackingService.ASK_NOTIFICATION_ID)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ZoneLab.answer(context.applicationContext, id, spot, spot, "notification")
            } finally {
                pending.finish()
            }
        }
    }
}
