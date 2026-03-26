package com.example.apirutemap

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://apitrackline-3047cf7af332.herokuapp.com/"

    fun getClient(context: Context): Retrofit {
        val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                if (!token.isNullOrEmpty()) {
                    // Tu backend usa Cookie (no Bearer)
                    requestBuilder.addHeader("Cookie", "authToken=$token")
                }
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
