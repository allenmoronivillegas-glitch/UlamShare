package com.example.ulamshare

data class SendOtpRequest(
    val userId: String,
    val destination: String,
    val channel: String = "email"
)

data class SendOtpResponse(
    val success: Boolean = false,
    val requestId: String = "",
    val message: String = ""
)

data class VerifyOtpRequest(
    val requestId: String,
    val otp: String
)

data class VerifyOtpResponse(
    val success: Boolean = false,
    val status: String = "",
    val message: String = ""
)
