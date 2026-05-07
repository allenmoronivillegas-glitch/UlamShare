package com.example.ulamshare

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class UserDonationStats(
    val totalDonated: Long = 0L,
    val donationsCount: Int = 0,
    val campaignsDonatedCount: Int = 0,
    val donationsThisYear: Int = 0,
    val donations: List<UserImpactDonation> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap()
)

data class UserImpactDonation(
    val donationId: String,
    val amount: Long,
    val campaignId: String,
    val campaignTitle: String,
    val category: String,
    val status: String,
    val timestamp: Long,
    val dateString: String
)

object UserDonationStatsRepository {
    private const val TAG = "UserProfileStats"
    private const val DONATION_STATS_TAG = "DonationStats"
    private const val CAMPAIGN_PROGRESS_TAG = "CampaignProgress"
    private const val DATABASE_URL =
        "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
    private const val DONATIONS_COLLECTION = "donations"
    private const val USERS_COLLECTION = "users"

    private val userIdFields = listOf("donorId", "userId", "uid", "donorUid", "createdBy")
    private val successfulStatuses = setOf(
        "completed",
        "complete",
        "success",
        "successful",
        "approved",
        "paid",
        "verified",
        "confirmed",
        "recorded"
    )
    private val rejectedStatuses = setOf(
        "pending",
        "failed",
        "fail",
        "cancelled",
        "canceled",
        "rejected",
        "declined",
        "unverified"
    )

