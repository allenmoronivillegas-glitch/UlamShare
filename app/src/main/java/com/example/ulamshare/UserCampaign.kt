package com.example.ulamshare

data class UserCampaign(
    val campaignId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "",
    val category: String = "",
    val assignedAt: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "campaignId" to campaignId,
        "title" to title,
        "description" to description,
        "status" to status,
        "category" to category,
        "assignedAt" to assignedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): UserCampaign? {
            val campaignId = map["campaignId"] as? String ?: return null
            return UserCampaign(
                campaignId = campaignId,
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: "",
                status = map["status"] as? String ?: "",
                category = map["category"] as? String ?: "",
                assignedAt = (map["assignedAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
