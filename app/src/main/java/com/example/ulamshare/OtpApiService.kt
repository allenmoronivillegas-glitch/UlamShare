package com.example.ulamshare

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface OtpApiService {
    @POST("api/otp/send")
    fun sendOtp(@Body request: SendOtpRequest): Call<SendOtpResponse>

    @POST("api/otp/verify")
    fun verifyOtp(@Body request: VerifyOtpRequest): Call<VerifyOtpResponse>
}
