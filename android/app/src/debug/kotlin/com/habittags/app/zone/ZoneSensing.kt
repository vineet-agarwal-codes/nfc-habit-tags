package com.habittags.app.zone

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.habittags.app.DebugSettings
import com.habittags.core.zone.WifiReading
import com.habittags.core.zone.ZoneSample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sqrt

/** One Wi-Fi scan as read back: [fresh] = a new scan completed; [startScanReturned] = Android accepted the request. */
data class WifiScan(val readings: List<WifiReading>, val ageMs: Long?, val fresh: Boolean, val startScanReturned: Boolean)

interface WifiScanSource {
    suspend fun scan(): WifiScan
}

/** Real scans. Android allows about 4 per 2 minutes per app; refused requests return the cached list. */
class RealWifiScanSource(private val context: Context) : WifiScanSource {
    @SuppressLint("MissingPermission")
    override suspend fun scan(): WifiScan {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val done = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                done.complete(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false))
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        @Suppress("DEPRECATION")
        val accepted = wifi.startScan()
        if (accepted) ScanBudget.noteAccepted(context)
        val fresh = try {
            accepted && (withTimeoutOrNull(SCAN_TIMEOUT_MS) { done.await() } ?: false)
        } finally {
            context.unregisterReceiver(receiver)
        }
        val results = wifi.scanResults.orEmpty()
        val home = connectedSsid(wifi)
        val nowUs = SystemClock.elapsedRealtime() * 1000
        val newestUs = results.maxOfOrNull { it.timestamp }
        return WifiScan(
            readings = results.map { WifiReading(it.BSSID, it.level, it.frequency, h = home != null && it.SSID == home) },
            ageMs = newestUs?.let { (nowUs - it) / 1000 },
            fresh = fresh,
            startScanReturned = accepted,
        )
    }

    /** The network the phone is connected to (readable with the lab's location permission), or null. */
    @Suppress("DEPRECATION")
    private fun connectedSsid(wifi: WifiManager): String? =
        wifi.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }

    private companion object {
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}

/** Emulator stand-in (habittags-debug://zone-fake/<spot>): a stable fingerprint per spot name with a little noise. */
class FakeWifiScanSource(private val spot: String) : WifiScanSource {
    override suspend fun scan(): WifiScan {
        delay(300)
        val seed = spot.lowercase().hashCode()
        val readings = (0 until 6).map { i ->
            val base = -40 - Math.floorMod(seed * (i + 7), 50)
            WifiReading("02:00:00:00:00:%02x".format(i), base + (-2..2).random(), if (i % 2 == 0) 2412 else 5180)
        }
        return WifiScan(readings, ageMs = 200, fresh = true, startScanReturned = true)
    }
}

object ZoneSensing {
    fun scanSource(context: Context): WifiScanSource =
        DebugSettings.zoneFakeSpot(context)?.let(::FakeWifiScanSource) ?: RealWifiScanSource(context)

    /** One sample: a Wi-Fi scan plus ~2 s of magnetometer, gravity and light readings. */
    suspend fun sample(context: Context, scan: WifiScan): ZoneSample {
        val sensors = readSensors(context)
        val battery = context.getSystemService(BatteryManager::class.java)
        return ZoneSample(
            wifi = scan.readings,
            scanAgeMs = scan.ageMs,
            scanFresh = scan.fresh,
            startScanReturned = scan.startScanReturned,
            magMean = sensors.magMean,
            magStd = sensors.magStd,
            gravity = sensors.gravity,
            light = sensors.light,
            charging = battery.isCharging,
            screenOn = context.getSystemService(PowerManager::class.java).isInteractive,
        )
    }

    private class SensorReadings(val magMean: Float?, val magStd: Float?, val gravity: List<Float>?, val light: Float?)

    private suspend fun readSensors(context: Context): SensorReadings {
        val sm = context.getSystemService(SensorManager::class.java)
        val mags = mutableListOf<Float>()
        val gravity = FloatArray(3)
        var gravityCount = 0
        var light: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_MAGNETIC_FIELD -> mags += sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                    Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                        for (i in 0..2) gravity[i] += e.values[i]
                        gravityCount++
                    }
                    Sensor.TYPE_LIGHT -> light = e.values[0]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        listOfNotNull(sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), gravitySensor, sm.getDefaultSensor(Sensor.TYPE_LIGHT))
            .forEach { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        try {
            delay(READ_MS)
        } finally {
            sm.unregisterListener(listener)
        }
        val mean = mags.takeIf { it.isNotEmpty() }?.average()
        val std = mean?.let { m -> sqrt(mags.sumOf { (it - m) * (it - m) } / mags.size) }
        return SensorReadings(
            magMean = mean?.toFloat(),
            magStd = std?.toFloat(),
            gravity = if (gravityCount > 0) gravity.map { it / gravityCount } else null,
            light = light,
        )
    }

    private const val READ_MS = 2_000L
}
