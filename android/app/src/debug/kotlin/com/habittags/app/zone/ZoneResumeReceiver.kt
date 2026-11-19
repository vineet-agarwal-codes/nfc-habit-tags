package com.habittags.app.zone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Turns lab tracking back on after an app update or a reboot if the user had left it on. */
class ZoneResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ZoneTrackingService.resumeIfWanted(context)
        }
    }
}
