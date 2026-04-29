package com.example.ulamshare

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object OtpApiClient {
    private const val BASE_URL = "https://asia-southeast1-ulamshare-4f2b9.cloudfunctions.net/otpApi/"

    val service: OtpApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OtpApiService::class.java)
    }
}
