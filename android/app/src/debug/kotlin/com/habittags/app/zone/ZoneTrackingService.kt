package com.habittags.app.zone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.habittags.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lab tracking: after the step detector sees walking and the phone then stays still for
 * [STILL_MS], take a sample and guess. About hourly, ask "Are you at X?". Phones without a step
 * detector sample every [FALLBACK_MS]. Runs as a location foreground service started from the app.
 */
class ZoneTrackingService : LifecycleService(), SensorEventListener {
    @Volatile private var lastStepMs = 0L
    @Volatile private var moved = false
    private var loop: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, ongoing(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: SecurityException) {
            // A location service may only start while the app is on screen; the switch shows it as off.
            Log.w(TAG, "can't start lab tracking from the background", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) start()
        // Not sticky: Android won't let a location service restart itself from the background.
        return START_NOT_STICKY
    }

    private fun start() {
        running = true
        ZoneLabStore.load(this)
        val sm = getSystemService(SensorManager::class.java)
        val stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        stepSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        loop = lifecycleScope.launch {
            var lastSampleMs = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                val settled = moved && now - lastStepMs >= STILL_MS
                val fallbackDue = stepSensor == null && now - lastSampleMs >= FALLBACK_MS
                if (settled || fallbackDue) {
                    moved = false
                    lastSampleMs = now
                    runCatching { sampleAndMaybeAsk() }.onFailure { Log.w(TAG, "tracking sample failed", it) }
                }
                delay(TICK_MS)
            }
        }
    }

    private suspend fun sampleAndMaybeAsk() {
        val obs = ZoneLab.observe(this, "tracking")
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val guess = obs.guess
        if (guess != null && now - prefs.getLong(KEY_LAST_ASK, 0) >= ASK_EVERY_MS) {
            prefs.edit().putLong(KEY_LAST_ASK, now).apply()
            ask(obs.id, guess.spot)
        }
        if (ZoneLabStore.pending.value >= UPLOAD_BATCH) ZoneLabStore.upload(this)
    }

    private fun ask(sampleId: String, spot: String) {
        val yes = PendingIntent.getBroadcast(
            this, sampleId.hashCode(),
            Intent(this, ZoneFeedbackReceiver::class.java).putExtra(EXTRA_ID, sampleId).putExtra(EXTRA_GUESS, spot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val no = PendingIntent.getActivity(
            this, sampleId.hashCode() + 1,
            Intent(this, ZoneLabActivity::class.java).putExtra(EXTRA_ID, sampleId).putExtra(EXTRA_GUESS, spot)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ASK_CHANNEL)
            .setSmallIcon(R.drawable.ic_tag)
            .setContentTitle("Are you at $spot?")
            .setContentText("Zone lab is checking its guess.")
            .setAutoCancel(true)
            .addAction(0, "Yes", yes)
            .addAction(0, "No", no)
            .build()
        getSystemService(NotificationManager::class.java).notify(ASK_NOTIFICATION_ID, notification)
    }

    private fun ongoing() = NotificationCompat.Builder(this, ONGOING_CHANNEL)
        .setSmallIcon(R.drawable.ic_tag)
        .setContentTitle("Zone lab tracking")
        .setContentText("Samples your spot when you stop moving.")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, ZoneLabActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    override fun onSensorChanged(event: SensorEvent) {
        lastStepMs = System.currentTimeMillis()
        moved = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        running = false
        getSystemService(SensorManager::class.java).unregisterListener(this)
        loop?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "zone_sample_id"
        const val EXTRA_GUESS = "zone_guess"
        private const val ACTION_STOP = "stop"
        private const val TAG = "ZoneTracking"
        private const val PREFS = "zone_lab"
        private const val KEY_LAST_ASK = "lastAsk"
        private const val ONGOING_CHANNEL = "zone_lab"
        private const val ASK_CHANNEL = "zone_lab_ask"
        private const val NOTIFICATION_ID = 4101
        const val ASK_NOTIFICATION_ID = 4102
        private const val STILL_MS = 20_000L
        private const val FALLBACK_MS = 15 * 60_000L
        private const val TICK_MS = 5_000L
        private const val ASK_EVERY_MS = 55 * 60_000L
        private const val UPLOAD_BATCH = 10

        @Volatile var running = false
            private set

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(ONGOING_CHANNEL, "Zone lab tracking", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(ASK_CHANNEL, "Zone lab questions", NotificationManager.IMPORTANCE_DEFAULT))
        }

        fun start(context: Context) {
            createChannels(context)
            ContextCompat.startForegroundService(context, Intent(context, ZoneTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ZoneTrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}
