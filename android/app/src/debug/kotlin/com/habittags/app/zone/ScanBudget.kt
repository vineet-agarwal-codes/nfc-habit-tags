package com.habittags.app.zone

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Android lets an app start at most 4 Wi-Fi scans per 2 minutes (unless the user turned throttling off
 * in developer options). The lab remembers when its scans were accepted so a recording can wait until
 * all 4 scans are available up front, instead of stalling halfway.
 */
object ScanBudget {
    const val MAX_SCANS = 4
    const val WINDOW_MS = 2 * 60_000L
    private const val PREFS = "zone_scan_budget"
    private const val KEY = "acceptedAt"

    /** True while a recording runs; tracking skips its scans so it can't eat the recording's budget. */
    @Volatile var recording = false

    fun noteAccepted(context: Context, atMs: Long = System.currentTimeMillis()) {
        val kept = (times(context) + atMs).filter { atMs - it < WINDOW_MS }.takeLast(MAX_SCANS)
        prefs(context).edit().putString(KEY, kept.joinToString(",")).apply()
    }

    /** Milliseconds until [needed] scans can run back to back; 0 when they can start now. */
    fun waitFor(context: Context, needed: Int = MAX_SCANS, nowMs: Long = System.currentTimeMillis()): Long {
        if (!throttled(context)) return 0
        val recent = times(context).filter { nowMs - it < WINDOW_MS }.sorted()
        val mustExpire = recent.size + needed - MAX_SCANS
        if (mustExpire <= 0) return 0
        return (recent[mustExpire - 1] + WINDOW_MS - nowMs).coerceAtLeast(0)
    }

    private fun throttled(context: Context): Boolean =
        Build.VERSION.SDK_INT < 30 || context.getSystemService(WifiManager::class.java).isScanThrottleEnabled

    private fun times(context: Context): List<Long> =
        prefs(context).getString(KEY, "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
