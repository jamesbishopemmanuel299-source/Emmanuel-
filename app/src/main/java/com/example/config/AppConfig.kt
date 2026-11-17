package com.example.config

import com.example.BuildConfig

object AppConfig {
    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val paystackPublicKey: String
        get() = BuildConfig.PAYSTACK_PUBLIC_KEY.trim()

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("YOUR_PROJECT_REF") &&
            supabaseAnonKey.isNotBlank() &&
            !supabaseAnonKey.contains("YOUR_SUPABASE_ANON_KEY")

    val isPaystackConfigured: Boolean
        get() = paystackPublicKey.startsWith("pk_") &&
            !paystackPublicKey.contains("YOUR_PAYSTACK_PUBLIC_KEY")
}
