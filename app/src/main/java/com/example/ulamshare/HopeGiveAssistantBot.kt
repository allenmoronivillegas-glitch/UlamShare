package com.example.ulamshare

data class FaqItem(
    val question: String,
    val keywords: List<String>,
    val answer: String
)

object HopeGiveAssistantBot {
    const val CHANNEL = "bot"
    const val ROOT_PATH = "local/hopegiveAssistant"
    const val SENDER_ID = "hopegive-assistant"

    val quickQuestions = listOf(
        "How do I donate?",
        "How do I choose a campaign?",
        "Where can I see my donations?",
        "How do I contact support?",
        "How do I edit my profile?",
        "How do campaign posts work?",
        "How do payment methods work?"
    )

    private val faqs = listOf(
        FaqItem(
            question = "How do I donate?",
            keywords = listOf("donate", "donation", "give", "make a donation", "donation amount"),
            answer = "To donate, go to Campaigns, choose a campaign, enter your donation amount, select a payment method, and confirm your donation."
        ),
        FaqItem(
            question = "How do I choose a campaign?",
            keywords = listOf("choose campaign", "campaign", "campaigns", "fundraiser", "browse", "progress", "campaign details"),
            answer = "Go to Campaigns and browse the available campaigns. You can view campaign details, progress, and updates before donating."
        ),
        FaqItem(
            question = "Where can I see my donations?",
            keywords = listOf("my donations", "donation history", "history", "receipt", "records", "past donation", "see my donations"),
            answer = "Open your Profile and tap My Donations to view your donation history."
        ),
        FaqItem(
            question = "How do I contact support?",
            keywords = listOf("support", "admin", "contact", "help", "talk to support", "admin team"),
            answer = "You can contact support through Messenger by opening the Support Team conversation."
        ),
        FaqItem(
            question = "How do I edit my profile?",
            keywords = listOf("profile", "edit", "change profile", "name", "phone", "email", "profile information", "profile photo"),
            answer = "Go to Profile, tap the edit pencil, update your information, then tap Save Changes."
        ),
        FaqItem(
            question = "How do campaign posts work?",
            keywords = listOf("campaign feed", "feed", "post", "posts", "community", "official", "comment", "reaction", "share"),
            answer = "The Campaign Feed shows official HopeGive updates, community posts, comments, reactions, and campaign progress."
        ),
        FaqItem(
            question = "How do I follow users?",
            keywords = listOf("follow", "followers", "following", "friend", "add friend", "connections"),
            answer = "Open a user profile or Add Friends from Messenger, then follow or add the person you want to connect with."
        ),
        FaqItem(
            question = "How do I report a problem?",
            keywords = listOf("report", "problem", "issue", "bug", "error", "not working"),
            answer = "You can report a problem by opening Messenger and sending details to the Support Team conversation."
        ),
        FaqItem(
            question = "How do payment methods work?",
            keywords = listOf("payment", "payment method", "payment methods", "gcash", "maya", "card", "cash", "bank", "saved method"),
            answer = "Open Profile -> Payment Methods to add or manage GCash, Maya, or card details. Payment methods are saved for future donations."
        ),
        FaqItem(
            question = "How does donation verification work?",
            keywords = listOf("verification", "verify donation", "donation verification", "verified", "proof", "confirmation"),
            answer = "After you donate, HopeGive records the transaction and may review payment details before marking the donation as verified."
        )
    )

    fun answerFor(message: String): String? {
        val normalizedMessage = normalize(message)
        if (normalizedMessage.isBlank()) return null

        val bestMatch = faqs
            .map { item -> item to score(item, normalizedMessage) }
            .maxByOrNull { it.second }

        return bestMatch
            ?.takeIf { it.second > 0 }
            ?.first
            ?.answer
    }

    private fun score(item: FaqItem, normalizedMessage: String): Int {
        var total = 0
        val normalizedQuestion = normalize(item.question)
        if (normalizedQuestion.isNotBlank() && normalizedMessage.contains(normalizedQuestion)) {
            total += 8
        }

        item.keywords.forEach { keyword ->
            val normalizedKeyword = normalize(keyword)
            if (normalizedKeyword.isBlank()) return@forEach

            if (normalizedMessage.contains(normalizedKeyword)) {
                total += if (normalizedKeyword.contains(" ")) 3 else 1
            }
        }

        return total
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
