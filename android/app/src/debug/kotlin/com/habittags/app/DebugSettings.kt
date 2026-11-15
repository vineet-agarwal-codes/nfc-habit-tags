package com.habittags.app

import android.content.Context

/** Debug-only knobs used by the emulator tests. */
object DebugSettings {
    private const val PREFS = "debug"

    fun gateWords(context: Context): Int? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("gateWords", 0).takeIf { it > 0 }

    fun setGateWords(context: Context, words: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("gateWords", words).commit()
    }

    /** "real" (default), "fixture" or "offline"; see BackendFactory. */
    fun backendMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("backend", "real") ?: "real"

    fun setBackendMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("backend", mode).commit()
    }

    /** Spot name the fake Wi-Fi scanner pretends to be at, or null for real scans. */
    fun zoneFakeSpot(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("zoneFake", null)

    fun setZoneFakeSpot(context: Context, spot: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("zoneFake", spot).commit()
    }
}
