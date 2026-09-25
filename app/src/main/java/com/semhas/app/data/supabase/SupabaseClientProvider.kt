package com.semhas.app.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL.ifBlank { "https://placeholder.supabase.co" },
            supabaseKey = SupabaseConfig.ANON_KEY.ifBlank { "placeholder-key" }
        ) {
            install(Postgrest)
            install(Realtime)
        }
    }
}
