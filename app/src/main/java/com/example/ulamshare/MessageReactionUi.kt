package com.example.ulamshare

object MessageReactionUi {
    val quickReactionOrder = listOf("+1", "love", "wow", "sad", "ok", "thanks")

    private val reactionDisplayMap = linkedMapOf(
        "+1" to "👍",
        "-1" to "👎",
        "heart" to "❤️",
        "laugh" to "😄",
        "hooray" to "🎉",
        "confused" to "😕",
        "eyes" to "👀",
        "love" to "❤️",
        "wow" to "😮",
        "sad" to "😢",
        "ok" to "👌",
        "thanks" to "🙏"
    )

    data class Summary(
        val type: String,
        val count: Int,
        val reactedByMe: Boolean
    )

    fun displayLabel(type: String): String {
        return reactionDisplayMap[type.trim()] ?: type.trim()
    }

    fun actorKey(userId: String): String {
        return "user_$userId"
    }

    fun summarize(
        reactions: Map<String, ChatReactionEntry>,
        currentUserId: String
    ): List<Summary> {
        if (reactions.isEmpty()) return emptyList()

        val actorKey = actorKey(currentUserId)
        val counts = linkedMapOf<String, Int>()
        reactions.values.forEach { entry ->
            val token = entry.emoji.trim()
            if (token.isBlank()) return@forEach
            counts[token] = (counts[token] ?: 0) + 1
        }

        val myToken = reactions[actorKey]?.emoji?.trim().orEmpty().ifBlank {
            reactions.values.firstOrNull { it.by == actorKey }?.emoji?.trim().orEmpty()
        }

        return counts.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> {
                    quickReactionOrder.indexOf(it.key).let { index ->
                        if (index == -1) Int.MAX_VALUE else index
                    }
                }.thenBy { it.key }
            )
            .map { entry ->
                Summary(
                    type = entry.key,
                    count = entry.value,
                    reactedByMe = entry.key == myToken
                )
            }
    }
}
