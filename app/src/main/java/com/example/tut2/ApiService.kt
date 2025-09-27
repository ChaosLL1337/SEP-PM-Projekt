package com.example.tut2

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

// Datenklasse für den Input (User-Text)
data class InputData(val text: String)

// Datenklasse für die Antwort vom Python-Backend
data class ResponseData(val response: String)

// Retrofit-Interface
interface ApiService {
    @POST("process")  // muss zu deiner FastAPI-Route passen
    fun sendText(@Body input: InputData): Call<ResponseData>
}