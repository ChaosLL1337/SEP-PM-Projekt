package com.example.tut2

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Deine aktuelle ngrok-URL einsetzen!
    private const val BASE_URL = "https://nonanarchistic-tosha-nonefficacious.ngrok-free.dev/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // Verbindung aufbauen
        .readTimeout(120, TimeUnit.SECONDS)     // Warten auf Antwort (wichtig!)
        .writeTimeout(120, TimeUnit.SECONDS)    // Daten senden
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)   // <-- hier deinen Client einhängen
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
