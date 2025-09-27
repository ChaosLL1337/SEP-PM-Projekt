package com.example.tut2

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Deine aktuelle ngrok-URL einsetzen!
    private const val BASE_URL = "https://nonanarchistic-tosha-nonefficacious.ngrok-free.dev/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}

//