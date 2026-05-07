package com.example.ulamshare

object PrivacyDisplayHelper {
    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun publicName(value: String?, fallback: String = "HopeGive User"): String {
        val trimmed = value.orEmpty().trim()
        return when {
            trimmed.isBlank() -> fallback
            trimmed.contains("@") || emailRegex.matches(trimmed) -> fallback
            else -> trimmed
        }
    }

    fun publicMeta(vararg values: String?): String {
        return values
            .map { it.orEmpty().trim() }
            .firstOrNull { it.isNotBlank() && !it.contains("@") && !emailRegex.matches(it) }
            .orEmpty()
    }

    fun removeEmailAddresses(value: String, replacement: String = "HopeGive User"): String {
        return value.replace(emailRegex, replacement)
    }
}
