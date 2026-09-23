package com.example.despensadomestica

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // IP local de la PC en la red WiFi (no 10.0.2.2: esa solo funciona en el
    // emulador). El celular y la PC deben estar en la misma red WiFi, y
    // Apache/MySQL de XAMPP deben estar encendidos.
    private const val BASE_URL = "http://192.168.18.166/api_despensa/"
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}