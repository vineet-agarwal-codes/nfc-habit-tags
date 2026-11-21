package com.habittags.app.backend

import com.habittags.core.catalog.CatalogSnapshot
import com.habittags.core.catalog.decodeRemoteCatalog
import com.habittags.core.usage.UsageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

interface Backend {
    suspend fun fetchCatalog(): CatalogSnapshot
    suspend fun report(deviceId: String, events: List<UsageEvent>)
    val supportsRealtime: Boolean

    /** Uploads one feedback attachment to [path] in the private feedback bucket; an existing file counts as done. */
    suspend fun uploadFeedbackFile(path: String, bytes: ByteArray, contentType: String)

    /** Calls submit_feedback with the JSON [body] (see feedbackBody). */
    suspend fun submitFeedback(body: String)
}

fun reportBody(deviceId: String, events: List<UsageEvent>): String = buildJsonObject {
    put("device_id", JsonPrimitive(deviceId))
    put("events", Json.encodeToJsonElement(ListSerializer(UsageEvent.serializer()), events))
}.toString()

class SupabaseBackend(private val baseUrl: String, private val key: String, private val client: OkHttpClient) : Backend {
    override val supportsRealtime = true

    private fun get(path: String): String {
        val request = Request.Builder().url("$baseUrl$path").header("apikey", key).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GET $path → ${response.code}")
            return response.body?.string() ?: throw IOException("GET $path: empty body")
        }
    }

    override suspend fun fetchCatalog(): CatalogSnapshot = withContext(Dispatchers.IO) {
        val recipes = get("/rest/v1/recipes?select=id,doc,published_at,sort_hint&published=is.true")
        val groups = get("/rest/v1/app_groups?select=id,doc")
        val stats = runCatching { get("/rest/v1/recipe_stats?select=recipe_id,weekly_active,installs") }.getOrNull()
        decodeRemoteCatalog(recipes, groups, stats, System.currentTimeMillis())
    }

    override suspend fun uploadFeedbackFile(path: String, bytes: ByteArray, contentType: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/storage/v1/object/feedback/$path")
            .header("apikey", key)
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            // A retry after a lost response finds the file already there: that's success.
            val duplicate = response.code == 400 && response.body?.string().orEmpty().contains("Duplicate")
            if (!response.isSuccessful && !duplicate) throw IOException("feedback upload → ${response.code}")
        }
    }

    override suspend fun submitFeedback(body: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/submit_feedback")
            .header("apikey", key)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("submit_feedback → ${response.code}")
        }
    }

    override suspend fun report(deviceId: String, events: List<UsageEvent>) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/report_events")
            .header("apikey", key)
            .post(reportBody(deviceId, events).toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("report_events → ${response.code}")
        }
    }
}
