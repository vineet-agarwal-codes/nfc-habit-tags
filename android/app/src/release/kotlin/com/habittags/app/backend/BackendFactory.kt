package com.habittags.app.backend

import android.content.Context
import com.habittags.app.BuildConfig
import okhttp3.OkHttpClient

object BackendFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, client: OkHttpClient): Backend = SupabaseBackend(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, client)
}
