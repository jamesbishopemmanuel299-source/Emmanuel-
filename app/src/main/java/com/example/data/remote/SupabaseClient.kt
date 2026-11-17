package com.example.data.remote

import com.example.config.AppConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class SupabaseClient(
    private val tokenProvider: () -> String?
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val anonKey = AppConfig.supabaseAnonKey
        val jwt = tokenProvider()?.takeIf { it.isNotBlank() } ?: anonKey

        val request = original.newBuilder()
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $jwt")
            .apply {
                if (original.header("Content-Type") == null && original.body != null) {
                    val contentType = original.body?.contentType()?.toString()
                    if (contentType != null && contentType.contains("json")) {
                        header("Content-Type", "application/json")
                    }
                }
            }
            .build()
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: SupabaseApi by lazy {
        val baseUrl = if (AppConfig.isSupabaseConfigured) {
            "${AppConfig.supabaseUrl}/"
        } else {
            "https://placeholder.supabase.co/"
        }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApi::class.java)
    }

    fun publicStorageUrl(bucket: String, path: String): String =
        "${AppConfig.supabaseUrl}/storage/v1/object/public/$bucket/$path"
}

object SupabaseErrorParser {
    fun <T> extractMessage(response: Response<T>, fallback: String = "Request failed"): String {
        val raw = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (raw.isNullOrBlank()) return "$fallback (${response.code()})"
        return try {
            val json = JSONObject(raw)
            json.optString("error_description")
                .ifBlank { json.optString("msg") }
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
                .ifBlank { "$fallback (${response.code()})" }
        } catch (_: Exception) {
            raw.take(180)
        }
    }
}
