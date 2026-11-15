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
}
