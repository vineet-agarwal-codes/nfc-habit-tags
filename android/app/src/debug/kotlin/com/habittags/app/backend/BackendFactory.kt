package com.habittags.app.backend

import android.content.Context
import com.habittags.app.BuildConfig
import com.habittags.app.DebugSettings
import com.habittags.core.catalog.CatalogSnapshot
import com.habittags.core.catalog.decodeRemoteCatalog
import com.habittags.core.usage.UsageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.days

/** Debug builds can run against bundled fixtures or simulate no network, so CI never touches production. */
object BackendFactory {
    fun create(context: Context, client: OkHttpClient): Backend {
        val real = SupabaseBackend(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, client)
        return object : Backend {
            private fun current(): Backend = when (DebugSettings.backendMode(context)) {
                "fixture" -> FixtureBackend(context)
                "offline" -> OfflineBackend
                else -> real
            }
            override suspend fun fetchCatalog() = current().fetchCatalog()
            override suspend fun report(deviceId: String, events: List<UsageEvent>) = current().report(deviceId, events)
            override val supportsRealtime get() = current().supportsRealtime
        }
    }
}

private class FixtureBackend(private val context: Context) : Backend {
    override val supportsRealtime = false

    private fun asset(name: String) = context.assets.open("fixture_backend/$name").bufferedReader().use { it.readText() }

    override suspend fun fetchCatalog(): CatalogSnapshot = withContext(Dispatchers.IO) {
        val recent = (Clock.System.now() - 2.days).toString()
        decodeRemoteCatalog(asset("recipes.json").replace("\"RECENT\"", "\"$recent\""), asset("app_groups.json"), asset("recipe_stats.json"), System.currentTimeMillis())
    }

    override suspend fun report(deviceId: String, events: List<UsageEvent>) = withContext(Dispatchers.IO) {
        File(context.filesDir, "debug_reports.jsonl").appendText(reportBody(deviceId, events) + "\n")
    }
}

private object OfflineBackend : Backend {
    override val supportsRealtime = false
    override suspend fun fetchCatalog(): CatalogSnapshot = throw IOException("offline (debug)")
    override suspend fun report(deviceId: String, events: List<UsageEvent>) = throw IOException("offline (debug)")
}