    fun loadUserDonationStats(
        userId: String,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        realtimeDatabase: FirebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL),
        onComplete: (Result<UserDonationStats>) -> Unit
    ) {
        if (userId.isBlank()) {
            onComplete(Result.success(UserDonationStats()))
            return
        }

        Log.d(TAG, "Loading donation stats for userId=$userId")

        val firestoreTasks = buildFirestoreDonationTasks(userId, firestore)
        val realtimeTasks = buildRealtimeDonationTasks(userId, realtimeDatabase)
        val aggregateTask = firestore.collection(USERS_COLLECTION).document(userId).get()
        val allTasks = mutableListOf<Task<*>>().apply {
            addAll(firestoreTasks)
            addAll(realtimeTasks)
            add(aggregateTask)
        }

        Tasks.whenAllComplete(allTasks)
            .addOnCompleteListener {
                val donationRecords = mutableListOf<UserImpactDonation>()

                firestoreTasks.forEach { task ->
                    if (task.isSuccessful) {
                        task.result?.documents
                            ?.mapNotNullTo(donationRecords) { document -> parseFirestoreDonation(document) }
                    } else {
                        Log.e(TAG, "Failed to load Firestore donation stats", task.exception)
                    }
                }

                realtimeTasks.forEach { task ->
                    if (task.isSuccessful) {
                        task.result?.children
                            ?.mapNotNullTo(donationRecords) { snapshot -> parseRealtimeDonation(snapshot) }
                    } else {
                        Log.e(TAG, "Failed to load Realtime Database donation stats", task.exception)
                    }
                }

                val normalizedRecords = donationRecords
                    .filter { it.amount > 0L && isSuccessfulDonation(it.status) }
                    .distinctBy { donation ->
                        donation.donationId.ifBlank {
                            listOf(
                                donation.campaignId,
                                donation.campaignTitle,
                                donation.timestamp.toString(),
                                donation.amount.toString()
                            ).joinToString("|")
                        }
                    }
                    .sortedByDescending { it.timestamp }

                val aggregateFallback = if (aggregateTask.isSuccessful) {
                    aggregateTask.result?.toAggregateStats()
                } else {
                    Log.e(TAG, "Failed to load aggregate donation stats", aggregateTask.exception)
                    null
                }

                val stats = if (normalizedRecords.isEmpty() && aggregateFallback != null) {
                    aggregateFallback
                } else {
                    buildStats(normalizedRecords)
                }

                Log.d(
                    TAG,
                    "Stats loaded: total=${stats.totalDonated}, donations=${stats.donationsCount}, campaigns=${stats.campaignsDonatedCount}"
                )
                onComplete(Result.success(stats))
            }
    }

    fun refreshUserDonationAggregates(
        userId: String,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        realtimeDatabase: FirebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL),
        onComplete: (Result<UserDonationStats>) -> Unit = {}
    ) {
        loadUserDonationStats(userId, firestore, realtimeDatabase) { result ->
            result
                .onSuccess { stats ->
                    val userRef = firestore.collection(USERS_COLLECTION).document(userId)
                    val batch = firestore.batch()
                    batch.set(
                        userRef,
                        mapOf(
                            "totalDonated" to stats.totalDonated,
                            "donationsCount" to stats.donationsCount,
                            "campaignsDonatedCount" to stats.campaignsDonatedCount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    stats.donations
                        .filter { it.campaignId.isNotBlank() }
                        .groupBy { it.campaignId }
                        .forEach { (campaignId, donations) ->
                            val first = donations.minByOrNull { it.timestamp }
                            val last = donations.maxByOrNull { it.timestamp }
                            batch.set(
                                userRef.collection("donated_campaigns").document(campaignId),
                                mapOf(
                                    "campaignId" to campaignId,
                                    "campaignTitle" to (last?.campaignTitle ?: first?.campaignTitle).orEmpty(),
                                    "campaignCategory" to (last?.category ?: first?.category).orEmpty(),
                                    "firstDonatedAt" to timestampFromMillis(first?.timestamp ?: 0L),
                                    "lastDonatedAt" to timestampFromMillis(last?.timestamp ?: 0L),
                                    "totalDonatedToCampaign" to donations.sumOf { it.amount },
                                    "donationCount" to donations.size
                                ),
                                SetOptions.merge()
                            )
                        }
                    batch.commit()
                        .addOnSuccessListener { onComplete(Result.success(stats)) }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Failed to update aggregate donation stats", error)
                            onComplete(Result.failure(error))
                        }
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to refresh aggregate donation stats", error)
                    onComplete(Result.failure(error))
                }
        }
    }

    fun recalculateUserDonationStats(
        userId: String,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        realtimeDatabase: FirebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL),
        onComplete: (Result<UserDonationStats>) -> Unit = {}
    ) {
        refreshUserDonationAggregates(userId, firestore, realtimeDatabase, onComplete)
    }

    fun applySuccessfulDonation(
        donationId: String,
        userId: String,
        amount: Long,
        campaignId: String,
        campaignTitle: String,
        campaignCategory: String = "",
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        realtimeDatabase: FirebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL),
        donationPayload: Map<String, Any?> = emptyMap(),
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (donationId.isBlank() || userId.isBlank() || campaignId.isBlank() || amount <= 0L) {
            onComplete(Result.failure(IllegalArgumentException("Invalid donation stats payload.")))
            return
        }

        Log.d(DONATION_STATS_TAG, "Applying donation stats donationId=$donationId")
        Log.d(DONATION_STATS_TAG, "campaignId=$campaignId, amount=$amount, userId=$userId")

        val firestoreDonation = donationPayload
            .filterValues { it != null }
            .mapValues { it.value as Any }
            .toMutableMap()
            .apply {
                put("donationId", donationId)
                put("userId", userId)
                put("donorId", userId)
                put("amount", amount)
                put("campaignId", campaignId)
                put("campaignTitle", campaignTitle)
                put("campaignCategory", campaignCategory)
                put("status", "successful")
                put("updatedAt", FieldValue.serverTimestamp())
            }

        firestore.collection(DONATIONS_COLLECTION).document(donationId)
            .set(firestoreDonation, SetOptions.merge())
            .addOnSuccessListener {
                applyCampaignProgressOnce(
                    realtimeDatabase = realtimeDatabase,
                    donationId = donationId,
                    campaignId = campaignId,
                    amount = amount
                ) { progressResult ->
                    progressResult
                        .onFailure { error ->
                            Log.e(CAMPAIGN_PROGRESS_TAG, "Failed to update campaign progress", error)
                            onComplete(Result.failure(error))
                        }
                        .onSuccess {
                            applyUserStatsOnce(
                                firestore = firestore,
                                donationId = donationId,
                                userId = userId,
                                campaignId = campaignId,
                                campaignTitle = campaignTitle,
                                campaignCategory = campaignCategory,
                                amount = amount
                            ) { statsResult ->
                                statsResult
                                    .onSuccess {
                                        realtimeDatabase.reference
                                            .child(DONATIONS_COLLECTION)
                                            .child(donationId)
                                            .updateChildren(
                                                mapOf(
                                                    "statsApplied" to true,
                                                    "appliedAt" to System.currentTimeMillis()
                                                )
                                            )
                                        onComplete(Result.success(Unit))
                                    }
                                    .onFailure { error ->
                                        Log.e(DONATION_STATS_TAG, "Failed to update user stats", error)
                                        onComplete(Result.failure(error))
                                    }
                            }
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.e(DONATION_STATS_TAG, "Failed to mirror donation document", error)
                onComplete(Result.failure(error))
            }
    }

    fun recalculateCampaignProgress(
        campaignId: String,
        realtimeDatabase: FirebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL),
        onComplete: (Result<Pair<Long, Int>>) -> Unit = {}
    ) {
        if (campaignId.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("Missing campaignId.")))
            return
        }

        realtimeDatabase.reference.child(DONATIONS_COLLECTION)
            .orderByChild("campaignId")
            .equalTo(campaignId)
            .get()
            .addOnSuccessListener { snapshot ->
                val records = snapshot.children.mapNotNull(::parseRealtimeDonation)
                    .filter { it.amount > 0L && isSuccessfulDonation(it.status) }
                val total = records.sumOf { it.amount }
                val count = records.size
                realtimeDatabase.reference.child("campaigns").child(campaignId)
                    .updateChildren(
                        mapOf(
                            "raised" to total,
                            "raisedAmount" to total,
                            "currentAmount" to total,
                            "amountRaised" to total,
                            "donationCount" to count
                        )
                    )
                    .addOnSuccessListener {
                        Log.d(CAMPAIGN_PROGRESS_TAG, "Recalculated campaignId=$campaignId raised=$total count=$count")
                        onComplete(Result.success(total to count))
                    }
                    .addOnFailureListener { error ->
                        Log.e(CAMPAIGN_PROGRESS_TAG, "Failed to recalculate campaign progress", error)
                        onComplete(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(CAMPAIGN_PROGRESS_TAG, "Failed to load donations for campaign recalculation", error)
                onComplete(Result.failure(error))
            }
    }

    fun formatPeso(amount: Long): String {
        return "\u20B1${NumberFormat.getNumberInstance(Locale.US).format(amount)}"
    }

    private fun buildFirestoreDonationTasks(
        userId: String,
        firestore: FirebaseFirestore
    ): List<Task<com.google.firebase.firestore.QuerySnapshot>> {
        return userIdFields.map { field ->
            firestore.collection(DONATIONS_COLLECTION)
                .whereEqualTo(field, userId)
                .get()
        }
    }

    private fun buildRealtimeDonationTasks(
        userId: String,
        realtimeDatabase: FirebaseDatabase
    ): List<Task<DataSnapshot>> {
        val donationsRef = realtimeDatabase.reference.child(DONATIONS_COLLECTION)
        return userIdFields.map { field ->
            donationsRef.orderByChild(field).equalTo(userId).get()
        }
    }

    private fun applyCampaignProgressOnce(
        realtimeDatabase: FirebaseDatabase,
        donationId: String,
        campaignId: String,
        amount: Long,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val donationRef = realtimeDatabase.reference.child(DONATIONS_COLLECTION).child(donationId)
        donationRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.child("campaignProgressApplied").getValue(Boolean::class.java) == true) {
                    return Transaction.abort()
                }
                currentData.child("campaignProgressApplied").value = true
                currentData.child("campaignProgressAppliedAt").value = System.currentTimeMillis()
                currentData.child("status").value = "successful"
                return Transaction.success(currentData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) {
                    onComplete(Result.failure(error.toException()))
                    return
                }
                if (!committed) {
                    Log.d(CAMPAIGN_PROGRESS_TAG, "Campaign progress already applied for donationId=$donationId")
                    onComplete(Result.success(Unit))
                    return
                }
                incrementCampaignProgress(
                    realtimeDatabase = realtimeDatabase,
                    campaignId = campaignId,
                    amount = amount,
                    onComplete = { result ->
                        result.onFailure {
                            donationRef.child("campaignProgressApplied").setValue(false)
                        }
                        onComplete(result)
                    }
                )
            }
        })
    }

    private fun incrementCampaignProgress(
        realtimeDatabase: FirebaseDatabase,
        campaignId: String,
        amount: Long,
        onComplete: (Result<Unit>) -> Unit
    ) {
        Log.d(CAMPAIGN_PROGRESS_TAG, "Updating campaignId=$campaignId, amount=$amount")
        Log.d(CAMPAIGN_PROGRESS_TAG, "Incrementing raised by amount=$amount for campaignId=$campaignId")
        realtimeDatabase.reference.child("campaigns").child(campaignId)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentRaised = firstNumber(
                        currentData.child("raised").value,
                        currentData.child("raisedAmount").value,
                        currentData.child("currentAmount").value,
                        currentData.child("amountRaised").value
                    )
                    val newRaised = currentRaised + amount
                    val currentDonationCount = numberToLong(currentData.child("donationCount").value)
                    currentData.child("raised").value = newRaised
                    currentData.child("raisedAmount").value = newRaised
                    currentData.child("currentAmount").value = newRaised
                    currentData.child("amountRaised").value = newRaised
                    currentData.child("donationCount").value = currentDonationCount + 1L
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) {
                        onComplete(Result.failure(error.toException()))
                    } else {
                        onComplete(Result.success(Unit))
                    }
                }
            })
    }

    private fun applyUserStatsOnce(
        firestore: FirebaseFirestore,
        donationId: String,
        userId: String,
        campaignId: String,
        campaignTitle: String,
        campaignCategory: String,
        amount: Long,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val donationRef = firestore.collection(DONATIONS_COLLECTION).document(donationId)
        val userRef = firestore.collection(USERS_COLLECTION).document(userId)
        val donatedCampaignRef = userRef.collection("donated_campaigns").document(campaignId)

        firestore.runTransaction { transaction ->
            val donationSnapshot = transaction.get(donationRef)
            if (donationSnapshot.getBoolean("statsApplied") == true) {
                Log.d(DONATION_STATS_TAG, "Stats already applied for donationId=$donationId")
                return@runTransaction false
            }
            val donatedCampaignSnapshot = transaction.get(donatedCampaignRef)
            val firstDonationToCampaign = !donatedCampaignSnapshot.exists()
            Log.d(DONATION_STATS_TAG, "firstDonationToCampaign=$firstDonationToCampaign")

            transaction.set(
                userRef,
                mapOf(
                    "uid" to userId,
                    "totalDonated" to FieldValue.increment(amount),
                    "donationsCount" to FieldValue.increment(1L),
                    "campaignsDonatedCount" to FieldValue.increment(if (firstDonationToCampaign) 1L else 0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            val donatedCampaignPayload = mutableMapOf<String, Any>(
                "campaignId" to campaignId,
                "campaignTitle" to campaignTitle,
                "campaignCategory" to campaignCategory,
                "lastDonatedAt" to FieldValue.serverTimestamp(),
                "totalDonatedToCampaign" to FieldValue.increment(amount),
                "donationCount" to FieldValue.increment(1L)
            )
            if (firstDonationToCampaign) {
                donatedCampaignPayload["firstDonatedAt"] = FieldValue.serverTimestamp()
            }
            transaction.set(donatedCampaignRef, donatedCampaignPayload, SetOptions.merge())
            transaction.set(
                donationRef,
                mapOf(
                    "status" to "successful",
                    "campaignProgressApplied" to true,
                    "statsApplied" to true,
                    "appliedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            true
        }.addOnSuccessListener {
            onComplete(Result.success(Unit))
        }.addOnFailureListener { error ->
            onComplete(Result.failure(error))
        }
    }

    private fun buildStats(records: List<UserImpactDonation>): UserDonationStats {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val campaigns = records
            .map { it.campaignId.ifBlank { it.campaignTitle } }
            .filter { it.isNotBlank() }
            .toSet()
        val categoryCounts = records.groupingBy {
            it.category.ifBlank { "General Campaigns" }
        }.eachCount()
        val donationsThisYear = records.count { donation ->
            if (donation.timestamp <= 0L) {
                false
            } else {
                Calendar.getInstance().apply {
                    timeInMillis = donation.timestamp
                }.get(Calendar.YEAR) == currentYear
            }
        }

        return UserDonationStats(
            totalDonated = records.sumOf { it.amount },
            donationsCount = records.size,
            campaignsDonatedCount = campaigns.size,
            donationsThisYear = donationsThisYear,
            donations = records,
            categoryCounts = categoryCounts
        )
    }

    private fun parseFirestoreDonation(document: DocumentSnapshot): UserImpactDonation? {
        val amount = numberToLong(document.get("amount"))
        if (amount <= 0L) return null
        return UserImpactDonation(
            donationId = document.getString("donationId").orEmpty().ifBlank { document.id },
            amount = amount,
            campaignId = document.getString("campaignId").orEmpty(),
            campaignTitle = document.getString("campaignTitle").orEmpty(),
            category = document.getString("category").orEmpty()
                .ifBlank { document.getString("campaignCategory").orEmpty() },
            status = donationStatus(
                document.getString("status"),
                document.getString("paymentStatus"),
                document.getString("verificationStatus"),
                document.getString("donationStatus")
            ),
            timestamp = timestampToMillis(document.get("timestamp"))
                .takeIf { it > 0L }
                ?: timestampToMillis(document.get("createdAt")),
            dateString = document.getString("dateString").orEmpty()
        )
    }

    private fun parseRealtimeDonation(snapshot: DataSnapshot): UserImpactDonation? {
        val amount = numberToLong(snapshot.child("amount").value)
        if (amount <= 0L) return null
        return UserImpactDonation(
            donationId = snapshot.child("donationId").getValue(String::class.java).orEmpty()
                .ifBlank { snapshot.key.orEmpty() },
            amount = amount,
            campaignId = snapshot.child("campaignId").getValue(String::class.java).orEmpty(),
            campaignTitle = snapshot.child("campaignTitle").getValue(String::class.java).orEmpty(),
            category = snapshot.child("category").getValue(String::class.java).orEmpty()
                .ifBlank { snapshot.child("campaignCategory").getValue(String::class.java).orEmpty() },
            status = donationStatus(
                snapshot.child("status").getValue(String::class.java),
                snapshot.child("paymentStatus").getValue(String::class.java),
                snapshot.child("verificationStatus").getValue(String::class.java),
                snapshot.child("donationStatus").getValue(String::class.java)
            ),
            timestamp = numberToLong(snapshot.child("timestamp").value)
                .takeIf { it > 0L }
                ?: numberToLong(snapshot.child("createdAt").value),
            dateString = snapshot.child("dateString").getValue(String::class.java).orEmpty()
        )
    }

    private fun DocumentSnapshot.toAggregateStats(): UserDonationStats? {
        if (!exists()) return null
        val total = numberToLong(get("totalDonated"))
        val count = numberToLong(get("donationsCount")).toInt()
        val campaigns = numberToLong(get("campaignsDonatedCount")).toInt()
        if (total <= 0L && count <= 0 && campaigns <= 0) return null
        return UserDonationStats(
            totalDonated = total,
            donationsCount = count,
            campaignsDonatedCount = campaigns
        )
    }

    private fun donationStatus(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }

    private fun isSuccessfulDonation(status: String): Boolean {
        val normalized = status.trim().lowercase(Locale.getDefault())
        if (normalized.isBlank()) return true
        if (rejectedStatuses.any { normalized == it || normalized.contains(it) }) return false
        return successfulStatuses.any { normalized == it || normalized.contains(it) }
    }

    private fun numberToLong(value: Any?): Long {
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Double -> value.toLong()
            is Float -> value.toLong()
            is Number -> value.toLong()
            is String -> value.replace(",", "").trim().toDoubleOrNull()?.toLong() ?: 0L
            else -> 0L
        }
    }

    private fun firstNumber(vararg values: Any?): Long {
        return values.firstNotNullOfOrNull { value ->
            numberToLong(value).takeIf { it > 0L }
        } ?: 0L
    }

    private fun timestampToMillis(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
            is Date -> value.time
            else -> numberToLong(value)
        }
    }

    private fun timestampFromMillis(value: Long): Timestamp {
        return Timestamp(Date(value.takeIf { it > 0L } ?: System.currentTimeMillis()))
    }
}
