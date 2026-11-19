package com.habittags.app.block

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.habittags.app.HabitTagsApp
import com.habittags.app.R
import com.habittags.app.TapHandler
import com.habittags.app.ui.MainActivity
import com.habittags.core.mode.ActiveMode
import com.habittags.core.mode.ModeEngine
import com.habittags.core.store.TapLogEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps active modes enforced: expires them on time, applies DND, and covers blocked apps with an overlay. */
class BlockService : LifecycleService() {
    private var loop: Job? = null
    private var lastQueryMs = 0L
    private var foregroundPackage: String? = null
    private var lastLoggedFg: String? = null
    /** The last foreground app we counted a reach for, so staying in it doesn't count again. */
    private var lastReachFg: String? = null
    private lateinit var overlay: BlockOverlay

    override fun onCreate() {
        super.onCreate()
        overlay = BlockOverlay(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(HabitTagsApp.from(this).state.value.active), type)
        if (loop?.isActive != true) loop = lifecycleScope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val app = HabitTagsApp.from(this)
        lastQueryMs = System.currentTimeMillis() - LOOKBACK_MS
        var shown: Set<String>? = null
        while (lifecycleScope.isActive) {
            val now = System.currentTimeMillis()
            ModeExpiry.expireNow(this)
            val remaining = app.state.value.active
            if (remaining.isEmpty()) {
                overlay.hide()
                DndController.apply(this, false)
                stopSelf()
                return
            }
            if (remaining.keys != shown) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(remaining))
                shown = remaining.keys
            }
            DndController.apply(this, ModeEngine.dndWanted(remaining))
            val fg = currentForegroundPackage(now)
            val blocking = remaining.values.filter { fg != null && fg != packageName && fg in it.blockedPackages }
            if (fg != lastLoggedFg) {
                Log.d(TAG, "foreground=$fg blocking=${blocking.map { it.tagId }}")
                lastLoggedFg = fg
            }
            if (fg != lastReachFg) {
                lastReachFg = fg
                if (blocking.isNotEmpty() && fg != null) recordReach(fg, blocking)
            }
            if (blocking.isEmpty()) overlay.hide() else showOverlay(blocking)
            delay(POLL_MS)
        }
    }

    private fun currentForegroundPackage(now: Long): String? {
        val usm = getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(lastQueryMs, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) foregroundPackage = event.packageName
        }
        lastQueryMs = now
        return foregroundPackage
    }

    /** Counts one reach on each running mode that blocks [pkg]; reported later as a daily count, never the app. */
    private fun recordReach(pkg: String, modes: List<ActiveMode>) {
        val app = HabitTagsApp.from(this)
        val now = System.currentTimeMillis()
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault().id
        app.update { s ->
            s.copy(
                active = ModeEngine.recordReach(s.active, pkg),
                reachLog = (s.reachLog + modes.map { TapLogEntry(now, it.tagId, "", it.recipeId, zone, "reach") }).takeLast(MAX_REACH_LOG),
            )
        }
    }

    private fun showOverlay(modes: List<ActiveMode>) {
        val app = HabitTagsApp.from(this)
        // Re-read: the reach just recorded should show at once.
        val live = modes.map { app.state.value.active[it.tagId] ?: it }
        val first = live.first()
        val ends = live.mapNotNull { it.autoOffAt }.maxOrNull()
        val reaches = live.maxOf { it.reaches }
        overlay.show(
            colour = app.spaceColour(first.recipeId),
            title = live.joinToString(" and ") { app.modeLabel(it) } + " is on",
            detail = listOfNotNull(
                ends?.let { "ends ${TapHandler.formatTime(it)}" },
                reaches.takeIf { it > 0 }?.let { com.habittags.app.ui.components.reachText(it) },
            ).joinToString("\n"),
        )
    }

    private fun notification(active: Map<String, ActiveMode>): Notification {
        val app = HabitTagsApp.from(this)
        val text = active.values.joinToString(", ") { app.modeLabel(it) }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tag)
            .setContentTitle("Habit mode on")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Active modes", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        overlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val MAX_REACH_LOG = 2_000
        private const val TAG = "BlockService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "active_modes"
        private const val POLL_MS = 1_000L
        private const val LOOKBACK_MS = 60 * 60_000L
    }
}
