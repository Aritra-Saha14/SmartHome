package com.semhas.app.data.supabase

import com.semhas.app.BuildConfig

object SupabaseConfig {
    const val DEVICE_CODE = "SEMHAS-001"
    val URL: String = BuildConfig.SUPABASE_URL.trim()
    val ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = URL.isNotBlank() && ANON_KEY.isNotBlank()
}
