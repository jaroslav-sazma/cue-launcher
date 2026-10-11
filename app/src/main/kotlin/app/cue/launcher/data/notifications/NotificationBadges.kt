package app.cue.launcher.data.notifications

data class BadgeKey(val packageName: String, val userSerial: Long)

data class BadgeCandidate(
    val key: BadgeKey,
    val number: Int,
    val canShowBadge: Boolean,
    val isGroupSummary: Boolean,
    val hasContent: Boolean,
    val ongoingOnDefaultChannel: Boolean,
)

// Mirrors what the stock launcher counts, so a badge here agrees with the dot the system would
// show: the per-channel "show badge" switch is honoured, and a group summary, an empty notification
// or an ongoing one in the legacy default channel never adds to the count.
fun badgeCounts(candidates: List<BadgeCandidate>): Map<BadgeKey, Int> = candidates
    .filter { it.canShowBadge && !it.isGroupSummary && it.hasContent && !it.ongoingOnDefaultChannel }
    .groupingBy { it.key }
    .fold(0) { total, candidate -> total + candidate.number.coerceAtLeast(1) }
