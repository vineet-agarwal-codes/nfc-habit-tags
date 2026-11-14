package com.habittags.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tagId = intent.getStringExtra("tagId") ?: return
        TapHandler.handle(context, tagId)
    }
}
