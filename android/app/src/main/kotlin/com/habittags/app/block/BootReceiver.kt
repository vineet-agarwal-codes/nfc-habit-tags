package com.habittags.app.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores enforcement after a reboot or an app update (both kill the service). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in RESTORE_ACTIONS) BlockController.sync(context)
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
